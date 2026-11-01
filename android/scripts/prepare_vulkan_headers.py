"""Prepare checksum-pinned build headers for the optional Android Vulkan backend.

Only header sources are downloaded. No SDK credentials or runtime model downloads
are involved; the Android NDK supplies the loader stub and host shader compiler.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import tempfile
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MAX_ARCHIVE_BYTES = 64 * 1024 * 1024
MAX_EXTRACTED_BYTES = 64 * 1024 * 1024
SOURCES = (
    {
        "name": "headers",
        "project": "KhronosGroup/Vulkan-Headers",
        "revision": "v1.4.313",
        "url": "https://codeload.github.com/KhronosGroup/Vulkan-Headers/zip/refs/tags/v1.4.313",
        "sha256": "debf13bfeba3b365f9d95d50c7cc7f1e21d93d241e7980ed1d16d67f065e73d5",
        "prefix": "Vulkan-Headers-1.4.313/",
    },
    {
        "name": "spirv",
        "project": "KhronosGroup/SPIRV-Headers",
        "revision": "vulkan-sdk-1.4.313.0",
        "url": "https://codeload.github.com/KhronosGroup/SPIRV-Headers/zip/refs/tags/vulkan-sdk-1.4.313.0",
        "sha256": "97515c2d267c95128abc50f88e221e49932b1af5414d57cd84b9efdf7c60a4cf",
        "prefix": "SPIRV-Headers-vulkan-sdk-1.4.313.0/",
    },
)

CONFIG = """# Generated from checksum-pinned SPIRV-Headers by prepare_vulkan_headers.py.
if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
  add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED GLOBAL)
  set_target_properties(SPIRV-Headers::SPIRV-Headers PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES "${CMAKE_CURRENT_LIST_DIR}/../include")
endif()
set(SPIRV-Headers_FOUND TRUE)
"""


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def archive(source: dict[str, str], cache: Path) -> Path:
    target = cache / (source["name"] + ".zip")
    if not target.is_file():
        cache.mkdir(parents=True, exist_ok=True)
        temp = target.with_suffix(".zip.part")
        try:
            with urllib.request.urlopen(source["url"], timeout=180) as response, temp.open("wb") as output:
                length = 0
                while block := response.read(1024 * 1024):
                    length += len(block)
                    if length > MAX_ARCHIVE_BYTES:
                        raise RuntimeError("Vulkan header archive exceeds the size limit")
                    output.write(block)
            if sha256(temp) != source["sha256"]:
                raise RuntimeError("Vulkan header archive checksum mismatch: " + source["name"])
            os.replace(temp, target)
        finally:
            temp.unlink(missing_ok=True)
    if target.stat().st_size > MAX_ARCHIVE_BYTES or sha256(target) != source["sha256"]:
        raise RuntimeError("Cached Vulkan header archive checksum mismatch: " + source["name"])
    return target


def extract(source: dict[str, str], path: Path, output: Path) -> None:
    with zipfile.ZipFile(path) as zipped:
        total = 0
        for info in zipped.infolist():
            if not info.filename.startswith(source["prefix"]):
                raise RuntimeError("Unexpected Vulkan header archive prefix")
            relative = PurePosixPath(info.filename[len(source["prefix"]):])
            if relative.is_absolute() or ".." in relative.parts or "\\" in info.filename:
                raise RuntimeError("Unsafe Vulkan header archive path")
            if stat.S_ISLNK(info.external_attr >> 16):
                raise RuntimeError("Unexpected symlink in Vulkan header archive")
            total += info.file_size
            if total > MAX_EXTRACTED_BYTES:
                raise RuntimeError("Expanded Vulkan header archive exceeds the size limit")
            if not relative.parts or info.is_dir():
                continue
            if relative.parts[0] == "include":
                destination = output / relative
            elif relative.parts[0] == "LICENSES" or relative.as_posix() in ("LICENSE", "LICENSE.md", "LICENSE.txt"):
                destination = output / "licenses" / source["name"] / relative
            else:
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            data = zipped.read(info)
            if len(data) != info.file_size:
                raise RuntimeError("Vulkan header archive entry size mismatch")
            destination.write_bytes(data)


def expected_metadata() -> dict:
    return {
        "format": 1,
        "sources": list(SOURCES),
        "cmake_config_sha256": hashlib.sha256(CONFIG.encode()).hexdigest(),
    }


def verify_ready(output: Path) -> bool:
    manifest = output / "provenance.json"
    if not manifest.is_file():
        return False
    data = json.loads(manifest.read_text(encoding="utf-8"))
    for key, value in expected_metadata().items():
        if data.get(key) != value:
            return False
    files = data.get("files", {})
    if not files or any(not (output / name).is_file() or sha256(output / name) != digest for name, digest in files.items()):
        return False
    return all(name in files for name in (
        "include/vulkan/vulkan.hpp", "include/vulkan/vulkan_core.h",
        "include/spirv/unified1/spirv.hpp", "cmake/SPIRV-HeadersConfig.cmake",
    ))


def prepare(cache: Path, output: Path) -> None:
    if output.exists():
        if verify_ready(output):
            print("Verified Vulkan build headers already ready:", output)
            return
        raise RuntimeError("Existing generated Vulkan headers are incomplete or modified; remove the generated output and retry")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=output.name + ".part-", dir=output.parent))
    try:
        for source in SOURCES:
            extract(source, archive(source, cache), temporary)
        cmake = temporary / "cmake/SPIRV-HeadersConfig.cmake"
        cmake.parent.mkdir(parents=True, exist_ok=True)
        cmake.write_text(CONFIG, encoding="utf-8")
        metadata = expected_metadata()
        metadata["files"] = {
            path.relative_to(temporary).as_posix(): sha256(path)
            for path in sorted(temporary.rglob("*")) if path.is_file()
        }
        (temporary / "provenance.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        if not verify_ready(temporary):
            raise RuntimeError("Prepared Vulkan headers did not verify")
        os.replace(temporary, output)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)
    print("Verified Vulkan build headers ready:", output)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path, default=ROOT / "third_party/.vulkan-cache")
    parser.add_argument("--output", type=Path, default=ROOT / "third_party/vulkan-headers")
    args = parser.parse_args()
    prepare(args.cache.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
