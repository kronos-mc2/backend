"""Generate a reviewable 60-day event bundle, without touching a database."""
import argparse
from collections import Counter
import datetime as dt
import hashlib
import json
from pathlib import Path
import random
import subprocess
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
ZONE = ZoneInfo("Europe/Zagreb")
PREFIX = "seed60-"
VIDEOS = [831, 4344, 4127, 4188]

# Fictional venues avoid suggesting an actual venue has booked these demo events.
VENUES = [
    ("Studio Ritma (demo)", "Trešnjevka, Zagreb", 45.7982, 15.9478),
    ("Salon 19 (demo)", "Donji grad, Zagreb", 45.8091, 15.9768),
    ("Klub Val (demo)", "Trnje, Zagreb", 45.7902, 15.9814),
    ("Prostor Iskra (demo)", "Maksimir, Zagreb", 45.8251, 16.0157),
    ("Dnevni boravak (demo)", "Jarun, Zagreb", 45.7851, 15.9294),
    ("Atelje Susret (demo)", "Gornji grad, Zagreb", 45.8189, 15.9734),
]
THEMES = [
    ("Vinyl Afterwork", "Funk i disco selekcija na pločama uz opušteno druženje nakon posla.", ["Disco","Funk","DJ Set","Groovy"]),
    ("Neon House Sessions", "House večer s toplim bas linijama, lokalnim DJ setovima i plesnim podijem.", ["House","Club Night","Party"]),
    ("Jazz u dnevnom boravku", "Mali akustični sastav donosi jazz standarde i prostor za razgovor između setova.", ["Jazz","Live Music","Intimate"]),
    ("Indie Radar", "Večer novih gitarskih zvukova uz nastupe zamišljenih lokalnih indie bendova.", ["Indie","Alternative","Concert","Energetic"]),
    ("Kviz i craft", "Ekipni pub kviz s pitanjima iz glazbe, filma i svakodnevnog života.", ["Pub Quiz","Friendly","Locals"]),
    ("Salsa Social", "Uvodni plesni koraci i društveni ples uz latino ritmove; partner nije potreban.", ["Latin","Workshop","Beginners","Friendly"]),
    ("Stand-up laboratorij", "Otvoreni mikrofon za nove fore, kratke nastupe i opuštenu publiku.", ["Stand-up","Open Mic","Casual"]),
    ("R&B Late Lounge", "Soul i R&B selekcija za intimnu večer u prigušenom svjetlu.", ["R&B","Soul","DJ Set","Cozy"]),
    ("Techno Podrum", "Klupska večer s industrijskim zvukom, sporim uvodom i intenzivnim završnim setom.", ["Techno","Club Night","Underground","Late Night"]),
    ("Akustični krug", "Kantautorska druženja, ogoljene gitare i male priče između pjesama.", ["Acoustic","Live Music","Chill"]),
    ("Filmski klub", "Druženje ljubitelja filma uz razgovor o režiji, omiljenim scenama i filmskoj glazbi.", ["Movie Night","Meetup","Creatives"]),
    ("Board Game Café", "Društvene igre za male ekipe i početnike, uz kratka objašnjenja pravila.", ["Game Night","Gamers","Beginners","Friendly"]),
    ("Retro plesnjak", "Plesni hitovi osamdesetih i devedesetih uz disco kuglu i nostalgičnu atmosferu.", ["Pop","Disco","Club Night","Retro","Nostalgic"]),
    ("Kava i kod", "Neformalni susret developera: kratke demo prezentacije i razgovor o projektima.", ["Meetup","Developers","Networking","Casual"]),
    ("Urban Sketch Club", "Kreativno crtanje gradskih motiva u zatvorenom studiju uz razmjenu skica.", ["Workshop","Artists","Artsy","Beginners"]),
    ("Groove Collective", "Funk ritmovi, soul vokali i DJ selekcija za druženje na plesnom podiju.", ["Funk","Soul","DJ Set","Groovy"]),
    ("Karaoke bez treme", "Poznati refreni, zajedničko pjevanje i podrška publici koja prvi put izlazi na mikrofon.", ["Pop","Karaoke","Friendly","Feel-good"]),
    ("Fotografski susreti", "Razgovor o uličnoj fotografiji i mali zadatak za vježbanje kompozicije.", ["Workshop","Meetup","Artists","Creatives"]),
    ("Blues & Bourbon", "Večer blues standarda i akustičnih improvizacija u malom klupskom prostoru.", ["Blues","Live Music","Intimate"]),
    ("Drum & Bass Junction", "Brzi breakbeatovi, duboki bas i energičan program za ljubitelje drum & bassa.", ["Drum & Bass","Club Night","Energetic"]),
    ("Poetski mikrofon", "Autorska poezija i spoken word nastupi uz kratke otvorene prijave.", ["Open Mic","Artists","Artsy","Intimate"]),
    ("Disco Brunch Club", "Dnevno druženje uz vedre disco ritmove i razgovor oko zajedničkog stola.", ["Disco","Food Event","Feel-good","Afternoon"]),
    ("Synth City", "Elektronički pop i retro sintesajzeri u klupskom programu nadahnutom noćnim gradom.", ["Pop","EDM","DJ Set","Retro"]),
    ("Acoustic Stories", "Mali kantautorski setovi koji spajaju akustičnu glazbu i pripovijedanje.", ["Acoustic","Concert","Cozy"]),
    ("Creative Exchange", "Susret dizajnera i kreativaca s kratkim predstavljanjem ideja i druženjem.", ["Networking","Meetup","Creatives","Friendly"]),
    ("Soul Kitchen Sessions", "Soul glazba, ležeran ritam i zajedničko druženje uz zamišljeni pop-up food program.", ["Soul","Food Event","Chill"]),
    ("Rock Revival", "Gitarski hitovi i klupska selekcija za ljubitelje rocka i alternativne scene.", ["Rock","Alternative","DJ Set","Loud"]),
    ("Afro House Flow", "Afrobeat i house selekcija s naglaskom na perkusije i zajednički ples.", ["House","Afrobeat","Club Night","Groovy"]),
    ("Impro večer", "Kratke improvizacijske scene u kojima prijedlozi publike stvaraju nove priče.", ["Theatre","Workshop","Experimental"]),
    ("Lo-Fi Listening Room", "Mirno slušanje downtempo selekcije i druženje u malom prostoru.", ["DJ Set","Chill","Relaxed"]),
]


