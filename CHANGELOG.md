# Changelog

## [0.5.0](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.4.0...v0.5.0) (2026-08-15)


### Features

* **core:** separate the sports worth one tap from the whole catalogue ([9889bc7](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9889bc7c30fcb62523141331dedf89477f3a7b10))
* **mobile:** edit the default layout, and group the sports by discipline ([9701fba](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9701fbacce6dfbd2d4ebae9d1962f10754e943b5))
* **wear:** let a layout change reach the ride already in progress ([2ecbf26](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2ecbf26ff4852623c3bd5c3c7a4ce67f78a0e60c))
* **wear:** let a ride be thrown away without saving it first ([7b82180](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7b8218042dc2fcd773454c8f255fcf4bab53ad74))
* **wear:** pick a sport through its discipline, with favourites on top ([d615c36](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d615c36f0840bb11b0450c12c41a960ea1072e85))
* **wear:** say whether a paired sensor is connected, and hold to forget it ([8c5228b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/8c5228b0cbcc793e7604ca6fe99924485e66954f))


### Bug Fixes

* **core:** let a connected sensor take its fields over from the watch ([d79d2af](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d79d2af9c0fa1fcea4a82368ca02c0cf6321de83))
* **mobile:** keep the app running while the browser holds the Strava sign-in ([3767edf](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3767edfbfb75ce38b2c909ab97114cbb42425323))
* **mobile:** offer Reset only where there is something of its own to drop ([cd5d5a3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/cd5d5a3ea8dde4ddbe9285e8ab516077fe376776))
* stop blanking a heart rate that is merely batched ([7e72100](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7e72100bead90f65426242f7b52097514c170114))
* **wear:** give the discard control a target a thumb can find ([aee27d2](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/aee27d2cb193b1fffc559af0a5a5b458a1902d7b))
* **wear:** let back answer no to a confirmation ([14d85a4](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/14d85a4c186dde5bce5f6181338cb5b1d7fb1d69))
* **wear:** let the controls page scroll if it ever outgrows the face ([e313e44](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e313e445c6bc90dce93cce11364c15170685c807))
* **wear:** pair a sensor with every profile it advertises, not one ([7696b7c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7696b7c22dde29f7e5673b780a05fe7a7cbabfc3))


### Performance Improvements

* **wear:** apply settings off the main thread ([4ac1fef](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/4ac1fefcfa47d05972c3b2b9aced607285eb5484))

## [0.4.0](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.3.3...v0.4.0) (2026-08-14)


### Features

* **mobile:** let the rider read back the secrets they type ([2194a47](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2194a47ee9d3d66d9b699cab6de7ebf53cb98999))
* **mobile:** open the field picker one heading at a time ([79ebba0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/79ebba0fdc746ef08db26ea03008f9e33ce514cf))
* say why a ride is still waiting instead of only that it is ([3e9b2de](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3e9b2de62b28b9d7bfcde74f4b3547686218e54a))


### Bug Fixes

* **core:** age the watch's own readings out the way a strap's already were ([7247609](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/72476098b05a87fa8b13adf0702585467f159e64))
* **core:** bound the failure text a summary carries to the phone ([c1170de](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c1170de3fcd3c24b84b05b04c2e8af19ecc556a3))
* **mobile:** answer every connection the browser opens during Strava sign-in ([ef1dbf1](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ef1dbf19b6619ef930ebe870c0ec2addb7aaaaaa))
* **mobile:** close the redirect listener when the wait for it ends ([64601d4](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/64601d466fdbe8bbe42ac0a9b994414932f0f82e))
* **mobile:** keep the field count level with the buttons that change it ([00d1fcd](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/00d1fcdcffcc8f37987ad7abf65107b2f2ec60cb))
* **mobile:** let the headings outrank the fields under them ([6e04abf](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6e04abfd32ba87dc5eb1ce46a65a1419ae911963))
* **mobile:** measure the space the field count needs instead of guessing it ([aa7478e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/aa7478e245d848fbd6156a4db2093c52fbb791d9))
* **wear:** keep the retry chain from drifting hours between passes ([8b2ac2d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/8b2ac2de06f39d6b6351d2ff9af4dc73116dff3e))
* **wear:** stop declaring the upload drain finished while rides still wait ([60f7ce6](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/60f7ce6a7a682c9cd7dafbbadbb495e63abe4c63))

