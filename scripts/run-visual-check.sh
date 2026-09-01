#!/usr/bin/env bash
# Invoked as a single `script:` line by
# .github/workflows/visual-check-a22-a25.yml's
# reactivecircus/android-emulator-runner step.
#
# Same real, proven mechanism as scripts/run-spike-a.sh (see its own
# comment for the full story): connectedDebugAndroidTest uninstalls the
# app -- wiping its private storage -- the instant the test run finishes,
# before any later script line can read files back off it. logcat's
# system-wide ring buffer has no such lifecycle tie, so the test logs
# base64-encoded preview images there instead of writing files.
set +e
adb logcat -c
./gradlew connectedDebugAndroidTest --tests "com.offgridpdf.android.spike.VisualCheckSpikeTest" --stacktrace
GRADLE_EXIT=$?

echo "=================================================="
echo "Visual check results (A-22 / A-25 disclosed gaps)"
echo "=================================================="
adb logcat -d -s VisualCheck:I

exit "$GRADLE_EXIT"
