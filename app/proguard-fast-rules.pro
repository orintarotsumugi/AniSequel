# The throwaway R8 pass, and why it is a separate file.
#
# A build with this file signs an APK that only has to prove the keep rules
# produce a working APK - not a release. It keeps shrinking, obfuscation and
# resource shrinking on and turns off only the whole-program optimization passes
# (inlining, class merging), which are the expensive half of R8's wall time.
#
# Enabled by `-PanisequelFastR8=true`, read in app/build.gradle.kts.
#
# **CI does not pass it any more.** The release workflow builds the `debug` variant
# for pull requests, so this path is reachable only from a local
# `./gradlew assembleRelease -PanisequelFastR8=true`. The comment here used to say
# the workflow passed it for pull requests, which stopped being true when that
# step moved to `debug` - and a comment describing a caller that no longer exists
# is how the next reader concludes the release build is exercised on every PR when
# it is not. Never add this file to a release pipeline, and never ship its output.
-dontoptimize
