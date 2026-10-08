#!/usr/bin/env bash
# iOS simulatorda: ilovani yig'adi, ochadi, YouTube havolasini beradi va skrinshot oladi.
# Natija diag/ papkaga yig'iladi.
mkdir -p diag
cd ios || exit 1

xcodebuild -workspace YtLynx.xcworkspace -scheme YtLynx -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build-sim \
  CODE_SIGNING_ALLOWED=NO build > ../diag/xcode-sim.txt 2>&1
APP=build-sim/Build/Products/Debug-iphonesimulator/YtLynx.app
if [ ! -d "$APP" ]; then
  echo "simulator uchun yig'ilmadi" > ../diag/sim.txt
  grep -E 'error:' ../diag/xcode-sim.txt | head -40 >> ../diag/sim.txt
  exit 0
fi

# birinchi mavjud iPhone simulatori
UDID=$(xcrun simctl list devices available -j | node -e '
  let s = ""; process.stdin.on("data", d => s += d).on("end", () => {
    const j = JSON.parse(s);
    for (const [runtime, devices] of Object.entries(j.devices)) {
      if (!/iOS/.test(runtime)) continue;
      const d = devices.find(d => /iPhone/.test(d.name));
      if (d) { console.log(d.udid); return; }
    }
  });')
{
  echo "simulator: $UDID"
  xcrun simctl boot "$UDID"
  xcrun simctl bootstatus "$UDID" -b > /dev/null
  xcrun simctl install "$UDID" "$APP" && echo "o'rnatildi"
  xcrun simctl launch "$UDID" com.example.ytlynx && echo "ishga tushdi"
  sleep 15
  # "Ulashish" o'rniga: ytlynx://open?url=...
  xcrun simctl openurl "$UDID" "ytlynx://open?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DjNQXAC9IVRw" && echo "havola ochildi"
  sleep 40
  xcrun simctl io "$UDID" screenshot ../diag/ios-screen.png > /dev/null && echo "skrinshot olindi"
  # ilova jarayoni tirikmi
  xcrun simctl spawn "$UDID" launchctl list | grep -i ytlynx || echo "jarayon topilmadi (crash?)"
} > ../diag/sim.txt 2>&1

xcrun simctl spawn "$UDID" log show --last 3m --style compact \
  --predicate 'process == "YtLynx"' > ../diag/ios-log.txt 2>&1
exit 0
