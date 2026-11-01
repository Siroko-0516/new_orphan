# Native template and JNI exception regressions

Prepare the pinned engine and model pack first. On Linux with a full JDK and CMake 3.22+:

```sh
cmake -S android/native-tests -B /tmp/giljabi-native-check
cmake --build /tmp/giljabi-native-check --target template_regression giljabi_guard_probe -j4
/tmp/giljabi-native-check/bin/template_regression android/model-packs/minicpm-1b/model.gguf
java -Djava.library.path=/tmp/giljabi-native-check/bin android/native-tests/NativeGuardProbe.java
```

The first test uses the real language GGUF with `vocab_only`. It asserts that the old system-only `common_chat_format_single` call reproduces `Unable to generate parser for this template`, then checks the production `render_turn` helper with the complete system/user turn. The second uses the production JNI guard header in a small JNI library and checks conversion of `std::invalid_argument`, allocation failure, and unknown C++ exceptions into Java exceptions, plus an unchanged successful return.

`NativeLocalSmoke.java` additionally exercises the production `LlamaEngine` API with both actual weights and a public JPEG across three reset/generate cycles. It needs a host build of the production JNI bridge using host JNI headers and a test-only Android logging shim. It is not an Android-device performance test. No API keys or user images are used by these tests.

## Production JNI + shared walking prompt protocol (Linux CPU)

After preparing the engine/model pair and running Android unit tests (which cache
org.json 20250517), build the explicitly selected production-bridge host target:

```sh
cmake -S android/native-tests -B /tmp/giljabi-native-check
cmake --build /tmp/giljabi-native-check --target giljabi_native -j4
python android/native-tests/run_guidance_smoke.py \
  --native-dir /tmp/giljabi-native-check/bin \
  --model android/model-packs/minicpm-1b/model.gguf \
  --vision android/model-packs/minicpm-1b/mmproj.gguf
```

The runner compiles the actual `WalkingGuidance` prompt/parser and `LlamaEngine`
Java interface with the exact production `llama_jni.cpp`. Only Android logging is
replaced by a small host shim. It uses the engine's public landscape JPEG, checks
strict typed guidance JSON and a nonempty English caption, and records raw public
fixture outputs and host-only phase times. It does not validate scene accuracy,
Korean translation quality, an Android/GPU runtime, or phone latency.
