#!/usr/bin/env python3
"""Stage actual GGUF weights and their publisher's license for a fully offline APK."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def stage(model, vision, license_file, output, source):
    if not license_file.is_file() or not license_file.read_bytes().strip():
        raise ValueError("Provide the model publisher's actual weight license")
    for path in (model, vision):
        with path.open("rb") as stream:
            if stream.read(4) != b"GGUF" or path.stat().st_size < 8:
                raise ValueError(f"Not a GGUF file: {path}")
    # A sub-2GB asset pair keeps this direct-install APK below ZIP32/Android limits.
    if model.stat().st_size + vision.stat().st_size > 1_900_000_000:
        raise ValueError("This model pair exceeds the supported single-APK size; use a smaller compatible pair")
    output.mkdir(parents=True, exist_ok=True)
    manifest = ["format=1"]
    records = {}
    for key, name, path in (("model", "model.gguf", model), ("vision", "mmproj.gguf", vision)):
        target = output / name
        if target.resolve() == path.resolve():
            raise ValueError("Output must differ from the input directory")
        shutil.copyfile(path, target)
        sha = hashlib.sha256()
        with target.open("rb") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                sha.update(block)
        manifest.extend((f"{key}.bytes={target.stat().st_size}", f"{key}.sha256={sha.hexdigest()}"))
        records[key] = {"original_file": path.name, "bytes": target.stat().st_size, "sha256": sha.hexdigest()}
    shutil.copyfile(license_file, output / "LICENSE.txt")
    (output / "provenance.json").write_text(json.dumps({"source": source, "weights": records}, indent=2) + "\n")
    # Manifest is committed last: partial staging must never look like a usable pack.
    (output / "manifest.properties").write_text("\n".join(manifest) + "\n", encoding="ascii")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--vision", type=Path, required=True)
    parser.add_argument("--license", type=Path, required=True)
    parser.add_argument("--source", required=True, help="Publisher repository URL and preferably pinned revision")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    stage(args.model, args.vision, args.license, args.output, args.source)
