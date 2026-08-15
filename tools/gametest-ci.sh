#!/usr/bin/env bash
#
# The in-world gate, bounded, with a thread dump when it does not come back.
#
# Why this exists: run 31861107125 passed. All thirty tests ran in 5.1 seconds,
# the JUnit report was written and uploaded - and then the server logged
# "Saving worlds" and said nothing for twenty-seven minutes, until the job
# timeout cancelled it and the runner reported three orphan java processes.
#
# A hang that produces no output costs a guess per round trip, and the first
# guess was already wrong. `gradlew runGametest` on its own has no way to say
# what it is waiting on, so this wraps it: run it, give it a bound, and if the
# bound passes, dump the stacks of every JVM on the box before killing it. The
# next red run names the blocked thread instead of leaving it to inference.
#
# Deliberately not `timeout(1)`: that kills the process and takes the stacks
# with it, which is the one thing worth keeping.
#
# Used by .github/workflows/verify.yml. Local equivalent is tools/verify.ps1.

set -uo pipefail

cd "$(dirname "$0")/.."

# Comfortably over the 5 seconds the tests actually take, and comfortably under
# the job's own 30-minute timeout, so the dump is uploaded rather than cancelled.
LIMIT="${GAMETEST_LIMIT_SECONDS:-600}"

JSTACK="${JAVA_HOME:-}/bin/jstack"
[ -x "$JSTACK" ] || JSTACK="$(command -v jstack || true)"

./gradlew runGametest --console=plain &
gradle_pid=$!

(
    sleep "$LIMIT"
    echo "::error::runGametest has not returned after ${LIMIT}s - the gate hung."
    if [ -n "$JSTACK" ]; then
        # Every JVM, not just the server: the gradle daemon and the launcher are
        # both in the chain, and which of the three is stuck is the question.
        for pid in $(pgrep java || true); do
            echo "===== jstack $pid ====="
            ps -o args= -p "$pid" 2>/dev/null | cut -c1-200
            "$JSTACK" -l "$pid" 2>&1 || true
        done
    else
        echo "no jstack on PATH and none under JAVA_HOME - no stacks to show."
    fi
    echo "===== killing the run ====="
    kill -TERM "$gradle_pid" 2>/dev/null
    sleep 10
    kill -KILL "$gradle_pid" 2>/dev/null
) &
watchdog=$!

wait "$gradle_pid"
status=$?

kill "$watchdog" 2>/dev/null
wait "$watchdog" 2>/dev/null

exit "$status"