## [0.3.3](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.3.2...v0.3.3) (2026-08-14)


### Bug Fixes

* **mobile:** keep Reset always present so the banner cannot move anything ([88fac4d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/88fac4d287a68e63fa4e9208b3986c7183b6489c))
* **mobile:** make the refresh slider land on intervals a rider would pick ([7d04655](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7d04655dec123dd50d96fb648168145b2f3f6c94))
* **mobile:** round the slider tick and keep the banner one size ([5b8d728](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5b8d728bcc9ef58ab48a30783b553f099d0227df))
* **mobile:** stop the editor jumping when a sport forks from its parent ([f7f450b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f7f450b65e0c269fc036afaac6677a70ef515b54))
* **wear:** inset each band against the circle so the rim stops eating captions ([ed81e3f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ed81e3f52e81c4fb78dc0ead7d300bf6592418c9))
* **wear:** let the line height follow the size, and hold an empty cell open ([5737e88](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5737e8811fb44cb1841354a627b4105d7199503d))
* **wear:** let the text measure itself instead of guessing from the band height ([52a0979](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/52a097930ac78d2d5bb4f1862dbd709bf4bf2266))
* **wear:** measure the rim against what a band draws, not the cell it was given ([c8bd6c0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c8bd6c02a45acc7d112dd541d898bb8fa2125b66))
* **wear:** size a slot's text to the room it actually gets ([159d7c5](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/159d7c587c12443e00660e012a299bba3d63a849))

## [0.3.2](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.3.1...v0.3.2) (2026-08-14)


### Bug Fixes

* keep the protobuf field names R8 was renaming out from under Health Services ([7b50268](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7b502684d648e185162324bdc3ddba384d9db1bc))
* keep the R8 rules where they are needed and say what CI actually does ([a881787](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a881787f84c7b20626a389b8e862fb3af6efd7f8))

## [0.3.1](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.3.0...v0.3.1) (2026-08-14)


### Bug Fixes

* **localization:** finish the captions that stopped at the word hold ([eadc319](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/eadc31972fd3e1976acfd36676cb114470d23a5a))
* **wear:** cap the controls row instead of only appearing to ([a5f09d1](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a5f09d1f175fc5a13380892bed09052dbab2e5aa))
* **wear:** centre the sport labels so a round screen cannot clip them ([5a38227](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5a382274f378d537be1a09f978979a9b07263ff1))
* **wear:** darken the go disc until its icon actually contrasts ([c651d33](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c651d330f74c51d6df95ddc1cc6f414b812a5dd1))
* **wear:** let the controls fit a small round watch ([71c64e8](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/71c64e8016ff3ea12680caab449e87393b20ada0))
* **wear:** make the recording controls the size and colour a wrist needs ([d3a750a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d3a750a49d47e2abedb6cdafe425e4160bea0d11))
* **wear:** say that discarding a start needs a hold ([78fe103](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/78fe1035b79f0d7a751f82f5b70dbe4bdad94608))
* **wear:** stop the screen reader announcing every control twice ([d3a5dc6](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d3a5dc653e458b49ef1137978e2758cc1d9f7eeb))

## [0.3.0](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.2.0...v0.3.0) (2026-08-14)


### Features

* **core:** add an append-only track journal a killed process cannot lose ([073191c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/073191c9d5494d1d7cc6e2b8fa4f896670afc78d))
* **core:** add the watch-to-phone path finished workouts travel on ([442259f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/442259f94d7b0caf03b63b47db50e153b526d91c))
* **mobile:** let the rider choose metric or imperial for every field at once ([85adfab](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/85adfaba5adc49c025af5edd4dc20185d0326598))
* **mobile:** receive the watch's workouts so the history stops being empty ([908da3f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/908da3f78402b7957c14ef6ae73021dc781ff57b))
* **mobile:** sign in to Garmin with the rider's own login and password ([279721c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/279721cd2e5dafe1b70ecace14f7efbcf9f4e23a))
* port the Garmin sign-in so the watch uploads with a token it can renew ([d827207](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d827207c3846f0a92fa31d50f0183c549225b386))
* **wear:** journal the track as it is ridden and recover an interrupted one ([1bcd406](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1bcd40642f4d7201e1c1fc920d51cdb5e959d10a))
* **wear:** publish finished workouts so the phone has a history to show ([9eea119](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9eea11936a2c5ebf377a8140fb6b61b780819f46))


