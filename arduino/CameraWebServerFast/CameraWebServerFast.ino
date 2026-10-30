// Adapted from Espressif CameraWebServer, arduino-esp32 3.3.12.
// See UPSTREAM.md and LICENSE.upstream.md.
#include <Arduino.h>
#include "esp_camera.h"
#include <WiFi.h>
#include "board_config.h"

const char *apName = "ESP32-CAM";
const char *apPassword = "12345678";
const int apChannel = 1; // If needed, test 6 or 11 separately.

void startCameraServer();
void setupLedFlash();

void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(false);
  Serial.println();

  const bool hasPsram = psramFound();
  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Reserve up to VGA only when PSRAM is available, then start in QVGA.
  config.frame_size = hasPsram ? FRAMESIZE_VGA : FRAMESIZE_QVGA;
  config.jpeg_quality = 20; // Higher number = stronger JPEG compression.
  config.fb_count = hasPsram ? 2 : 1;
  config.fb_location = hasPsram ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  config.grab_mode = hasPsram ? CAMERA_GRAB_LATEST : CAMERA_GRAB_WHEN_EMPTY;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", static_cast<unsigned int>(err));
    return;
  }

  sensor_t *sensor = esp_camera_sensor_get();
  if (sensor == nullptr) {
    Serial.println("Camera sensor not found");
    return;
  }
  if (sensor->id.PID == OV3660_PID) {
    sensor->set_vflip(sensor, 1);
    sensor->set_brightness(sensor, 1);
    sensor->set_saturation(sensor, -2);
  }
  if (sensor->set_framesize(sensor, FRAMESIZE_QVGA) != 0 ||
      sensor->set_quality(sensor, 20) != 0) {
    Serial.println("Camera QVGA/quality setting failed");
    return;
  }

#if defined(LED_GPIO_NUM)
  setupLedFlash();
#endif

  WiFi.mode(WIFI_AP);
  if (!WiFi.softAP(apName, apPassword, apChannel, false, 2)) {
    Serial.println("WiFi AP start failed");
    return;
  }
  WiFi.setSleep(false);
  startCameraServer();

  Serial.println("===== ESP32-CAM FAST READY =====");
  Serial.printf("Camera PID: 0x%04x; PSRAM: %s\n",
                static_cast<unsigned int>(sensor->id.PID), hasPsram ? "yes" : "no");
  Serial.println("Default: 320x240 JPEG, quality 20");
  Serial.print("WiFi: ");
  Serial.println(apName);
  Serial.print("Page: http://");
  Serial.println(WiFi.softAPIP());
  Serial.print("Stream: http://");
  Serial.print(WiFi.softAPIP());
  Serial.println(":81/stream");
  Serial.println("Open ONE stream only. STREAM stats print every 3 seconds.");
}

void loop() {
  delay(1000);
}
