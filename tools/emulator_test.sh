#!/usr/bin/env bash
# Run by the emulator job: walks through Ferro on the cover screen's shape and the main screen's,
# with screenshots of each step, and fails if the app crashed.
set -x
PKG=com.cerniera.ferro
source tools/emu.sh

adb install -r Ferro.apk
adb logcat -c

# Home, first open
adb shell am start -n $PKG/.MainActivity
sleep 6; shot home
adb shell input swipe 540 1800 540 500 300; sleep 1.5; shot home-scrolled

# Begin Push and log three sets of the first exercise
adb shell am force-stop $PKG
adb shell am start -n $PKG/.MainActivity --es screen session
sleep 5; shot session
for i in 1 2 3; do tap_text "Log set $i of 3"; sleep 1.2; tap_text "Skip"; sleep 1; done
shot session-logged
tap_text "Log set 1 of 3"; sleep 2; shot resting
tap_text "Skip"; sleep 1

# The main screen's shape: logger and coach side by side
adb shell wm size 2256x2504; sleep 4; shot session-unfolded
adb shell wm size reset; sleep 3

# Finish and read the summary
adb shell input swipe 540 1800 540 300 300; sleep 1
tap_text "Finish session"; sleep 3; shot summary
tap_text "Done"; sleep 2; shot home-after

# Day light
adb shell "cmd uimode night no"; sleep 3; shot home-day
adb shell "cmd uimode night yes"; sleep 2

adb logcat -d > app-log.txt
if crashed; then echo "::error::Ferro crashed"; grep -A20 "FATAL EXCEPTION" app-log.txt | head -40; exit 1; fi
echo "No crash"