### Bug Fixes

* **core:** drop a journal line cut off inside its last column ([4b93274](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/4b93274793f8008870ece85d097c647d286f9745))
* **core:** keep the header Garmin was recorded as refusing an upload without ([279e415](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/279e415d94da69bced7352c2cb08a6cc207ed661))
* **core:** make the journal version guard say and do the same thing ([2b4f174](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2b4f1745d37632b82cbb08e8ee2138f8f94f1c82))
* **localization:** stop promising an encryption the credentials do not have ([83051b4](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/83051b41d325dbfeb23598d1bcd502da0d9ed972))
* **mobile:** clear a stale code prompt when a new Garmin sign-in starts ([1106762](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1106762674a4df0489727a95f456630735c89508))
* **mobile:** keep the code field up when Garmin rejects a code ([8f45e48](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/8f45e4858b51a2a2ce0c846e197d83ceaf02c068))
* **mobile:** keep the history looking for new rides while it is open ([9df7f44](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9df7f448ec1bc2a5401cc292230a75b6ae5df5f8))
* **mobile:** send the sign-in session only to the host that issued it ([eb5ef71](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/eb5ef7108c0bae399f81dccf3d8a0fcdfa7c8076))
* **mobile:** stop polling behind a phone in a pocket, and read files off the main thread ([e5be600](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e5be60003eb566236c103c754486a9a346615c74))
* **mobile:** stop two writers of the workout history sharing one temporary file ([616e0b9](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/616e0b964865d54d4109938bd3f6c92272216302))
* **mobile:** survive a deleted data item instead of crashing the phone ([538aeaf](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/538aeafe5131fe9746cf779e32fd1b57d2d9d7ee))
* **wear:** claim an interrupted ride's journal before a new ride can overwrite it ([07ea7d4](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/07ea7d4e9dce73a5550a9e0de3a2c3d1c23c1e2d))
* **wear:** drop a claimed journal that cannot become a ride ([56db38d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/56db38dd1675174de0c15824a865e72b7ad2424f))
* **wear:** finish the whole save even when the rider swipes the app away ([ca2e2a7](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ca2e2a78bd650d1a4bfe518dabce2b0f809444ca))
* **wear:** keep the whole save inside one uncancellable block ([5748b1a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5748b1a142ad81b4dfa005bfe7b82898cdd74e8f))
* **wear:** keep what a re-saved workout already knows about its uploads ([f8f4e77](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f8f4e771afb507574f422faef7fa2d59d697d166))
* **wear:** leave no ride owning the journal when opening it fails ([df81e2c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/df81e2c3743f60d218004992d839d3481a213fd9))
* **wear:** let only the ride that opened a journal close it ([3fce18b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3fce18b0875978f9448b6568cbcc987cf11d5fbe))
* **wear:** make saving a workout idempotent by id ([01b8cfc](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/01b8cfcc5e7af2ffb1f701d04381731cc5361467))
* **wear:** never let a stuck claim cost the ride that came after it ([b75b1d4](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/b75b1d42c3cdcc1372e6f96ef8f2a1ca06e0aaf9))
* **wear:** renew a refused Garmin token instead of retrying against it ([bf6eb1c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/bf6eb1c9a5e073cba61cad33283576ba096395fd))
* **wear:** run every journal operation in the order it was asked for ([97c5437](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/97c543794e5573c000ee6a0c427834a350079bcd))

## [0.2.0](https://github.com/dchernykh1984/WearOSTrainingRecorder/compare/v0.1.1...v0.2.0) (2026-08-14)


### Features

