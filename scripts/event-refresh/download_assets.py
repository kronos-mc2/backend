"""Download seed assets locally. Requires curl and ffmpeg, no Python packages."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import subprocess
import tempfile

PHOTOS = [
    "1501386761578-eac5c94b800a", "1492684223066-81342ee5ff30", "1506157786151-b8491531f063",
    "1514525253161-7a46d19cd819", "1521334884684-d80222895322", "1511795409834-ef04bbd61622",
    "1517457373958-b7bdd4587205", "1470229722913-7c0e2dbbafd3", "1516280440614-37939bbacd81",
    "1511632765486-a01980e01a18", "1519167758481-83f550bb49b3", "1516450360452-9312f5e86fc7",
]
VIDEOS = {831:"dj-playing-music-at-a-nightclub", 4344:"people-dancing-at-a-night-club",
          4127:"dj-mixing-acetate-discs", 4188:"stage-of-an-electronic-music-festival"}


def fetch(url, path):
    subprocess.run(["curl", "-fL", "--retry", "2", "--max-time", "60", "-sS", url, "-o", str(path)], check=True)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    out = args.bundle / "assets"
    out.mkdir(parents=True, exist_ok=True)
    jobs = [(f"https://cdn.entr.io/images/events/{str(i)[:3]}/{i}_poster_300x400.png", out/f"entrio-{i}.png")
            for i in [34769, 33550, 31014, 29981]]
    jobs += [("https://cdn.entr.io/images/events/default/default_banner_dj_party.jpg", out/"entrio-dj-default.jpg")]
    jobs += [(f"https://images.unsplash.com/photo-{i}?auto=format&fit=crop&w=1200&q=80", out/f"stock-{n:02d}.jpg")
             for n, i in enumerate(PHOTOS)]
    with ThreadPoolExecutor(max_workers=6) as pool:
        for result in [pool.submit(fetch, *job) for job in jobs]:
            print("Downloaded", result.result().name)
    sources = []
    with tempfile.TemporaryDirectory(prefix="gik-assets-") as work:
        for number, slug in VIDEOS.items():
            page = f"https://mixkit.co/free-stock-video/{slug}-{number}/"
            html = fetch(page, Path(work)/f"video{number}.html").read_text()
            if 'data-license="videoFree"' not in html:
                raise RuntimeError(f"{page}: Free license could not be verified")
            urls = list(dict.fromkeys(re.findall(r"https://assets\.mixkit\.co/videos/"+str(number)+r'/[^" ]+\.mp4', html)))
            url = next((u for u in urls if "720.mp4" in u), urls[0])
            raw = fetch(url, Path(work)/f"raw-{number}.mp4")
            final = out/f"party-{number}.mp4"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(raw),
                "-t", "7", "-an", "-vf", "scale=720:-2", "-r", "25", "-c:v", "libx264",
                "-preset", "fast", "-crf", "25", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(final)], check=True)
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", "1", "-i", str(final),
                            "-frames:v", "1", str(out/f"party-{number}.jpg")], check=True)
            sources.append(dict(file=final.name, source=page, download=url,
                license="Mixkit Stock Video Free License", license_url="https://mixkit.co/license/#videoFree",
                duration_seconds=7, audio=False))
            print("Prepared video", final.name)
    (args.bundle/"video-sources.json").write_text(json.dumps(sources, indent=2)+"\n")
    images = [dict(file=path.name, source=url, kind="event-poster" if "entr.io" in url else "stock-photo") for url, path in jobs]
    (args.bundle/"image-sources.json").write_text(json.dumps(images, indent=2)+"\n")


if __name__ == "__main__":
    main()
