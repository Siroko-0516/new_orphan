"""Download pinned, hash-verified MiniCPM weights and stage the full APK's assets."""
import argparse
import concurrent.futures
import hashlib
import json
import shutil
import urllib.request
from pathlib import Path
from bundle_models import stage


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fetch(url, target, expected_hash, expected_bytes=None):
    if target.is_file() and (expected_bytes is None or target.stat().st_size == expected_bytes) and digest(target) == expected_hash:
        print(f"Verified cached {target.name}", flush=True)
        return target
    partial = target.with_name(target.name + ".part")
    for attempt in range(3):
        try:
            offset = partial.stat().st_size if partial.exists() else 0
            request = urllib.request.Request(url, headers={"Range": f"bytes={offset}-"})
            with urllib.request.urlopen(request, timeout=90) as response:
                if response.status == 206:
                    if not response.headers.get("Content-Range", "").startswith(f"bytes {offset}-"):
                        raise ValueError("Unexpected download range")
                elif response.status == 200:
                    offset = 0
                else:
                    raise ValueError(f"Unexpected HTTP status {response.status}")
                count = offset
                last = offset
                with partial.open("ab" if offset else "wb") as stream:
                    while block := response.read(4 * 1024 * 1024):
                        count += len(block)
                        if expected_bytes is not None and count > expected_bytes:
                            raise ValueError("Oversized download")
                        stream.write(block)
                        if count - last >= 128 * 1024 * 1024:
                            print(f"{target.name}: {count // 1048576} MiB", flush=True)
                            last = count
            if expected_bytes is not None and partial.stat().st_size != expected_bytes:
                raise ValueError("Incomplete download")
            if digest(partial) != expected_hash:
                partial.unlink()
                raise ValueError("SHA-256 mismatch")
            partial.replace(target)
            print(f"Verified {target.name}", flush=True)
            return target
        except Exception as error:
            print(f"{target.name}: attempt {attempt + 1} failed ({type(error).__name__})", flush=True)
            if attempt == 2:
                raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", type=Path, default=Path(__file__).resolve().parents[1] / "model-specs/minicpm-1b.json")
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    spec = json.loads(args.spec.read_text())
    args.cache.mkdir(parents=True, exist_ok=True)
    base = f"https://huggingface.co/{spec['repository']}/resolve/{spec['revision']}/"
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(fetch, base + spec[key]["filename"] + "?download=true", args.cache / spec[key]["filename"], spec[key]["sha256"], spec[key]["bytes"]) for key in ("model", "vision")]
        model, vision = (future.result() for future in futures)
    license_file = fetch(spec["license"]["url"], args.cache / "WEIGHTS-LICENSE.txt", spec["license"]["sha256"])
    source = f"https://huggingface.co/{spec['repository']}/tree/{spec['revision']}"
    stage(model, vision, license_file, args.output, source)
    shutil.copyfile(args.spec, args.output / "model-spec.json")
    print(f"Full APK assets ready: {args.output}", flush=True)


if __name__ == "__main__":
    main()
