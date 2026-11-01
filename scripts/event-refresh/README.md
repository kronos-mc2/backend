# GIK event refresh

Prepared and deployed on 20 October 2026 after the owner confirmed Raspberry connectivity returned. Public health at https://test-api-gik.nerizz.com/health returns `{"status":"ok"}`. All 20 Flyway migrations succeeded. PostgreSQL, MinIO and the backend are running; `gik-test.service` is enabled and active. Its startup command starts these three existing containers without triggering a compose recreation. A physical reboot has not been tested.

Import completed: 180 events across 60 days, 720 images and 180 video records. Public authenticated verification traversed all 18 feed pages without duplicates, checked all 21 distinct image/video source files plus four video thumbnails, and verified HTTP 206 MP4 byte ranges for every clip. Cloudflare sometimes omits `Accept-Ranges` from the public response, but all four returned the requested 1024 bytes and correct `Content-Range`. The verification accounts were removed. Results are saved in the local bundle as `api-verification.json`.

Remote bundle: `/mnt/ssd/apps/gik/event-refresh-20261020/`. Pre-import backup: `backups/before-20261020T160441195026Z.dump` (root-only, mode 0600). Import log: `import-result.log`. No user events or user accounts were replaced by the seed import.

The local bundle is `backend/.local/event-refresh-20261020/` (ignored by Git). It contains 180 events for **21 October–19 December 2026**, three every day in Europe/Zagreb, including the October daylight-saving transition. Five listings have verified dates and original source links; 175 are fictional demos with explicit demo descriptions and fictional venue names. Four real listings have their original posters; Lift uses the generic DJ banner from its source page. Real venue coordinates are approximate.

Each event has four distinct images, cover first, and one of four different 7-second party clips. Videos are H.264/yuv420p MP4, fast-start, muted, with JPEG thumbnails. Images and clips are copied to separate event-owned keys in private S3 storage and served through the existing authenticated media endpoints. Extra media on real listings is illustrative stock, not footage of those events. Listings are open for planning attendance inside this test app; actual tickets must be purchased from the external source.

The source ledgers `image-sources.json`, `video-sources.json`, and `scripts/event-refresh/real-events.json` preserve provenance. Videos were checked individually for the [Mixkit Stock Video Free License](https://mixkit.co/license/#videoFree); photos use Unsplash CDN URLs already used by the existing seed, plus additional stock images. Event poster rights remain with the original publishers; the bundle is for the requested test application. No external API keys or account credentials are included.

## Local preparation and review

Run from `backend/`. Download requires curl/ffmpeg; preparation requires ffprobe and Python 3.9+.

```sh
python3 scripts/event-refresh/download_assets.py .local/event-refresh-20261020
python3 scripts/event-refresh/prepare.py .local/event-refresh-20261020 --start 2026-10-21
python3 scripts/event-refresh/apply.py .local/event-refresh-20261020
```

The last command validates every asset hash and writes `import.sql` without accessing Podman, a database, or storage. Review `PREVIEW.md`, `events.json`, and `import.sql`. If resuming after 21 October, regenerate the bundle with a suitable start date; real events keep their actual dates and are included only when inside the new range.

## State on the Raspberry before the power outage

- SSH target: `david@david-raspberry.local`; project: `/mnt/ssd/apps/gik/backend`.
- Root Podman had no GIK containers, images or GIK volumes before this attempt. Rootless Podman had no GIK data either. No existing volumes were removed.
- Started the build using `sudo podman-compose -f deploy/test/compose.podman.yml up -d --build`. Last observed build stage was compilation of 135 Java sources. Completion is unknown.
- Started PostgreSQL separately; `gik_postgres_test` was up. Fresh named volumes are `gik-test_postgres-data` and `gik-test_minio-data`. No seed data was imported.
- Original compose is backed up remotely as `deploy/test/compose.podman.yml.before-20261020`. Only the obsolete DNS IP was changed remotely to `1.1.1.1`.
- Original MinIO Docker Hub images returned access denied; Quay also denied anonymous pulls. A Chainguard pull was started, but completion is unknown.
- Local compose now pins the Chainguard multi-platform MinIO index digest verified on 20 October, uses volume ownership adjustment for its non-root UID, and replaces the unavailable `minio/mc` image with a standard-library Python S3 bootstrap job.
- Build/storage logs: `/mnt/ssd/apps/gik/start-20261020.log`, `storage-start.log`, `minio-chainguard.log`.
- Running Caddy already contains `http://test-api-gik.nerizz.com` → `gik_backend_test:8080` on the `web` network. The actual public route/health has **not** been verified.

## Resume after power returns

First inspect GIK container/log state and the current remote Git diff. Transfer only the new scripts, updated compose, service unit and bundle; preserve `deploy/test/backend.env` and any remote changes. For example, from `backend/`:

```sh
rsync -av scripts/event-refresh/ david@david-raspberry.local:/mnt/ssd/apps/gik/backend/scripts/event-refresh/
rsync -av deploy/test/compose.podman.yml deploy/test/gik-test.service david@david-raspberry.local:/mnt/ssd/apps/gik/backend/deploy/test/
rsync -av .local/event-refresh-20261020/ david@david-raspberry.local:/mnt/ssd/apps/gik/event-refresh-20261020/
```

On the Pi, build/start using the existing environment:

```sh
cd /mnt/ssd/apps/gik/backend
sudo podman-compose -f deploy/test/compose.podman.yml up -d --build
sudo podman logs gik_minio_init_test
sudo podman logs --tail 100 gik_backend_test
curl -fsS -H 'Host: test-api-gik.nerizz.com' http://127.0.0.1:18080/health
curl -fsS https://test-api-gik.nerizz.com/health
```

If ownership of an existing MinIO volume needs adjustment, inspect it before changing it; `:U` recursively applies the specified MinIO UID to that named volume. MinIO must start and the bootstrap container must exit zero, with the bucket private. The backend must complete all 20 Flyway migrations. Do not reset PostgreSQL or Flyway history to bypass errors.

Import only after health succeeds:

```sh
sudo python3 scripts/event-refresh/apply.py /mnt/ssd/apps/gik/event-refresh-20261020 --apply
```

This command creates a timestamped `pg_dump -Fc` backup with mode 0600, uploads and HEAD-checks all content-addressed assets, then imports the event data in a single transaction. It replaces only `seed60-*` records and removes past `local-N`/`dev-N` events whose creator is NULL. It preserves other events and all user accounts. Re-running the same bundle keeps stable event IDs and object keys and updates cache timestamps. Each event owns its copies, so deleting an event or media item cannot break another event. Unreferenced objects from older bundles are not automatically deleted.

Verify the output: 60 dates × 3 events, 720 image rows and 180 video rows. Check authenticated `/api/events`, `/api/feed`, one original cover, a stock image, a video thumbnail, and MP4 playback/range requests using a legitimate test account. Never use a real user's account or reset their password for validation.

After successful verification, install the dedicated boot unit:

```sh
sudo install -m 0644 deploy/test/gik-test.service /etc/systemd/system/gik-test.service
sudo systemctl daemon-reload
sudo systemctl enable --now gik-test.service
```

This starts only GIK at boot and avoids enabling a global restart of unrelated Podman containers. A real reboot test remains to be done with the owner's availability. Restore, if needed, from the saved database dump into a separate recovery database first and inspect it before replacing live data.
