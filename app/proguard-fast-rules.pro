# The pull-request R8 pass, and why it is a separate file.
#
# A PR builds a signed APK for reviewers, not a release, but it is still a real
# R8 build: skipping the minify task would stop proving that the keep rules
# produce a working APK before merge. This file keeps shrinking, obfuscation and
# resource shrinking on and turns off only the whole-program optimization passes
# (inlining, class merging), which are the expensive half of R8's wall time.
#
# Enabled by -PanisequelFastR8=true, which the release workflow passes for
# pull_request runs only. Shipped builds on main and tags use proguard-rules.pro
# alone, with optimization on. Never add this file to a release pipeline.
-dontoptimize
