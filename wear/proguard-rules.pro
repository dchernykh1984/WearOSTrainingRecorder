# Add project-specific ProGuard/R8 rules here.
#
# R8 is enabled for release builds. Anything reached only by reflection or by the
# platform (entry points named in the manifest are kept automatically) needs an
# explicit keep rule here.

# Health Services and the Data Layer speak protobuf, and protobuf-javalite builds
# a message schema at runtime by reflecting on the generated fields *by name* -
# the names are embedded in a string constant the generated class carries. R8
# renames those fields, the lookup then fails, and the failure surfaces far from
# its cause: on this app it was "Field packageName_ for l3.e2 not found", thrown
# the moment a recording tried to start, so the release build showed the sport
# picker and simply refused to record.
#
# CI does run R8 - the Gradle gate assembles the release variant on every push -
# so the gap is not that the minified APK is never built. It is that nothing ever
# *runs* it: the instrumented tests are `connectedDebugAndroidTest`, and a
# release APK that installs and opens looks exactly like one that works. What
# found this was installing the release artifact and starting a ride on it, which
# is worth doing before calling a release good.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