def media_metadata(path):
    info = json.loads(subprocess.check_output([
        "ffprobe", "-v", "quiet", "-show_streams", "-show_format", "-of", "json", str(path)]))
    stream = next(s for s in info["streams"] if s["codec_type"] == "video")
    data = path.read_bytes()
    result = dict(file=path.name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                  width=stream["width"], height=stream["height"],
                  content_type={".png":"image/png", ".jpg":"image/jpeg", ".mp4":"video/mp4"}[path.suffix])
    if path.suffix == ".mp4":
        result["duration_seconds"] = float(info["format"]["duration"])
        assert 3 <= result["duration_seconds"] <= 10
        assert len(data) < 10 * 1024 * 1024
        assert stream["codec_name"] == "h264" and stream["pix_fmt"] == "yuv420p"
    return result


def build(start, assets):
    rng = random.Random(20261020)
    real = json.loads((HERE / "real-events.json").read_text())
    actual = {}
    for event in real:
        actual.setdefault(dt.datetime.fromisoformat(event["start"]).date(), []).append(event)
    events = []
    for offset in range(60):
        date = start + dt.timedelta(days=offset)
        daily_real = actual.get(date, [])[:3]
        for slot in range(3):
            if slot < len(daily_real):
                e = dict(daily_real[slot], kind="verified-listing")
                e["about"] += " Prijava u testnoj aplikaciji služi planiranju dolaska i nije ulaznica. Dodatne stock slike i video su ilustracija, nisu snimke ovog događaja."
            else:
                theme_index = (offset * 3 + slot) % len(THEMES)
                title, description, tags = THEMES[theme_index]
                venue, address, lat, lng = VENUES[(offset + slot * 2) % len(VENUES)]
                season = "Advent Edition" if date.month == 12 else "Autumn Sessions"
                e = dict(kind="demo", title=f"{title} — {season} {offset // 10 + 1}",
                         start=dt.datetime.combine(date, dt.time([18,20,22][slot]), ZONE).isoformat(),
                         venue=venue, address=address, lat=lat, lng=lng,
                         cover=f"stock-{rng.randrange(12):02d}.jpg", source=None,
                         tags=tags, about=description + " " + rng.choice([
                             "Dođi samostalno ili povedi društvo; program je zamišljen za upoznavanje novih ljudi.",
                             "Opušten početak večeri prelazi u življu atmosferu uz male pauze za razgovor.",
                             "Mali prostor i neformalni program naglašavaju dobru atmosferu i zajedničko iskustvo.",
                         ]) + " Demo događaj za testiranje GIK aplikacije; naziv prostora, termin i program su izmišljeni. Fotografije i party video služe kao ilustracija.")
            e["id"] = f"{PREFIX}{date.isoformat()}-{slot+1}"
            # Unknown real end times stay unknown. Demo end times are timezone aware.
            e["end"] = (dt.datetime.fromisoformat(e["start"]) + dt.timedelta(hours=3)).isoformat() if e["kind"] == "demo" else None
            available = [f"stock-{i:02d}.jpg" for i in range(12) if f"stock-{i:02d}.jpg" != e["cover"]]
            e["images"] = [e["cover"]] + rng.sample(available, 3)
            v = VIDEOS[(offset * 3 + slot) % len(VIDEOS)]
            e["video"] = f"party-{v}.mp4"
            e["video_thumbnail"] = f"party-{v}.jpg"
            events.append(e)
    counts = Counter(dt.datetime.fromisoformat(e["start"]).astimezone(ZONE).date() for e in events)
    assert len(events) == 180 and len(counts) == 60 and set(counts.values()) == {3}
    assert len({e["id"] for e in events}) == 180
    filenames = {name for e in events for name in e["images"] + [e["video"], e["video_thumbnail"]]}
    metadata = {name: media_metadata(assets / name) for name in sorted(filenames)}
    return dict(schema_version=1, timezone="Europe/Zagreb", start=str(start),
                end=str(start + dt.timedelta(days=59)), events=events, assets=metadata,
                verified_at="2026-10-20", coordinates_note="Venue coordinates are approximate map pins.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("--start", type=dt.date.fromisoformat, default=dt.datetime.now(ZONE).date()+dt.timedelta(days=1))
    args = parser.parse_args()
    bundle = build(args.start, args.bundle / "assets")
    (args.bundle / "events.json").write_text(json.dumps(bundle, indent=2, ensure_ascii=False)+"\n")
    rows = ["# GIK event preview", "", f"{bundle['start']} – {bundle['end']}; 3 events/day; Europe/Zagreb", "",
            "| Local start | Title | Kind |", "|---|---|---|"]
    rows += [f"| {e['start']} | {e['title']} | {e['kind']} |" for e in bundle["events"]]
    (args.bundle / "PREVIEW.md").write_text("\n".join(rows)+"\n")
    print(json.dumps(dict(events=180, days=60, verified=sum(e["kind"]=="verified-listing" for e in bundle["events"]),
                          assets=len(bundle["assets"]), start=bundle["start"], end=bundle["end"])))


if __name__ == "__main__":
    main()
