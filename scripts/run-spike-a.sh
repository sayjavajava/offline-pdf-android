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
# set +e for the whole rest of this script, not just the gradlew line:
# everything below is best-effort diagnostics-and-retrieval that must
# keep running (and printing) even when an individual step fails, so a
# real error from any one command is visible instead of silently
# aborting the script before later, still-useful commands run.
set +e
./gradlew connectedDebugAndroidTest --stacktrace
GRADLE_EXIT=$?

# Pull the real results regardless of pass/fail -- a failing comparison
# is still real data worth seeing, not just a red X. Internal storage
# (context.filesDir in the test), retrieved via `adb exec-out run-as
# <pkg> cat ...` -- the standard, root-independent way to read a
# debuggable app's private files, since two earlier attempts found
# nothing retrievable (first from an external-files path, then from this
# same run-as approach whose stderr was being swallowed, hiding whatever
# it was actually saying). stderr is deliberately NOT suppressed below --
# whatever run-as/ls/adb really say gets printed and this round's job log
# is the last resort for finding out why, rather than guessing a fourth
# time.
mkdir -p spike-a-results
PKG=com.offgridpdf.android
echo "--- diagnostic: adb devices ---"
adb devices
echo "--- diagnostic: run-as ls files/ (real stdout+stderr) ---"
adb shell run-as "$PKG" ls -la files/
echo "--- diagnostic: run-as id / whoami ---"
adb shell run-as "$PKG" sh -c 'id; pwd'
FILES=$(adb shell run-as "$PKG" ls files/ 2>&1 | tr -d '\r' | grep '^spike-a-')
echo "--- diagnostic: matched filenames: [$FILES] ---"
for f in $FILES; do
  echo "--- pulling $f ---"
  adb exec-out run-as "$PKG" cat "files/$f" > "spike-a-results/$f"
  ls -la "spike-a-results/$f"
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
