package `in`.artistant.app.data.repository

import `in`.artistant.app.common.util.lowercaseUuid

/**
 * In-memory [SavedArtistsRepository] (iOS `FakeSavedArtistsRepository`). Backed by
 * a set so add/remove are idempotent, matching the real upsert/delete contract.
 */
class FakeSavedArtistsRepository(
    initial: Set<String> = emptySet(),
) : SavedArtistsRepository {
    private val saved = initial.map { it.lowercaseUuid() }.toMutableSet()

    override suspend fun add(artistId: String) { saved.add(artistId.lowercaseUuid()) }
    override suspend fun remove(artistId: String) { saved.remove(artistId.lowercaseUuid()) }
    override suspend fun list(): List<String> = saved.toList()
}