* add the launcher icon from the Amazfit race stats app ([06d3386](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/06d33861add831b0fbc9fc46d2e99ff32e16fb8f))
* carry the phone settings to the watch over the Data Layer ([d7c3233](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d7c32336ee61112bf492dc8c709a705134d00d34))
* **core:** add round band and square grid layout planning ([65008a5](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/65008a54914cfacbad2feb64746ed6c9574c33ad))
* **core:** add the data field catalogue grouped by category ([4478228](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/4478228809cb31b502a5fa02b458d99970f01aa3))
* **core:** add the Garmin Connect sign-in and upload protocol ([0fd64e1](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/0fd64e1a4fddcbe1268805593b048c2cc8c18543))
* **core:** add the phone-to-watch settings contract with forward-compatible decoding ([a3f3e42](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a3f3e4267dd08a964cdef16e2d9ffcfdf3833717))
* **core:** add the pluggable storage connector interface and registry ([0645143](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/0645143af11adb4601a89c1a7b2991b053117ec8))
* **core:** add the race stats contract, parser and formatter ([769429a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/769429a035385abfde6da253ce1c0ddc166e21e7))
* **core:** add the recording state machine with separate elapsed and moving time ([1692ae0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1692ae048356db1b2347011f8285e67af7ce6be7))
* **core:** add the sport discipline and sub-type taxonomy ([e38e8a2](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e38e8a2f5d34e3ddab221b6ec59330cfae94854a))
* **core:** add the Strava protocol with per-user credentials and loopback auth ([42d3d51](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/42d3d51ecd4631ff4525ccfa64440814ff110bbd))
* **core:** add the three-level screen configuration with copy-on-write forking ([59b2754](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/59b2754a8c09eea49beb8509dffc79a5322b0164))
* **core:** add the upload queue with exponential backoff and retry-after support ([807b022](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/807b02252da4c925322506b1d11b303d19d783a3))
* **core:** add the workout summary model and retention policy ([1ccc7fe](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1ccc7fee56041a16993b077d544de2662c6066d9))
* **core:** carry service credentials on their own sync path ([5bc460b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5bc460b4a7c5af7131e5645f5b357822e6b8299c))
* **core:** encode finished workouts as FIT with the official Garmin SDK ([d445e75](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d445e75b2907c8b50b49bf5e0435ce379f82276f))
* **core:** exchange a strava authorization code for a token ([3faf313](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3faf313267b66de468c609d7b9a438f04d748e5a))
* **core:** format field values for display with metric and imperial units ([240cead](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/240cead59b99cccd46025e73ab14c10b0281f417))
* **core:** format swimming pace per 100 m and sub-unit ratios ([0d134ec](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/0d134ecfe0a5e4020e61dd4ebea6d64639a65a6c))
* **core:** merge external and built-in sensor readings with staleness fallback ([700411a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/700411a91441e27c53b07e8ad19b91361feafefb))
* **core:** name the credential keys once and refresh strava tokens ([f4ec463](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f4ec4638146a2be1e06530f9ecbb6418a099b004))
* **core:** order the sport picker by most recent use ([dd44c9b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/dd44c9bb1f60b6c53b4da0de1b3f5f149a9ca1e1))
* **core:** parse the Bluetooth LE fitness characteristics with rollover-safe rates ([591e9c2](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/591e9c202be166b89b3c0c69c8faed28b99bb42a))
* **core:** render every catalogue field from one snapshot ([776886e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/776886ed83636432ace8acbd84d236c38e719490))
* **localization:** add Chinese, Japanese, Turkish and Hindi to reach fifteen languages ([2caf1ac](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2caf1ac25d3689e7ac8dce2182dbe8eb61719071))
* **localization:** add Russian and Kazakh, and race stats labels for eight more languages ([5b29825](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5b29825dfb0a1b6d287c018d72e92ce2f269844c))
* **localization:** add the language catalogue and the switch ([41f6483](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/41f6483e7b0aa36b1b388d042edf7c936b6ae767))
* **localization:** complete the German, French, Italian, Spanish, Portuguese, Dutch, Polish and Czech locales ([4d846ad](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/4d846ad6a3336f812ec6650f882d388776268fd1))
* **localization:** name the connection outcomes ([4689f99](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/4689f99717b2e24a8822c2efbef7328ddb9307e0))
* **localization:** name the sensor pairing controls ([9a09573](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9a09573a485422db2ff499af7b8d34bef520154b))
* **mobile:** add the companion app with the sport configuration list ([6b60786](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6b6078601c2c35e0e6137fc83e6c9b89144cd30c))
* **mobile:** add the connection setup screen for per-user credentials ([eee4674](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/eee46748ff74c7158743e30117f9abbf7fa90786))
* **mobile:** add the language picker ([63ed019](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/63ed01925fb86c5b5b7c0a4cccf6c0b8831d66f2))
* **mobile:** add the race stats settings screen ([bf94ba0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/bf94ba083484fc1c795fb867bcc3a7b6e277d316))
* **mobile:** add the screen editor with an explicit inheritance banner ([9d019fe](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9d019fe86d9a3ff83157d4a514e6367c621bd6a5))
* **mobile:** add the workout history with its upload state ([ab5bbda](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ab5bbdae146dcbb346bfff876751445e1f68d68e))
* **mobile:** authorize strava through a loopback redirect ([c87f1fa](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c87f1fa0fc058ee12a2fed980644c441db964a56))
* **mobile:** connect a service and report how it went ([ae5aa0c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ae5aa0c1bbf541df249e0544f3bc4e26be80bc13))
* **mobile:** navigate between every companion screen ([00d1de5](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/00d1de5971460974464e5b1688b676957607b566))
* **mobile:** publish service credentials to the watch ([23190c5](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/23190c573b8c8c54e7e0239b0a31d533d32c37d3))
* **mobile:** remember the configuration between launches ([33593ec](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/33593ecad6d7ac6c21b7d9adc79d33fba1e847b8))
* **wear:** add Compose for Wear and a sport picker ordered by recent use ([7b2ef10](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7b2ef10740f0f88d941cadbb442e80af32d1ce7c))
* **wear:** add the sensor pairing screen ([74d76f3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/74d76f38fe767aa8af0e81cb698e669944a98ba8))
* **wear:** add the swipeable recording screens with icon controls ([aa0613f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/aa0613fdcf334de80b10b62916a7626db644224f))
* **wear:** connect paired Bluetooth sensors and stream their readings ([a60dff5](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a60dff5b57ced817b213f53721a6b29b85937249))
* **wear:** keep recordings alive in a foreground service with an ongoing activity ([7bcda0b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7bcda0bb30087663df710dafe12007d9f3ffe6e7))
* **wear:** poll the timing server while a race is being recorded ([d8f1060](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d8f1060d9d49e190f2b68fb189918a8cb2f84cbc))
* **wear:** read speed from a wheel-only cadence sensor ([e7b804a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e7b804a91e63be144b490da4ffbcdcb50029825c))
* **wear:** record from external sensors alongside the watch ([6ce7e73](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6ce7e7303e65a600a01a7eec17706d47e6605447))
* **wear:** record workouts through Health Services ([f48dd58](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f48dd58031a7208f8042ef5707f09cb9d227ee84))
* **wear:** remember paired sensors and merge what they report ([eb4afa3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/eb4afa373063cce9eafa54cec37ebf43c4e4b451))
* **wear:** scan for nearby fitness sensors by profile ([37dd15e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/37dd15e51f0f9a1d211fdcd6dfbebacf46016f06))
* **wear:** store finished workouts as FIT files with an atomic index ([6394740](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/639474016fe0d2b526d243c930ccc55b7e404628))
* **wear:** upload finished workouts to Strava and Garmin over multipart ([1b8087e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1b8087e152692c862f36bb1bfff93c0467f2d48c))
* **wear:** upload finished workouts when the watch has a network ([a483ef3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/a483ef32e7bdfa209d1aba3d62e2034053bb415d))
* **wear:** wire the app to a recording view model so a workout actually records ([fdcc75c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/fdcc75c382decaa22b6f325c3883a8e74bdfcf02))


