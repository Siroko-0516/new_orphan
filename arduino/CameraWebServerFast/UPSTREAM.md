# Upstream

Based on Espressif Systems `arduino-esp32` tag **3.3.12**, `libraries/ESP32/examples/Camera/CameraWebServer`:

https://github.com/espressif/arduino-esp32/tree/3.3.12/libraries/ESP32/examples/Camera/CameraWebServer

`app_httpd.cpp` and `camera_index.h` preserve their Apache-2.0 copyright/license headers. The upstream repository's LGPL-2.1 text is included as `LICENSE.upstream.md`, and the Apache-2.0 text as `LICENSE.apache-2.0.txt`.

Unmodified upstream file SHA-256:

- `camera_index.h`: `c31bc07d50b88f23eb41378abdb898f84c1caee76bf8b4e28c1404abb2b5055e`
- `camera_pins.h`: `71de6987ee84a6d119596f3b52c8aead1b4a8e48cf4a7c3e1f39cc050ecf2754`

`app_httpd.cpp` originally had SHA-256 `f235a1bbc425fd815ef33f5ceb0927ecde1003514b663fd2fa5f8e07c748a1ba`. Local modifications: bounded QVGA/VGA control requests; one three-second diagnostic window for frame throughput and timings; corrected character buffer declaration; removed the fixed `X-Framerate: 60` hint. The main sketch is adapted for the AI Thinker pinout, AP mode, QVGA JPEG quality 20 defaults, and startup diagnostics. See the source diff for implementation.
