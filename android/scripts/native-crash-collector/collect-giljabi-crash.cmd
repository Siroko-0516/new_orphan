@echo off
rem SPDX-License-Identifier: AGPL-3.0-only
setlocal
chcp 65001 >nul
pushd "%~dp0"
if not exist "adb.exe" (
  echo adb.exe was not found.
  echo Extract the official Android platform-tools ZIP first.
  echo Copy this CMD file into the platform-tools folder beside adb.exe.
  echo Download: https://dl.google.com/android/repository/platform-tools-latest-windows.zip
  pause
  popd
  exit /b 1
)
echo Connect your phone with USB debugging enabled.
echo Unlock the phone and accept the USB debugging authorization prompt.
echo.
adb.exe devices
adb.exe get-state 2>nul | findstr /x "device" >nul
if errorlevel 1 (
  echo No single authorized device was found.
  echo If unauthorized, unlock the phone and accept the debugging prompt.
  echo If multiple devices are connected, disconnect the other devices.
  pause
  popd
  exit /b 1
)
echo.
echo If Giljabi already crashed recently, press any key to collect the log.
echo Otherwise start ESP video in Giljabi, wait for the crash, then return here.
echo This tool only reads the crash log; it does not clear logs or app data.
pause
adb.exe logcat -b crash -d -v threadtime -t 1200 > "giljabi-crash-local.txt"
if errorlevel 1 (
  echo Could not read the Android crash log.
  pause
  popd
  exit /b 1
)
powershell -NoProfile -Command "$raw = Get-Content -LiteralPath 'giljabi-crash-local.txt' -Raw -Encoding UTF8; $chunks = [regex]::Split($raw, '(?m)(?=^.*\*\*\* \*\*\* \*\*\*)'); $ours = @($chunks | Where-Object { $_ -match '(>>> dev\.giljabi\.camera <<<|Cmdline:\s*dev\.giljabi\.camera(\s|$))' }); if ($ours.Count -eq 0) { 'No Giljabi native crash was found in the recent crash buffer. Reproduce the crash and run this tool again.' | Set-Content -LiteralPath 'giljabi-native.txt' -Encoding UTF8; exit 2 }; $last = $ours[-1]; $lines = @($last -split '\r?\n' | Where-Object { $_ -match '(Abort message:|signal \d+|backtrace:|#\d+\s+pc|>>>|Cmdline:|pid:|ABI:|Build fingerprint:|Cause:)' } | Select-Object -First 90); if ($lines.Count -eq 0) { 'A Giljabi record was found but no native backtrace was available. Reproduce the crash and collect it again.' | Set-Content -LiteralPath 'giljabi-native.txt' -Encoding UTF8; exit 2 }; $lines | Set-Content -LiteralPath 'giljabi-native.txt' -Encoding UTF8"
if errorlevel 1 (
  echo No readable Giljabi native backtrace was extracted.
  echo Open giljabi-native.txt for instructions, then reproduce and collect again.
) else (
  echo Saved giljabi-native.txt - share the text in that file.
  echo giljabi-crash-local.txt contains the local raw crash buffer; do not share it wholesale.
)
echo No logs are uploaded automatically.
notepad.exe "giljabi-native.txt"
pause
popd
