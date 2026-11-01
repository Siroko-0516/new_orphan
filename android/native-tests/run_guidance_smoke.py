"""Compile shared Java prompt/parser and run the production JNI on a public fixture.

This is a Linux CPU integration check, never a phone/GPU performance benchmark.
The model paths must contain the already-verified pinned model pair.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JSON_SHA256 = "3ea61b2a06e31edf1c91134fe9106b0ebb16628be169f3db75bc7a2b06b45796"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-dir", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--vision", required=True, type=Path)
    parser.add_argument("--fixture", type=Path, default=ROOT / "third_party/llama.cpp-omni/tools/server/webui/src/stories/fixtures/assets/1.jpg")
    parser.add_argument("--json-jar", type=Path)
    parser.add_argument("--prompt-file", type=Path, help="Candidate-only prompt experiment; omit for production shared prompt")
    parser.add_argument("--output", type=Path, default=Path("guidance-native-validation.json"))
    args = parser.parse_args()
    java_home = Path(os.environ["JAVA_HOME"])
    jars = [args.json_jar] if args.json_jar else list((Path.home() / ".gradle/caches").rglob("json-20250517.jar"))
    jar = next((p for p in jars if p and p.is_file() and hashlib.file_digest(p.open("rb"), "sha256").hexdigest() == JSON_SHA256), None)
    if jar is None:
        raise SystemExit("Verified org.json 20250517 jar missing; run Android unit tests first or pass --json-jar")
    with tempfile.TemporaryDirectory(prefix="giljabi-guidance-") as work:
        classes = Path(work)
        sources = [ROOT / "app/src/main/java/com/example/minicpm_v_demo/LlamaEngine.java",
                   ROOT / "app/src/main/java/dev/giljabi/WalkingGuidance.java",
                   ROOT / "app/src/main/java/dev/giljabi/SceneLanguage.java",
                   ROOT / "native-tests/NativeGuidanceSmoke.java"]
        subprocess.run([str(java_home / "bin/javac"), "-cp", str(jar), "-d", str(classes), *map(str, sources)], check=True)
        process = subprocess.run([str(java_home / "bin/java"), "-Xmx256m", "-Djava.library.path=" + str(args.native_dir.resolve()), "-cp", str(classes) + os.pathsep + str(jar), "dev.giljabi.NativeGuidanceSmoke", str(args.native_dir.resolve()), str(args.model.resolve()), str(args.vision.resolve()), str(args.fixture.resolve()), *([str(args.prompt_file.resolve())] if args.prompt_file else [])], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        print(process.stdout)
        lines = process.stdout.splitlines()
        report = {"host_only": True, "physical_phone_latency_verified": False,
                  "production_jni_used": True, "shared_walking_prompt_parser_used": args.prompt_file is None,
                  "guidance_protocol_passed": process.returncode == 0 and any(s.startswith("NATIVE_GUIDANCE_JSON_PASS") for s in lines),
                  "fixture": "public engine landscape 1.jpg", "scenery_alert_suppressed": any(s.startswith("NATIVE_GUIDANCE_JSON_PASS speak=false") for s in lines)}
        for prefix, name in [("NATIVE_GUIDANCE_REPLY=", "guidance_reply"), ("NATIVE_ENGLISH_CAPTION=", "english_caption"), ("HOST_SECONDS ", "host_phase_seconds"), ("HOST_CAPTION_SECONDS=", "host_caption_seconds")]:
            report[name] = next((s[len(prefix):] for s in lines if s.startswith(prefix)), None)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        if process.returncode:
            raise SystemExit(process.returncode)


if __name__ == "__main__":
    main()
