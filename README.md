# WearOSTrainingRecorder

A Wear OS app that records training sessions on the watch - GPS track, data from
the built-in and external (Bluetooth LE) sensors, and live workout metrics on
screen - and syncs the finished session to the services connected to the app
(Strava, Garmin Connect).

The repository holds two modules:

- `:wear` - the watch app; it records and stores a session standalone, without a
  phone;
- `:mobile` - the phone companion; it authorizes the third-party services and
  forwards finished sessions to them.

Both ship under a single `applicationId`, so Google Play delivers the matching
APK for each form factor.

## Setup

### 1. Download the project

Install Git if you don't have it:

- **macOS:** `brew install git`
- **Linux (Ubuntu / Debian):** `sudo apt install git`
- **Windows:** download from [git-scm.com](https://git-scm.com/downloads) and run the installer

Then clone the repository:

```bash
git clone https://github.com/dchernykh1984/WearOSTrainingRecorder.git
cd WearOSTrainingRecorder
```

All subsequent commands should be run from the `WearOSTrainingRecorder` folder.

### 2. Install a JDK 17

The Android Gradle Plugin used here targets Java 17.

- **macOS:** `brew install --cask temurin@17`
- **Linux (Ubuntu / Debian):** `sudo apt install openjdk-17-jdk`
- **Windows:** download **Temurin 17** from [adoptium.net](https://adoptium.net/) and run the installer

Verify it is the active JDK:

```bash
java -version
```

The output should report version `17`.

### 3. Install the Android SDK

**Easiest (recommended): Android Studio.** It bundles the SDK, emulators, and
device tooling.

- **macOS:** `brew install --cask android-studio`
- **Others:** download from [developer.android.com/studio](https://developer.android.com/studio)

On first launch Android Studio installs the SDK and points the project at it
(it writes `local.properties` for you).

**Command-line only (no IDE):**

```bash
# macOS
brew install --cask android-commandlinetools

# Install the pieces this project needs, then accept the licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```

Point the build at the SDK by exporting `ANDROID_HOME` (and adding
`platform-tools` to your `PATH`), or by creating a `local.properties` file with:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

> **Gradle itself does not need to be installed.** The committed wrapper
> (`./gradlew`) downloads the correct Gradle version automatically.

### 4. Build and test

```bash
./gradlew assembleDebug      # build both debug APKs
./gradlew testDebugUnitTest  # run the JVM unit tests
```

The debug APKs are written to `wear/build/outputs/apk/debug/wear-debug.apk` and
`mobile/build/outputs/apk/debug/mobile-debug.apk`.

### 5. Run and debug the app

A **physical watch is required** for real work: the emulator has no GPS fix, no
heart-rate sensor and no Bluetooth LE, so nothing this app records can be
exercised on it. The emulator is still useful for layout and navigation.

Add a Wear OS emulator image if you want one:

```bash
sdkmanager "system-images;android-34;android-wear;x86_64"
```

- **Android Studio:** open the project, pick the `wear` run configuration and a
  watch (paired over Wi-Fi debugging or USB) and press **Run**.
- **Command line:** install on a connected watch and launch it:

  ```bash
  ./gradlew :wear:installDebug
  adb shell am start -n com.dchernykh.trainingrecorder/com.dchernykh.trainingrecorder.wear.MainActivity
  ```

  To pair a watch over Wi-Fi: on the watch enable **Developer options ->
  Wireless debugging**, then `adb pair <ip>:<port>` and `adb connect <ip>:<port>`.

### 6. Set up pre-commit hooks (contributors)

Install [pre-commit](https://pre-commit.com/) (`brew install pre-commit`, or
`pipx install pre-commit`), then register the hooks:

```bash
pre-commit install
pre-commit install --hook-type commit-msg
pre-commit install --hook-type pre-push
```

After that the hooks run automatically:

- **on commit** - file formatting, YAML/TOML checks, and a non-ASCII guard;
- **on the commit message** - Conventional Commits validation (commitizen);
- **on push** - `ktlintCheck` and `detekt` (these need the JDK + Android SDK).

To run all checks manually across every file:

```bash
pre-commit run --all-files
```

## Contributing

Before requesting a review, make sure the CI pipeline passes on your pull
request. Once the pipeline is green, request a review from
[@dchernykh1984](https://github.com/dchernykh1984).
