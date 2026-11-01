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
