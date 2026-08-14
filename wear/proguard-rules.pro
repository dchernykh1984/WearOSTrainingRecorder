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
# picker and simply refused to record. Nothing catches this before a device -
# every check in CI runs on a debug build, where R8 is off.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}
