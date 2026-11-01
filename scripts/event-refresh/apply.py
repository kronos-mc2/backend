"""Review/import a prepared bundle. Mutation requires --apply and runs on the Pi."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import urllib.error
from zoneinfo import ZoneInfo

from storage import S3


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def insert(table, values, conflict=None):
    keys = list(values)
    sql = f"INSERT INTO {table} ({', '.join(keys)}) VALUES ({', '.join(literal(values[k]) for k in keys)})"
    if conflict:
        sql += " ON CONFLICT (id) DO UPDATE SET " + ", ".join(f"{k}=EXCLUDED.{k}" for k in keys if k != "id")
    return sql + ";"


def object_key(asset, event_id):
    # Separate object ownership per event: deleting one event must not break others.
    return f"events/{event_id}/seed60/{asset['sha256'][:20]}-{asset['file']}"


def sql_for(bundle, bucket):
    events = bundle["events"]
    ids = ",".join(literal(e["id"]) for e in events)
    sql = ["\\set ON_ERROR_STOP on", "BEGIN;", "SET LOCAL standard_conforming_strings = on;",
           "SELECT pg_advisory_xact_lock(60702026);",
           # Only seed namespaces. User-created events and accounts are preserved.
           f"DELETE FROM events WHERE id LIKE 'seed60-%' AND id NOT IN ({ids});",
           "DELETE FROM events WHERE creator_user_id IS NULL "
           "AND (id ~ '^local-[0-9]+$' OR id ~ '^dev-[0-9]+$') "
           f"AND start_at < {literal(bundle['start'] + 'T00:00:00')}::timestamp AT TIME ZONE 'Europe/Zagreb';"]
    for e in events:
        assert e["id"].startswith("seed60-")
        sql.append(insert("events", dict(id=e["id"], title_hr=e["title"], title_en=e["title"],
            where_hr=e["venue"], where_en=e["venue"], address=e["address"],
            about_hr=e["about"], about_en=e["about"], when_iso=e["start"], start_at=e["start"], end_at=e["end"],
            event_type="nearby", latitude=e["lat"], longitude=e["lng"], visibility="public",
            attendance_mode="open", status="published", source_url=e["source"]), conflict=True))
        sql += [f"UPDATE events SET updated_at=clock_timestamp() WHERE id={literal(e['id'])};",
                f"DELETE FROM event_tags WHERE event_id={literal(e['id'])};",
                f"DELETE FROM event_media WHERE event_id={literal(e['id'])};"]
        for tag in e["tags"]:
            sql.append(insert("event_tags", dict(event_id=e["id"], tag=tag)))
        for order, filename in enumerate(e["images"] + [e["video"]]):
            asset = bundle["assets"][filename]
            thumbnail = bundle["assets"][e["video_thumbnail"]] if filename == e["video"] else asset
            key, thumb_key = object_key(asset, e["id"]), object_key(thumbnail, e["id"])
            sql.append(insert("event_media", dict(id=f"{e['id']}-m{order}", event_id=e["id"],
                media_type="video" if filename == e["video"] else "image", url=f"s3://{bucket}/{key}",
                thumbnail_url=f"s3://{bucket}/{thumb_key}", original_filename=filename, storage_key=key,
                bucket_name=bucket, content_type=asset["content_type"], byte_size=asset["bytes"],
                width=asset["width"], height=asset["height"], sort_order=order,
                thumbnail_storage_key=thumb_key, thumbnail_bucket_name=bucket,
                thumbnail_content_type=thumbnail["content_type"], thumbnail_byte_size=thumbnail["bytes"],
                thumbnail_width=thumbnail["width"], thumbnail_height=thumbnail["height"])))
    sql += ["COMMIT;"]
    return "\n".join(sql) + "\n"


def podman(*args, **kwargs):
    return subprocess.run(["podman", *args], check=True, **kwargs)


def db_command(program, *args):
    # Credentials remain in the container environment, never in argv or logs.
    return ["exec", "-i", "gik_postgres_test", "sh", "-c",
            'exec ' + program + ' -U "$POSTGRES_USER" -d "$POSTGRES_DB" ' + " ".join(args)]


def validate(bundle, directory):
    from collections import Counter
    zone = ZoneInfo("Europe/Zagreb")
    assert bundle["schema_version"] == 1
    assert len(bundle["events"]) == 180
    counts = Counter(dt.datetime.fromisoformat(e["start"]).astimezone(zone).date() for e in bundle["events"])
    assert len(counts) == 60 and set(counts.values()) == {3}
    assert min(counts).isoformat() == bundle["start"] and max(counts).isoformat() == bundle["end"]
    assert len({e["id"] for e in bundle["events"]}) == 180
    for e in bundle["events"]:
        assert len(e["images"]) == len(set(e["images"])) == 4
        assert e["images"][0] == e["cover"]
        for name in e["images"] + [e["video"], e["video_thumbnail"]]:
            assert name in bundle["assets"]
    for name, asset in bundle["assets"].items():
        assert Path(name).name == name == asset["file"]
        data = (directory / "assets" / name).read_bytes()
        assert len(data) == asset["bytes"] and hashlib.sha256(data).hexdigest() == asset["sha256"]
    for asset in bundle["assets"].values():
        if asset["content_type"] == "video/mp4":
            assert 3 <= asset["duration_seconds"] <= 10 and asset["bytes"] < 10 * 1024 * 1024


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--bucket", default="gik-event-media", help="SQL preview bucket; actual import uses backend env")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    bundle = json.loads((args.bundle / "events.json").read_text())
    validate(bundle, args.bundle)
    sql = sql_for(bundle, args.bucket)
    (args.bundle / "import.sql").write_text(sql)
    print(f"Validated {len(bundle['events'])} events, 60 days, 4 images + 1 video each.")
    if not args.apply:
        print("SQL preview saved. Database and object storage unchanged.")
        return
    today = dt.datetime.now(ZoneInfo("Europe/Zagreb")).date()
    if dt.date.fromisoformat(bundle["start"]) < today:
        raise SystemExit("Bundle starts in the past. Regenerate with prepare.py --start YYYY-MM-DD first.")
    config = json.loads(podman("inspect", "gik_backend_test", capture_output=True).stdout)[0]
    env = dict(value.split("=", 1) for value in config["Config"]["Env"] if "=" in value)
    bucket = env["APP_STORAGE_BUCKET"]
    minio = json.loads(podman("inspect", "gik_minio_test", capture_output=True).stdout)[0]
    networks = minio["NetworkSettings"]["Networks"]
    address = next(v["IPAddress"] for k, v in networks.items() if "gik" in k)
    client = S3(f"http://{address}:9000", env["APP_STORAGE_ACCESS_KEY"], env["APP_STORAGE_SECRET_KEY"], env.get("APP_STORAGE_REGION", "auto"))
    client.ensure_private_bucket(bucket)
    # Preflight schema before any seed writes.
    podman(*db_command("psql", "-v ON_ERROR_STOP=1", "-q"), input=b"SELECT thumbnail_storage_key FROM event_media LIMIT 0;", capture_output=True)
    backup_dir = args.bundle / "backups"
    backup_dir.mkdir(exist_ok=True, mode=0o700)
    backup = backup_dir / (dt.datetime.now(dt.timezone.utc).strftime("before-%Y%m%dT%H%M%S%fZ") + ".dump")
    with backup.open("xb") as handle:
        podman(*db_command("pg_dump", "-Fc"), stdout=handle)
    backup.chmod(0o600)
    assert backup.stat().st_size > 0
    print(f"Database backup: {backup}")
    for e in bundle["events"]:
        for name in e["images"] + [e["video"], e["video_thumbnail"]]:
            asset = bundle["assets"][name]
            data = (args.bundle / "assets" / name).read_bytes()
            key = object_key(asset, e["id"])
            client.request("PUT", bucket, key, data, asset["content_type"])
            _, headers = client.request("HEAD", bucket, key)
            assert int(headers["Content-Length"]) == len(data)
    # Storage is verified before a single transactional database import.
    sql = sql_for(bundle, bucket)
    (args.bundle / "import.sql").write_text(sql)
    podman(*db_command("psql", "-v ON_ERROR_STOP=1", "-q"), input=sql.encode(), capture_output=True)
    verification = """
    SELECT (start_at AT TIME ZONE 'Europe/Zagreb')::date AS day, count(*)
    FROM events WHERE id LIKE 'seed60-%' GROUP BY 1 ORDER BY 1;
    SELECT media_type, count(*) FROM event_media WHERE event_id LIKE 'seed60-%' GROUP BY 1;
    """
    result = podman(*db_command("psql", "-v ON_ERROR_STOP=1"), input=verification.encode(), capture_output=True)
    print(result.stdout.decode())
    print("Import complete. Verify authenticated feed, image/video playback and public /health next.")


if __name__ == "__main__":
    main()
