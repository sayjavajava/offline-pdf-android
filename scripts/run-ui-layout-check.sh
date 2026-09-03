#!/usr/bin/env bash
# Invoked as a single `script:` line by .github/workflows/ui-layout-check.yml's
# reactivecircus/android-emulator-runner step.
#
# Unlike run-ui-screenshot.sh next to it, this one wants a real pass/fail:
# OptionRowLayoutTest asserts that every option in a group is actually on
# screen, so the emulator run's own exit code is the answer and there is
# nothing to read back out of logcat.
set -eo pipefail
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offgridpdf.android.ui.OptionRowLayoutTest \
  --stacktrace
