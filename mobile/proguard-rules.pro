# Add project-specific ProGuard/R8 rules here.
#
# R8 is enabled for release builds. Anything reached only by reflection or by the
# platform (entry points named in the manifest are kept automatically) needs an
# explicit keep rule here.
#
# The watch needs a keep rule for protobuf-javalite; this app deliberately does
# not. Its only protobuf is the copy DataStore repackages under its own namespace,
# and the Data Layer's is shaded inside Play Services - both ship the consumer
# rules that keep them working. A rule here matching `com.google.protobuf` would
# match nothing and read like a guard that is not there.
