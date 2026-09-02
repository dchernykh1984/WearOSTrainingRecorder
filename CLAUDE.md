# TrainingRecorder

A Wear OS watch app that records rides on the watch itself and uploads them to
Strava and Garmin Connect, plus an Android phone companion that owns the
configuration. The phone is not needed during a ride.

## Modules

| Module | What it is | Notes |
|---|---|---|
| `:core` | Plain JVM Kotlin, no Android | Everything worth testing lives here. Kover gate at 80%. |
| `:localization` | String resources in 15 languages | `Labels.kt` maps stable ids to resources. |
| `:wear` | The watch app | minSdk 30. Records, stores, uploads. |
| `:mobile` | The phone companion | minSdk 26. Owns settings, credentials, segments. |

The split is the point: a value the screen shows is a pure function of the
recording, the sensors and the chosen units, so it can be tested without a
watch. When something can be decided in `:core`, decide it there.

## Rules that are not negotiable

**No invented thresholds in recorded data.** No distance filters, no speed
floors, no auto-pause, no movement detection derived from speed. Record what the
sensors reported. A 3 m per-pair distance filter - reasonable for cycling -
silently discarded every step of a 200 m walk and recorded it as 3 m, which is
worse than recording nothing because it looks like it worked. Matching and
prediction logic may model (segment matching uses a heading test and a start
radius); measurements may not.

**Nothing is not zero.** An absent value renders as `--`, never as `0`. A rider
seeing `0 W` believes the meter; seeing a dash they look for it.

**No credentials ship in the app.** Each rider registers their own Strava API
application and enters its client id and secret. The Garmin password stays on
the phone and never reaches the watch - the watch uploads with a token. Never
add a shared client secret, and never claim in the UI that stored credentials
are encrypted.

**Every user-facing string is localized** into all 15 locales under
`localization/src/main/res/values*/`, and mapped in `Labels.kt`. `LabelsTest`
fails the build if a catalogue entry has no label.

**Commit messages are one line.** Conventional commits (release-please parses
them): `fix(core): let the altitude settle window actually close`. No body, no
co-authorship trailers, no "Generated with" lines.

## Comments

Comments explain why, not what. The bar: a comment should say something the code
cannot, usually the failure that motivated the line. Match the density and voice
of the surrounding file - this codebase comments its reasoning heavily, and a
bare change in the middle of that reads as unfinished.

## Testing

Tests carry the design decisions, so write them as prose a reader can follow.
Two failure modes have each let a real bug through here:

- **Implausible test data defends bugs.** A "watch on a table" test that drifted
  north at 1.5 m/s - a brisk walk - defended a broken distance filter. Altitude
  tests that climbed at 1 m/s defended a broken climb rule. Use speeds a person
  can actually produce.
- **Too-regular test data hides bugs.** Readings spaced exactly 1000 ms apart
  hid a settle window that could never close, and total ascent read zero on
  every real ride for two releases. Jitter timestamps the way the platform
  delivers them.

Before trusting a new test, break the fix and watch it fail. A test that passes
against the bug it claims to catch is worse than none.

## Where things are

- `core/.../field/` - the catalogue of every value a rider can put on a screen,
  and the pure function that renders them.
- `core/.../track/` - distance, altitude, climb, power averages, gradient.
- `core/.../segment/` - Strava live segments: the line, the reference effort,
  the matcher.
- `core/.../connector/` - Strava and Garmin protocols, kept away from any HTTP
  client so what is sent and what a response means can be tested.
- `core/.../datalayer/` - the wire formats between phone and watch.
- `wear/.../recording/RecordingViewModel.kt` - the hub: it owns the sensors, the
  trackers and the tick that writes track points.

## Skills

`.claude/skills/` holds the procedures: shipping a change, running the local
gate, debugging on the emulators, adding a data field, publishing news about the
app. Read the relevant one before starting that kind of work.
