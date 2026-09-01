#!/usr/bin/env bash
# Invoked as a single `script:` line by
# .github/workflows/ui-screenshot-check.yml's
# reactivecircus/android-emulator-runner step.
#
# Same real, proven mechanism as scripts/run-spike-a.sh and
# scripts/run-visual-check.sh: connectedDebugAndroidTest uninstalls the
# app -- wiping its private storage -- the instant the test run finishes,
# before any later script line can read files back off it. logcat's
# system-wide ring buffer has no such lifecycle tie, so the test logs a
# base64-encoded screenshot there instead of writing a file.
set +e
adb logcat -c
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.offgridpdf.android.spike.UiScreenshotSpikeTest --stacktrace
GRADLE_EXIT=$?

echo "=================================================="
echo "UI screenshot results"
echo "=================================================="
adb logcat -d -s UiScreenshot:I

exit "$GRADLE_EXIT"