### Bug Fixes

* **core:** encode rider-typed race parameters into the request path ([68536ad](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/68536adf5bba26537bba67691f24e22520d4c937))
* **core:** evict strictly oldest-first and never the workout just recorded ([c09f178](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c09f17859bb2a0c9e09a90f0c31037f78e10db67))
* **core:** freeze elapsed time when a recording finishes ([26fe780](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/26fe780265aa28ca1d1f6173194324cc91f1e236))
* **core:** measure the upload backoff from the last attempt ([9517be3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/9517be3401e3210a8dc828f195102a4ef698568c))
* **core:** re-serve the backoff when the queue is rebuilt after a restart ([54b999c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/54b999c7a28cdad6a638bc5d834f5cd963b2ec46))
* **core:** record an exhausted upload as failed so retention can reclaim it ([3988b16](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3988b1621a698078697dbb4a8c7a48979fa76199))
* **core:** round before choosing the unit so 999.6 m reads as 1 km ([141915f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/141915f461ea26c609bc00cd0a396659eae0c166))
* **core:** show short imperial distances in feet instead of a fraction of a mile ([d38433b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d38433b7f57ebf3f50b585f3681b4414f93b2d90))
* **core:** show short imperial distances in feet instead of a fraction of a mile ([d53e60e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d53e60efacc6ad56b9f097400a83272527eadd97))
* **core:** treat a refused upload as terminal so the ride is not stranded forever ([c8d731f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c8d731f982a7bfae2462ec2ba0778331f6abbe93))
* declare the coarse location permission alongside the fine one ([46aa729](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/46aa72982253e7e3ef7e7dba973a6bdce975bb30))
* drop the AppCompat tint attribute the watch theme does not define ([59a4e0f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/59a4e0f1343be6ef7bee6d450a33fa2eccf03792))
* give the sensor reading map an explicit type so inference holds ([b055115](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/b05511577c2a25f50b235480b89c452fb2b9a9f0))
* **localization:** apply the chosen language below api 33 ([03c172a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/03c172af4506e0abf51b8d58b10af517d7325f15))
* **localization:** escape the apostrophes aapt2 rejects ([5a62b31](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/5a62b31f01601b4280bdcfba921bb9c97c0be10f))
* mark the percent-sign labels as non-format strings ([be79df0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/be79df05bde2058c3365053ec26d4e03e6b497bf))
* **mobile:** declare the internet permission the authorization needs ([b3189fd](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/b3189fd131bf36ffdf4a5ee5e974edfe023e11d3))
* **mobile:** keep the refresh token and use the shared key names ([e695ca1](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e695ca10cf18a61fc9785515cf2dc546e606ab0b))
* **mobile:** save off the main thread and survive rotation ([65d4867](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/65d48670640dd171139a0775951bc6eb8cb84e05))
* **mobile:** survive a browser preconnect and a stalled token request ([f0ae292](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f0ae29288fd0af914121f5ad04231caa6cf22ba2))
* persist the upload attempt count so a restart resumes the backoff ([02fddf3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/02fddf3b99033445c366b88ea9829a26d4d40dfb))
* restore the core-ktx dependency the service compat helpers need ([6fe4827](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6fe48275841f920e45918c45684d8ea4e318188a))
* **wear:** also request the Wear OS 6 heart rate permission ([2cb157a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2cb157a06a840c08b361b96a6560f96b3de65af6))
* **wear:** bound the token request like every other one ([76deb27](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/76deb27987b92c88d64b85761d909b6e6a3341c1))
* **wear:** build the workout where a bad clock cannot crash the save ([d6c4fd8](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d6c4fd8eede9e2c5668ec38565c50774b44e4cee))
* **wear:** clear credentials the phone has revoked ([23164e3](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/23164e36f2602356e9729486ed78d38e79dce400))
* **wear:** clear the crank counters when a collection starts and ends ([7d3582c](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/7d3582c57f154fadf8651259b17fbde5a8d814f0))
* **wear:** complete the per-collection crank baseline refactor ([900c00b](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/900c00bd4a9fde73b22b5dd376aad3bd76738763))
* **wear:** declare the legacy Bluetooth permissions Wear OS 3 still checks ([55f8b20](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/55f8b2048d066c793e1b3cbbbdda02e170d896bd))
* **wear:** degrade to a bare session when Health Services lacks the exercise type ([f163941](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f1639418293f28caac2b15a28b5da6c8b971bba6))
* **wear:** deliver the credentials the phone publishes ([d5aa4dd](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d5aa4ddc1bd0f1b1fdce8b110a0304956c4781e0))
* **wear:** drop the fallback service type that was itself a crash ([52f377e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/52f377e91e70cd2a4b45626bdea0334cd32b41df))
* **wear:** fail loudly on a corrupt index rather than orphaning every workout ([6b46d7d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/6b46d7de0139ca4a4c8042d03ba6a47b534f4e3a))
* **wear:** fall back to a generic exercise type the config will accept ([e0ba27a](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/e0ba27a5b155d8bda4ca97bde4396c1a5b85cdc2))
* **wear:** import Part from its own package ([3dbb6fd](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3dbb6fd43f71444d328c32fbc6ef82a9ed5e8ae4))
* **wear:** keep the recording screens live and the transitions safe ([2aa632f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/2aa632fe2b9f2bbac261ab11a1953aa6bf129715))
* **wear:** keep the ride when the app is dismissed mid-finish ([1c74f5e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1c74f5ec470d28a9e291ff08854fc79a967660d2))
* **wear:** open on the data screens and keep the chosen one across the controls ([1787e8d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/1787e8d2a30c991603228cb9fcfa6b120753618a))
* **wear:** pull the settings a fresh install never heard change ([c220fae](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c220fae73e5ae315c21fdeacfe13b985157d7534))
* **wear:** put the controls right of the data screens, clear of the dismiss gesture ([c837c27](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c837c27155249c548378d98c1765777f26420886))
* **wear:** record when each upload was last attempted ([f04bd0e](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/f04bd0e69276761d0b4c15d4010261b3ead40735))
* **wear:** refresh an expired strava token before uploading ([b11ed76](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/b11ed76ac3fba8106e2c63d232cdb0dfb527bd41))
* **wear:** refuse a profile with no measurement characteristic instead of reading the wrong one ([ec7d049](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/ec7d04912305bf0ec54a70feda68261182e7b2f6))
* **wear:** request coarse location alongside fine or the platform ignores both ([accc9ef](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/accc9efd47c38508a725f788ac974031c0bd0da2))
* **wear:** request the runtime permissions recording actually needs ([8264345](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/82643459ae8ed2220474a14c97f6f840b8169894))
* **wear:** reread the tokens each attempt and name the ride properly ([408bb2d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/408bb2d31d701ed9d2aa7b8a54d769022353fa9d))
* **wear:** reset the crank counters on disconnect so reconnecting cannot spike cadence ([89c6f7f](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/89c6f7fd406876f77209a005fafad8d9fc12af87))
* **wear:** rewrite the index before deleting files and serialise every update ([26f49a8](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/26f49a8d938aae55af8da958c39ffcd0226b7e51))
* **wear:** satisfy the exercise config rules for pool swims and GPS-less sports ([3039d95](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/3039d95fdbe0766d9d96f9c1b55eb84f3fbf75ec))
* **wear:** scope the crank baseline to its collection ([0c99085](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/0c9908527a60cea134c34992275a69e5a56d478a))
* **wear:** scope the GATT connection to its collection so it cannot be leaked ([d969506](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d969506c65a158f907879d251194c6896c1c4023))
* **wear:** share the index lock across repositories and refuse a damaged entry ([c20963d](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/c20963def6e024ac33bfc39a9535b4fa251da545))
* **wear:** start a plain foreground service when no sensor permission was granted ([d0a8f37](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d0a8f37a75acd51d54849611ff246efa58ce18eb))
* **wear:** start each ride from zero and refuse impossible transitions ([d775be1](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/d775be14cc5da7b3bc3bf4efac1ff3b979d07674))
* **wear:** stop asking for BODY_SENSORS on Wear OS 6 where it no longer exists ([29d5838](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/29d583810a205a424e08ad4e3134695379bdf38a))
* **wear:** survive a refused or unavailable bluetooth stack ([eb529c0](https://github.com/dchernykh1984/WearOSTrainingRecorder/commit/eb529c027a60c405959c393db740a577a627247c))

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
