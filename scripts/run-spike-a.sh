#!/usr/bin/env bash
# Invoked as a single `script:` line by
# .github/workflows/spike-a-page-rendering.yml's
# reactivecircus/android-emulator-runner step.
#
# Real root causes found across several earlier CI runs, in order:
# 1. That action's `script:` input runs each line of a multi-line YAML
#    block as its OWN independent `sh -c` invocation, not one continuous
#    shell session -- `set +e`/a variable capture/`set -e` spread across
#    lines had no effect, and the action aborted the whole step the
#    instant the (expected, real-test-failures) gradlew line itself
#    returned non-zero. Fixed by moving everything into this one real
#    script file, invoked as a single line.
# 2. Even once that ran as one process, an app-private-file-based
#    results mechanism (write to context.filesDir, retrieve via
#    `adb exec-out run-as <pkg> cat ...`) reliably produced zero
#    retrievable data: `connectedDebugAndroidTest` uninstalls the app
#    (wiping its private storage with it) immediately after the test run
#    completes, before any post-hoc adb step can read it back --
#    confirmed for real via `run-as: unknown package` in a real CI run.
#    Fixed by switching the test itself to `Log.i("SpikeA", ...)` and
#    reading the results back from `adb logcat -d` below instead, whose
#    system-wide ring buffer has no such lifecycle tie to the package
#    that wrote to it.
set +e
adb logcat -c
./gradlew connectedDebugAndroidTest --stacktrace
GRADLE_EXIT=$?

echo "=================================================="
echo "Spike A real results (ANDROID_IMPLEMENTATION_PLAN.md)"
echo "=================================================="
adb logcat -d -s SpikeA:I

exit "$GRADLE_EXIT"
