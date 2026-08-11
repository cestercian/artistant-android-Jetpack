package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

sealed class BlockRepositoryError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    data object NotSignedIn : BlockRepositoryError("Sign in to block someone.")
    class Underlying(cause: Throwable) :
        BlockRepositoryError(cause.message ?: "Block request failed", cause)
}

/**
 * `public.blocked_users` — the signed-in user's own block list (migration 0087).
 *
 * **What blocking does in v1, exactly.** 0087 ships the table and own-rows RLS;
 * the CLIENT enforces the block by filtering the blocked person's conversations
 * out of the inbox. Server-side contact PREVENTION — rejecting a blocked user's
 * INSERT into your thread — was deliberately deferred rather than amending the
 * hot message-insert policy in that pass. So a block is "I don't want to see
 * this", not "you cannot reach me", and no copy in this app may say otherwise.
 * The upgrade is additive: when the insert policy learns about this table, the
 * same rows start preventing contact and nothing here has to change.
 *
 * **Own-rows only.** Every method operates on the signed-in user's blocks, and
 * there is deliberately no way to ask who has blocked YOU — 0087 exposes no
 * policy for `blocked_id = auth.uid()`, because that would leak the block back
 * to the blocked person.
 *
 * **Signed-in only.** Blocking is a per-account fact, so [listBlocked] throws
 * rather than returning an empty set when signed out. The distinction matters:
 * callers keep their last known set on failure, and an empty list returned as if
 * it were authoritative would silently un-hide every blocked conversation.
 */
interface BlockRepository {
    suspend fun block(userId: String)
    suspend fun unblock(userId: String)

    /** User ids this viewer has blocked, lowercased. Throws when signed out. */
    suspend fun listBlocked(): Set<String>
}

@Singleton
class SupabaseBlockRepository @Inject constructor(
    private val client: SupabaseClient,
) : BlockRepository {

    /**
     * Upsert on the `(blocker_id, blocked_id)` primary key so blocking someone
     * twice — two devices, or a retry after a timeout that actually landed — is
     * a no-op rather than a unique violation the UI would have to explain.
     *
     * `blocker_id` is deliberately absent from the payload: 0087 defaults it to
     * `auth.uid()`, so the row is stamped server-side and this client has no
     * shape in which it could file a block on somebody else's behalf.
     */
    override suspend fun block(userId: String) {
        currentUserId() ?: throw BlockRepositoryError.NotSignedIn
        try {
            client.from("blocked_users").upsert(BlockInsert(blockedId = userId.lowercase())) {
                onConflict = "blocker_id,blocked_id"
            }
        } catch (t: Throwable) {
            throw BlockRepositoryError.Underlying(t)
        }
    }

    /**
     * The `blocker_id` filter is redundant against the delete RLS, which already
     * scopes to `auth.uid()`. It is here anyway because a DELETE whose predicate
     * is wrong deletes more than it should, and the cost of proving otherwise is
     * one extra `eq`.
     */
    override suspend fun unblock(userId: String) {
        val uid = currentUserId() ?: throw BlockRepositoryError.NotSignedIn
        try {
            client.from("blocked_users").delete {
                filter {
                    eq("blocker_id", uid)
                    eq("blocked_id", userId.lowercase())
                }
            }
        } catch (t: Throwable) {
            throw BlockRepositoryError.Underlying(t)
        }
    }

    override suspend fun listBlocked(): Set<String> {
        val uid = currentUserId() ?: throw BlockRepositoryError.NotSignedIn
        return try {
            client.from("blocked_users")
                .select(Columns.list("blocked_id")) { filter { eq("blocker_id", uid) } }
                .decodeList<BlockedIdRow>()
                .map { it.blockedId.lowercase() }
                .toSet()
        } catch (t: Throwable) {
            throw BlockRepositoryError.Underlying(t)
        }
    }

    private fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id?.lowercase()
}

/** Only `blocked_id`; `blocker_id` and `created_at` are server-defaulted (0087). */
@Serializable
private data class BlockInsert(@SerialName("blocked_id") val blockedId: String)

@Serializable
private data class BlockedIdRow(@SerialName("blocked_id") val blockedId: String)

/** In-memory twin for ViewModel and store tests. */
class FakeBlockRepository(
    initial: Set<String> = emptySet(),
) : BlockRepository {
    private val ids = initial.mapTo(linkedSetOf()) { it.lowercase() }

    var signedIn: Boolean = true

    /** Flip to make the next write fail, so a caller's revert path can be tested. */
    var failWrites: Boolean = false

    /** Every id passed to [block], in order — proves the write reached the seam. */
    val blockCalls = mutableListOf<String>()
    val unblockCalls = mutableListOf<String>()

    override suspend fun block(userId: String) {
        if (!signedIn) throw BlockRepositoryError.NotSignedIn
        blockCalls += userId.lowercase()
        if (failWrites) throw BlockRepositoryError.Underlying(IllegalStateException("network down"))
        ids += userId.lowercase()
    }

    override suspend fun unblock(userId: String) {
        if (!signedIn) throw BlockRepositoryError.NotSignedIn
        unblockCalls += userId.lowercase()
        if (failWrites) throw BlockRepositoryError.Underlying(IllegalStateException("network down"))
        ids -= userId.lowercase()
    }

    override suspend fun listBlocked(): Set<String> {
        if (!signedIn) throw BlockRepositoryError.NotSignedIn
        return ids.toSet()
    }
}
