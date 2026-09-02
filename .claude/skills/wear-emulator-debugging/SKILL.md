---
name: wear-emulator-debugging
description: Reproducing a recording bug on the Wear OS and phone emulators - installing, driving the UI, reading the app's own files, and the GPS trap. Use when a bug needs to be reproduced or a fix confirmed on a device.
---

# Debugging on the emulators

Reproduce before releasing. A fix for a bug that was only reasoned about goes
out as a PR; a fix for a bug that was watched happening goes out as a release.

## Devices

Emulate hardware close to what the author actually rides with: a **OnePlus Watch
2R** for the watch (Wear OS 5, round, 466x466) and a **Xiaomi 17 Ultra** class
phone. CI covers a round watch on API 34, a square watch on API 33, and a Pixel
6 on API 34.

## Basics

```
./gradlew :wear:installDebug
adb shell am start -n com.dchernykh.trainingrecorder/.MainActivity
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml -
adb shell input tap <x> <y>
```

The app's own files - the ride journal, stored settings, stored segments - are
readable without root:

```
adb shell run-as com.dchernykh.trainingrecorder ls files/
adb shell run-as com.dchernykh.trainingrecorder cat files/ride-journal.tsv
```

That journal is the fastest way to see what the app actually recorded, as
opposed to what it displayed.

## The GPS trap

**`adb emu geo fix` does not reach the app.** Neither does
`cmd location providers set-test-provider-location`. The positions the app
receives come from the emulator image's own route, which walks north-east from
Mountain View at a steady ~3.12 m/s.

With `whs.USE_SYNTHETIC_PROVIDERS` on, Health Services fabricates heart rate,
speed and distance but supplies **no position at all** - the journal's lat/lon
columns come back empty.

So for anything position-dependent: turn synthetic providers off, read two
consecutive points out of the journal to get the live position and velocity,
extrapolate the geometry you need from there, write it into the app's own
directory, and then start the ride. Allow about 75 seconds of travel before the
feature should trigger - force-stopping and relaunching costs that much while
the emulator's track keeps moving.

Two attempts were lost to this: one built test geometry in a country the
emulator could never reach, and the next used coordinates that were 400 m stale
because the track had carried on during the reinstall.

## Synthetic providers, when position does not matter

```
adb shell setprop whs.USE_SYNTHETIC_PROVIDERS true
adb shell am broadcast -a "whs.synthetic.user.START_EXERCISE" \
  --es "exercise" "BIKING" --ei "maxHeartRate" 150
```

## Decoding a FIT file

Pull the recorded file and decode it in a throwaway JUnit test in `:core`, which
already has the Garmin FIT SDK on its test classpath. That is how to check what
actually reached the file - session totals, track point altitudes - rather than
what the screen said.
