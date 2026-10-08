#!/usr/bin/env bash
# Emulatorda: ilovani YouTube havolasi bilan ochadi, video boshlanishini kutadi, HOME bosib
# fonga o'tkazadi va ijro davom etyaptimi — tekshiradi. Natija diag/ papkaga yig'iladi.
APK=android/app/build/outputs/apk/debug/app-debug.apk
PKG=com.example.ytlynx
VIDEO="https://www.youtube.com/watch?v=jNQXAC9IVRw"
mkdir -p diag
adb install -r "$APK" > diag/install.txt 2>&1
# "batareya tejashdan chiqarish" dialogi ekranni to'sib qo'ymasin
adb shell dumpsys deviceidle whitelist +$PKG > /dev/null 2>&1 || true
adb logcat -c
adb shell am start -W -a android.intent.action.VIEW -d "$VIDEO" -n $PKG/.MainActivity > diag/start.txt 2>&1
sleep 45
adb exec-out screencap -p > diag/screen.png
adb shell pidof $PKG > diag/pid.txt 2>&1 || echo "jarayon yo'q (crash?)" > diag/pid.txt
{
  echo "--- media_session (ilova ochiq)"
  adb shell dumpsys media_session | grep -A8 "YtLynx" || echo "sessiya topilmadi"
} > diag/session.txt 2>&1

# HOME: ilova fonga o'tadi — jarayon, servis va ijro (state=3 PLAYING) saqlanishi kerak
adb shell input keyevent KEYCODE_HOME
sleep 20
{
  echo "pid: $(adb shell pidof $PKG || echo 'YOQ - jarayon oldi')"
  adb shell dumpsys activity services $PKG | grep -E 'ServiceRecord|isForeground|foregroundServiceType' || echo "servis topilmadi"
  echo "--- media_session (fonda, 20 s keyin)"
  adb shell dumpsys media_session | grep -A8 "YtLynx" || echo "sessiya topilmadi"
} > diag/background.txt 2>&1

adb logcat -d -v time > diag/logcat.txt
exit 0
