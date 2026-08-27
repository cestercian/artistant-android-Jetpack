package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.designsystem.theme.AppRole

/**
 * In-memory [UsersRepository] for tests / previews (the iOS `FakeUsersRepository` twin).
 * Handles in [taken] read as unavailable, every other handle reads available.
 * [selfProfile] is what fetchSelfProfile returns; set [failFetch] or
 * [failAvailability] to exercise the two degrade paths.
 */
class FakeUsersRepository(
    var selfProfile: SelfProfile? = null,
    var failFetch: Boolean = false,
    private val taken: Set<String> = emptySet(),
    /**
     * When true, [handleIsAvailable] answers [HandleAvailability.Failure] — the
     * real impl catches everything into it, and that branch drives the whole
     * degrade contract (Failure → `HandleStatus.Error`, which still counts as
     * available so a blip can't wedge Continue). Without the knob the seam
     * could only ever say Available/Unavailable.
     */
    var failAvailability: Boolean = false,
) : UsersRepository {

    /** Records the last upsert so tests can assert what was written. */
    var lastUpsert: SelfProfile? = null
        private set

    /**
     * The stored `terms_accepted_at`, as the server would hold it.
     *
     * Only ever SET, never cleared — mirroring the real write, which omits the
     * column entirely when there is no consent to assert and therefore cannot
     * erase a stamp an earlier save (or another device) put there. [SelfProfile]
     * carries no consent field, so without this the fake could not tell a test the
     * difference between recording consent and wiping it.
     */
    var termsAcceptedAt: String? = null
        private set

    override suspend fun handleIsAvailable(handle: String): HandleAvailability {
        if (failAvailability) return HandleAvailability.Failure("fake availability failure")
        // Format is deliberately NOT consulted: the RPC is `not exists (select 1
        // from users where handle = lower(trim($1)))`, so a badly formatted
        // handle nobody holds comes back AVAILABLE. Callers gate on format
        // themselves; a fake that answered Unavailable here would teach the
        // opposite of the real contract.
        val h = HandleRules.normalize(handle)
        return if (h in taken) HandleAvailability.Unavailable else HandleAvailability.Available
    }

    override suspend fun fetchSelfProfile(): SelfProfile? {
        if (failFetch) throw AppError.Unknown(RuntimeException("fake fetch failure"))
        return selfProfile
    }

    override suspend fun upsertSelfProfile(
        handle: String,
        fullName: String,
        city: String,
        role: AppRole,
        termsAccepted: Boolean,
    ) {
        if (HandleRules.normalize(handle) in taken) throw AppError.UniqueViolation
        lastUpsert = SelfProfile(
            role = role,
            fullName = fullName,
            city = city,
            handle = HandleRules.normalize(handle),
            artistSetupComplete = null,
        )
        if (termsAccepted) termsAcceptedAt = CONSENT_STAMP
    }

    private companion object {
        /** Stands in for the real `Clock.System.now().toString()` — a fake wants a stable one. */
        const val CONSENT_STAMP = "2026-01-01T00:00:00Z"
    }
}
