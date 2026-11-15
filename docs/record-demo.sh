#!/bin/bash
# 데모 영상 자동 녹화 스크립트 (docs/demo-script.md 시나리오)
# 사용법: ./docs/record-demo.sh <출력.webm>
# 전제: Pleos Connect Emulator 실행 중, 앱 설치됨, 시뮬레이션 패널 열린 상태(기본값)
set -u
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
OUT="${1:-demo.webm}"
PKG=ai.pleos.warninglight

# 시뮬레이션 패널 버튼 좌표 (2560x1440 기준). 행 순서는 WarningCatalog 순서.
ROW_Y_BRAKE=221; ROW_Y_ENGINE=365; ROW_Y_TPMS=653
X_OFF=2349; X_ON=2403; X_BLINK=2463

tap() { "$ADB" shell input tap "$1" "$2"; }

# 화면에서 글자를 찾아 그 가운데를 누른다 (자막 표시로 버튼 위치가 바뀌므로).
tap_text() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b
  b=$("$ADB" shell cat /sdcard/ui.xml | grep -oE "text=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
  if [ -z "$b" ]; then echo "찾지 못함: $1"; return 1; fi
  local x1 y1 x2 y2
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | sed 's/\]\[/,/' | tr -d '[]' | tr ',' ' ')"
  tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

echo "== 초기화"
"$ADB" shell am force-stop $PKG
"$ADB" logcat -c
sleep 1

echo "== 녹화 시작"
"$ADB" emu screenrecord start --time-limit 170 "$OUT" >/dev/null
sleep 1

echo "0) 앱 실행 → 시동 브리핑"
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
sleep 10

echo "1) 타이어 공기압 경고 점등"
tap $X_ON $ROW_Y_TPMS
sleep 16

echo "2) 가까운 블루핸즈 길안내"
tap_text "가까운 블루핸즈 길안내"
sleep 10

echo "3) 앱으로 복귀"
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
sleep 3

echo "4) 브레이크 경고 추가 → 최고 심각도로 전환"
tap $X_ON $ROW_Y_BRAKE
sleep 15

echo "5) 긴급출동 화면"
tap_text "긴급출동 호출"
sleep 7
tap_text "닫기"
sleep 1

echo "6) 브레이크 끄고 엔진 경고등 점멸 → 즉시 정차로 승격"
tap $X_OFF $ROW_Y_BRAKE
sleep 3
tap $X_BLINK $ROW_Y_ENGINE
sleep 15

echo "== 녹화 종료"
"$ADB" emu screenrecord stop >/dev/null
sleep 3
"$ADB" logcat -d -s Speech:I > "${OUT%.*}-speech.log"
echo "완료: $OUT"
