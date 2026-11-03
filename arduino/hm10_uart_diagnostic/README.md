# 길잡이 UNO · HM-10 수신 점검 v1

앱의 BLE TEST가 UNO까지 도착하는지 PC에서 확인하는 임시 코드입니다. 서보 D2, 버튼 D3, HM-10 TXD→UNO D0 배선을 사용합니다. UART와 PC 시리얼 모니터는 모두 **9600 baud**입니다. 모듈 설정을 바꾸는 AT 명령은 보내지 않습니다.

## 업로드와 연결

1. 전원을 끄고 HM-10의 D0·D1 통신선 두 개를 분리합니다. UNO를 USB에 연결해 `hm10_uart_diagnostic.ino`를 Arduino Uno 보드로 업로드합니다. Servo 라이브러리가 필요합니다.
2. 전원을 끄고 **HM-10 TXD→UNO D0(RX)만 다시 연결합니다. HM-10 RXD 선은 연결하지 않습니다.** 이 점검에서는 UNO D1을 USB 로그 출력에 사용합니다. GND는 공통 연결합니다. 서보 주황 D2, 빨강 5V, 갈색 GND와 버튼 S=D3 / V=3.3V / G=GND는 그대로 사용합니다.
3. 버튼을 놓고 전원을 켭니다. PC 시리얼 모니터를 **9600 baud**로 열고 `READY` 문구를 확인합니다. 이 점검에서는 시리얼 모니터를 열어 둡니다. 모니터에 직접 TEST를 입력하지 않습니다. 직접 입력하면 휴대폰에서 온 데이터인지 구분할 수 없습니다.
4. 앱의 AI·음성비서를 중지하고 BLE만 연결합니다. 앱에서 **아두이노 TEST 신호 보내기**를 한 번 누릅니다. PC에 나온 `RX HEX`, `COMMAND`, `FRAME`, `WAITING` 문구를 그대로 복사하거나 사진으로 남깁니다.
5. 물리 버튼도 한 번 누릅니다. 이 임시 코드에서는 음성비서를 부르는 대신 서보를 직접 시험합니다. 시작/리셋 때 버튼을 놓아야 하며, 누른 채 유지해도 반복 동작하지 않습니다.

## 결과 읽기

| PC 로그 또는 반응 | 확인되는 내용 |
| --- | --- |
| `RX HEX: 54 45 53 54 0A`와 `COMMAND: valid TEST/OBSTACLE -> servo` | 앱 TEST 전체가 UNO에 도착했고 서보 명령을 시작함 |
| `RX HEX: 54 45 53 54 0D 0A` | CR+LF로 도착한 TEST도 처리함 |
| 앱 TEST를 눌러도 `WAITING: no UART bytes received yet`만 계속 표시 | 이 UNO 코드에는 UART 수신이 잡히지 않음; BLE 쓰기 완료만으로는 UNO 수신을 확인할 수 없음 |
| `RX HEX`에 다른 값이나 `COMMAND: rejected` | 데이터는 들어옴; 실제 값으로 통신 속도·신호·전송 내용을 점검해야 함 |
| `FRAME: waiting for newline` | 일부 데이터는 도착했으나 줄바꿈을 아직 받지 못함 |
| 물리 버튼에서 `BUTTON: local servo test`와 서보 움직임 | 앱·BLE를 거치지 않은 로컬 서보 구동은 동작함 |
| `COMMAND: valid`인데 서보가 움직이지 않음 | 명령 수신 이후의 서보 신호·전원 경로를 점검해야 함 |

L LED는 유효한 경고 또는 물리 버튼의 약 1.74초 서보 명령 동안 켜집니다. TX/RX LED는 USB 통신에도 반응합니다. `SERVO: command sequence finished`는 명령 일정 종료를 뜻하며 실제 축 회전을 측정한 결과는 아닙니다.

전원은 기존 정격을 지키고, 서보 기구물은 분리한 상태로 시험하세요. 수신 기록은 처음 32바이트만 표시하며, 알 수 없는 명령·깨진 문자·과도하게 긴 줄은 모터를 작동시키지 않습니다. 이 코드는 로그를 출력하므로 **HM-10 RXD는 분리한 채로** 사용합니다.

점검 후에는 일반 v4 코드를 다시 업로드하고 UNO D1→분압→HM-10 RXD도 복구합니다. 일반 코드의 버튼은 다시 `ASSIST`를 보냅니다.

[점검 코드 ZIP](../../downloads/Giljabi-UNO-UART-Diagnostic-v1.zip) · [일반 v4 코드 ZIP](../../downloads/Giljabi-UNO-HM10-SG90-v4.zip)
