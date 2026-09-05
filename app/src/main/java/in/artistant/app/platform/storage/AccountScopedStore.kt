package `in`.artistant.app.platform.storage

/**
 * A singleton holding state that belongs to the SIGNED-IN ACCOUNT rather than to the device.
 *
 * Every implementation outlives the session — that is what makes it useful, and it is also what
 * makes it a leak: a `@Singleton` still holding the previous account's saved-artist ids, its DPDP
 * export payload or its half-drained upload queue hands all of it to whoever signs in next.
 *
 * **Why an interface and a multibinding rather than three fields.** There were three
 * hand-written teardown lists — `SessionManager.signOut()`, `ProfileViewModel.signOut()` and
 * `DeleteAccountViewModel.deleteAccount()` — and they had already drifted: the two ViewModel
 * backstops wiped the saved ids and the export but not the upload queue, so a sign-out whose
 * network logout threw left the departing artist's staged media resident for the next account to
 * resume against an RLS policy that (rightly) refuses it. A `Set` that Hilt fills means a new
 * account-scoped store is torn down by EXISTING code, and there is one list instead of three.
 *
 * It also breaks a dependency that had no business existing: `SessionManager` imported
 * `feature.profile.DataExportStore` — the auth layer reaching up into a feature package to name
 * one screen's store — which is why the store could not depend on the session in return.
 *
 * Implementations must not throw: [SessionManager.wipeLocalState] runs them one after another
 * on a path where the account may already be erased server-side, and one that throws must not
 * cost the others their turn. It guards each call for that reason; this is the contract that
 * makes the guard a belt rather than the braces.
 */
interface AccountScopedStore {
    /** Sign-out or delete-account: this device knows nothing now. */
    suspend fun reset()
}
