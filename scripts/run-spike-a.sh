#!/usr/bin/env bash
# Invoked as a single `script:` line by
# .github/workflows/spike-a-page-rendering.yml's
# reactivecircus/android-emulator-runner step.
#
# Real root cause of two earlier failed attempts to retrieve this spike's
# results: that action's `script:` input runs each line of a multi-line
# YAML block as its OWN independent `sh -c` invocation, not as one
# continuous shell session -- so a `set +e` / `GRADLE_EXIT=$?` / `set -e`
# spread across lines had no effect at all, and the action aborted the
# whole step the instant the (expected, real-test-failures) gradlew line
# itself returned non-zero, before any of the result-retrieval logic
# below ever ran. A real script file, invoked as a single line, runs as
# one coherent process where this actually works.
set +e
./gradlew connectedDebugAndroidTest --stacktrace
GRADLE_EXIT=$?
set -e

# Pull the real results regardless of pass/fail -- a failing comparison
# is still real data worth seeing, not just a red X. Internal storage
# (context.filesDir in the test), retrieved via `adb exec-out run-as
# <pkg> cat ...` -- the standard, root-independent way to read a
# debuggable app's private files, since a first attempt found nothing
# retrievable from the external-files path on this system image.
mkdir -p spike-a-results
PKG=com.offgridpdf.android
FILES=$(adb shell run-as "$PKG" ls files/ 2>/dev/null | tr -d '\r' | grep '^spike-a-' || true)
for f in $FILES; do
  adb exec-out run-as "$PKG" cat "files/$f" > "spike-a-results/$f" 2>/dev/null || true
done

echo "=================================================="
echo "Spike A real results (ANDROID_IMPLEMENTATION_PLAN.md)"
echo "=================================================="
shopt -s nullglob
for f in spike-a-results/spike-a-*.txt; do
  echo "--- $(basename "$f") ---"
  cat "$f"
  echo
done
shopt -u nullglob

exit "$GRADLE_EXIT"
