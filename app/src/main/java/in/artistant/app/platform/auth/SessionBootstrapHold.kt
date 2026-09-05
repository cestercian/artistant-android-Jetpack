package `in`.artistant.app.platform.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "A session is about to be installed from outside the auth pipeline — decide nothing yet."
 *
 * The one seam the DEBUG fixture harness needs in production code, and it is deliberately
 * tiny: a flag the root gate reads. `HarnessInstaller` hands supabase-kt a synthetic session
 * with `importSession`, but it cannot do that until supabase-kt's own storage restore has
 * settled (an import that beat the restore would be overwritten by it), and the restore is
 * asynchronous. Composition wins that race often enough to matter: the gate saw
 * `NotAuthenticated`, rendered the signup flow, and a `skip-signup-as-*` launch put the auth
 * screen in front of the operator — then behaved on the next try.
 *
 * The harness used to close that gap by BLOCKING `onActivityPreCreated` on the import for up
 * to two seconds. That is the activity-create thread — an ANR budget, not ours to spend — so
 * the wait moved here: the harness raises the flag before it starts, the gate holds
 * `RootGate.Loading` while it is up, and the harness lowers it when the import lands or when
 * its own timeout gives up. Same ordering guarantee, no stall on the main thread.
 *
 * Nothing in `main` ever calls [begin]: in a release build this is a `false` that costs one
 * `StateFlow` and is otherwise inert. It is an object rather than an injected type because
 * the harness runs BEFORE Hilt injects the activity — a bound implementation would not exist
 * yet at the moment the flag has to go up.
 */
object SessionBootstrapHold {

    private val _pending = MutableStateFlow(false)

    /** True while an out-of-band session import is in flight. */
    val pending: StateFlow<Boolean> = _pending

    /** Raise the hold. Idempotent. */
    fun begin() {
        _pending.value = true
    }

    /**
     * Lower the hold — the import landed, or gave up.
     *
     * Must be reached on every path out of the import, including the timeout and a throw,
     * or the app holds the splash for the rest of the process.
     */
    fun end() {
        _pending.value = false
    }
}
