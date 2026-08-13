# Changelog

## [0.1.1](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.1.0...v0.1.1) (2026-08-13)


### Bug Fixes

* add androidx.test:runner so the instrumentation runner exists at runtime ([d263621](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d26362150af110ee6991ce7e150a0701b986b56d))
* derive versionCode from the release version instead of the workflow run number ([7447153](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7447153d89eb1ad28522ebf32395fa8b63b99d7f))
* drop the watch uses-feature from the companion manifest ([9f66d6c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9f66d6ce780b7c8e891d73f96322738b0b2992cf))
* drop xml from the non-ASCII hook so localized string resources stay possible ([09ee66d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/09ee66d53209cabcd87492318d5bdeb1e5ad70d3))
* emit plain vX.Y.Z release tags so the tag trigger and version strip work ([defa3ca](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/defa3ca6fb241ed75e4f168179b30039c95954c3))
* publish the R8 mapping files so release crash traces stay deobfuscatable ([2a98a99](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2a98a998e44a97e778c83933fa08c0f8893b6e0a))
* read the fallback versionName from the release-please manifest ([9be6dbd](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9be6dbd99436d636fd55ebcc64d1a18cbc88af30))
* sanitize the artifact name so a branch dispatch cannot break staging ([430d978](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/430d978bdbea3bf8e2a3cebcbb5605d5c89d1dba))
* store gradlew.bat with CRLF as its -text attribute and editorconfig require ([6e8b112](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6e8b112114346fed77131c4b38e28186dffd2137))
* widen the versionCode fields so adjacent versions cannot collide ([a8d3b75](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a8d3b759c8be332fa8458fd26ff135e820f61c52))
