package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import java.time.Instant
import java.util.UUID

/**
 * In-memory [RequestsRepository] for tests / previews. [failNotFound] forces the
 * accept/decline/counter writes to throw [AppError.NotFoundOrUnauthorized] so the
 * store's optimistic-rollback path can be exercised deterministically.
 */
class FakeRequestsRepository(
    seed: List<StoredRequest> = emptyList(),
    var failNotFound: Boolean = false,
) : RequestsRepository {

    val requests: MutableList<StoredRequest> = seed.toMutableList()

    override suspend fun listForArtist(): List<StoredRequest> = requests.toList()
    override suspend fun listForClient(): List<StoredRequest> = requests.toList()

    override suspend fun create(
        artistId: String,
        proposedAmountInr: Int,
        dateLabel: String,
        message: String?,
        venue: String?,
        crowdSize: Int?,
        expiresAt: Instant,
    ): StoredRequest {
        val req = StoredRequest(
            raw = GigRequest(
                id = UUID.randomUUID().toString(),
                client = "You",
                message = message ?: "",
                date = dateLabel,
                amount = proposedAmountInr,
                `package` = "Custom",
                timeAgo = "now",
            ),
            status = GigRequestStatus.Open,
        )
        requests.add(0, req)
        return req
    }

    override suspend fun accept(id: String) = mutate(id, GigRequestStatus.Accepted, null)
    override suspend fun decline(id: String) = mutate(id, GigRequestStatus.Declined, null)
    override suspend fun counter(id: String, amount: Int) = mutate(id, GigRequestStatus.Countered, amount)

    private fun mutate(id: String, status: GigRequestStatus, counter: Int?) {
        if (failNotFound) throw AppError.NotFoundOrUnauthorized
        val idx = requests.indexOfFirst { it.id == id }
        if (idx >= 0) requests[idx] = requests[idx].copy(status = status, counterAmount = counter)
    }
}
