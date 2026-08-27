package `in`.artistant.app.harness

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaAspect
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.data.repository.ArtistMediaKind
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.FakeAccountRepository
import `in`.artistant.app.data.repository.FakeArtistLinksRepository
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.data.repository.FakePackagesRepository
import `in`.artistant.app.data.repository.BlockRepository
import `in`.artistant.app.data.repository.FakeBlockRepository
import `in`.artistant.app.data.repository.FakeReportsRepository
import `in`.artistant.app.data.repository.FakeReviewsRepository
import `in`.artistant.app.data.repository.FakeRequestsRepository
import `in`.artistant.app.data.repository.FakeSavedArtistsRepository
import `in`.artistant.app.data.repository.FakeScoreRepository
import `in`.artistant.app.data.repository.FakeSearchRepository
import `in`.artistant.app.data.repository.FakeTechRiderRepository
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.SearchRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Registry of the seeded in-memory repositories the DEBUG harness swaps in.
 *
 * Each accessor returns null when the harness is off — or when no fake has been written for
 * that seam yet — and the debug `RepositoryModule` falls back to the real Supabase
 * implementation. Two things follow from that shape:
 *
 *  1. A plain debug install with no `-e uitest` extra behaves EXACTLY as before. The fakes
 *     only ever appear for a process that was launched asking for them.
 *  2. Growing the harness is purely additive here and never touches the DI module.
 *
 * Instances are `by lazy` so a fake is only built when something asks for it, and so each is
 * a process-wide singleton — mutable fixture state (an accepted booking, a sent message) has
 * to survive navigation the way the real repositories do.
 *
 * Most of the fakes already existed in `main` for unit tests; the harness's contribution is
 * the seed data and the wiring, not another parallel set of test doubles.
 */
object HarnessRepositories {

    private val on: Boolean get() = HarnessState.useFakes

    /**
     * Several of the existing fakes only accept a seed through their `suspend` write methods
     * (`replaceAll`, `upsert`) rather than a constructor. Those methods are pure in-memory map
     * writes with no suspension point, so `runBlocking` here completes immediately and cannot
     * deadlock — it is bridging a `suspend` signature, not waiting on I/O. Doing it inside the
     * `lazy` block keeps each fake fully seeded before the first caller ever sees it.
     */
    private fun seedBlocking(block: suspend () -> Unit) = runBlocking { block() }

    /**
     * The roster with its generated cover art attached — the list every
     * artist-shaped fake is seeded from.
     *
     * Applied here rather than baked into [HarnessFixtures] so that file stays a
     * pure, Android-free data object: the URIs are device paths that only exist
     * once [HarnessCoverArt] has run, and a data fixture that depends on a
     * `Context` is a fixture that can't be read from a unit test. `by lazy` also
     * settles the ordering — nothing touches this until DI provisions a
     * repository, which is after the installer's pre-create hook.
     */
    private val roster: List<Artist> by lazy { HarnessCoverArt.withCovers(HarnessFixtures.roster) }

    /** The fixture artist, cover and all. [HarnessFixtures.roster] leads with it. */
    private val coveredArtist: Artist get() = roster.first()

    // --- Users: the seam the auth bypass depends on ---------------------------------------
    // RootViewModel asks this for the signed-in profile the moment the synthetic session
    // lands. Returning a COMPLETE profile is what moves the gate from Onboarding to the tab
    // shell; a null or half-filled one would strand the harness in signup.
    private val usersImpl: FakeUsersRepository by lazy {
        FakeUsersRepository(selfProfile = HarnessFixtures.selfProfile(HarnessState.flags))
    }

    val users: UsersRepository? get() = if (on) usersImpl else null

    // --- Artist dashboard / gigs / EPK ----------------------------------------------------

    private val artistsImpl: FakeArtistsRepository by lazy {
        // seedFull (not the constructor's plain seed) marks the row HYDRATED, so
        // `fetchArtist` returns the full profile — packages, samples, tech rider and all —
        // instead of a tile-shaped partial that would render a half-empty EPK.
        FakeArtistsRepository().apply { seedFull(listOf(coveredArtist)) }
    }

    private val bookingsImpl: FakeBookingsRepository by lazy {
        // The baseline set spans every status the dashboard buckets on. `seed-pending-request`
        // adds ONE more pending row so the artist accept/decline surface can be driven without
        // disturbing the baseline the other flags produce (mirrors the iOS harness's split).
        val rows = HarnessFixtures.bookings +
            if (HarnessState.flags.seedPendingRequest) listOf(HarnessFixtures.pendingBooking) else emptyList()
        FakeBookingsRepository(seed = rows)
    }

    private val requestsImpl: FakeRequestsRepository by lazy {
        FakeRequestsRepository(seed = listOf(HarnessFixtures.gigRequest))
    }

    private val scoreImpl: FakeScoreRepository by lazy {
        FakeScoreRepository(
            self = HarnessFixtures.score,
            byId = mapOf(HarnessFixtures.ARTIST_ID to HarnessFixtures.score),
            history = HarnessFixtures.scoreHistory,
        )
    }

    private val packagesImpl: FakePackagesRepository by lazy {
        FakePackagesRepository().also { fake ->
            seedBlocking { fake.replaceAll(HarnessFixtures.ARTIST_ID, HarnessFixtures.packageDrafts) }
        }
    }

    private val techRiderImpl: FakeTechRiderRepository by lazy {
        FakeTechRiderRepository().also { fake ->
            seedBlocking { fake.replaceAll(HarnessFixtures.ARTIST_ID, HarnessFixtures.techRider) }
        }
    }

    private val artistLinksImpl: FakeArtistLinksRepository by lazy {
        FakeArtistLinksRepository().also { fake ->
            seedBlocking {
                fake.upsert(HarnessFixtures.ARTIST_ID, "Instagram", "https://instagram.com/fixtureartist")
                fake.upsert(HarnessFixtures.ARTIST_ID, "Spotify", "https://open.spotify.com/artist/fixture")
            }
        }
    }

    // Seeded with the generated covers, so the EPK renders a populated gallery and its cover
    // agrees with the one Discover shows for the same artist (the server's rule too — the
    // cover IS position 0). It used to start empty, which meant the EPK only ever showed its
    // gradient fallback and the photo grid only ever showed its empty state.
    private val artistMediaImpl: ArtistMediaRepository by lazy {
        HarnessArtistMediaRepository(HarnessFixtures.ARTIST_ID, HarnessCoverArt.galleryFor(0))
    }

    // FakeSamplesRepository can only be seeded through `upload(File, …)`, which would mean
    // fabricating audio files on disk just to populate a list. This tiny read-only stand-in is
    // simpler and lets the EPK's samples section render its populated state.
    private val samplesImpl: SamplesRepository by lazy {
        HarnessSamplesRepository(HarnessFixtures.samples)
    }

    private val accountImpl: FakeAccountRepository by lazy { FakeAccountRepository() }
    private val reportsImpl: FakeReportsRepository by lazy { FakeReportsRepository() }

    // Nobody is blocked by default, so the harness inbox shows every seeded
    // thread; the point of the fake is that Block/Unblock round-trips offline
    // instead of throwing at a Supabase call the harness can never reach.
    //
    // Two launch flags move it off that default, and both exist because the
    // states they produce cannot be navigated to from inside a harness run:
    //
    //  - `seed-blocked-user` pre-blocks the fixture COUNTERPARTY, resolved from
    //    the booted role (a client blocks the artist; an artist blocks the
    //    client). Blocking someone hides the only screen that could block them,
    //    so "already blocked at launch" has no in-app path.
    //  - `block-list-unavailable` flips the fake to signed-out, which is how its
    //    reads and writes throw. That is the shape of every real failure here —
    //    offline, signed out, 0087 not applied — and it is the case the blocked
    //    accounts screen must render as "couldn't load", never as "empty".
    private val blockImpl: FakeBlockRepository by lazy {
        val seeded = if (HarnessState.flags.seedBlockedUser) {
            setOf(HarnessFixtures.counterpartUserId(HarnessState.flags.skipSignupAs))
        } else {
            emptySet()
        }
        FakeBlockRepository(seeded).apply {
            if (HarnessState.flags.blockListUnavailable) signedIn = false
        }
    }

    val artists: ArtistsRepository? get() = if (on) artistsImpl else null
    val bookings: BookingsRepository? get() = if (on) bookingsImpl else null
    val requests: RequestsRepository? get() = if (on) requestsImpl else null
    val score: ScoreRepository? get() = if (on) scoreImpl else null
    val packages: PackagesRepository? get() = if (on) packagesImpl else null
    val techRider: TechRiderRepository? get() = if (on) techRiderImpl else null
    val samples: SamplesRepository? get() = if (on) samplesImpl else null
    val artistMedia: ArtistMediaRepository? get() = if (on) artistMediaImpl else null
    val artistLinks: ArtistLinksRepository? get() = if (on) artistLinksImpl else null
    val account: AccountRepository? get() = if (on) accountImpl else null
    val reports: ReportsRepository? get() = if (on) reportsImpl else null
    val block: BlockRepository? get() = if (on) blockImpl else null

    // --- Client path: Discover / search / profile / saved / messages -----------------------

    // Pages the whole fixture roster rather than the single fixture artist: Discover's rails
    // group by category and the filter sheet derives its facets from the distinct categories
    // and cities present, so a one-card roster would render neither.
    //
    // Handed `artistsImpl` for the same reason the real search holds an ArtistsRepository: it
    // feeds every returned partial into the by-id cache. SearchViewModel has no cache of its
    // own, and ArtistProfileViewModel resolves a tapped card through find()/ensureFull() —
    // which, with artistsImpl seeded with the fixture artist ALONE, answered for nobody else
    // on the roster. Tapping a search result rendered "Artist not found." unless Discover had
    // happened to warm the cache first.
    private val searchImpl: FakeSearchRepository by lazy {
        FakeSearchRepository(roster, artistsRepository = artistsImpl)
    }

    private val reviewsImpl: FakeReviewsRepository by lazy {
        FakeReviewsRepository(byArtist = mapOf(HarnessFixtures.ARTIST_ID to HarnessFixtures.reviews))
    }

    private val savedArtistsImpl: FakeSavedArtistsRepository by lazy {
        FakeSavedArtistsRepository().also { fake ->
            seedBlocking { HarnessFixtures.savedArtistIds.forEach { fake.add(it) } }
        }
    }

    // The shared FakeMessagesRepository can only be filled through `send`, which stamps every
    // message as the viewer's own — so a thread seeded through it would render one bubble
    // treatment and hide the counterpart's. This stand-in seeds a genuine two-sided transcript.
    private val messagesImpl: MessagesRepository by lazy {
        HarnessMessagesRepository(HarnessState.flags.skipSignupAs?.let(HarnessFixtures::selfUserId))
    }

    val search: SearchRepository? get() = if (on) searchImpl else null
    val reviews: ReviewsRepository? get() = if (on) reviewsImpl else null
    val savedArtists: SavedArtistsRepository? get() = if (on) savedArtistsImpl else null
    val messages: MessagesRepository? get() = if (on) messagesImpl else null
}

/**
 * In-memory [MessagesRepository] seeded with one two-sided thread.
 *
 * Exists rather than reusing the shared `FakeMessagesRepository` because that one has no seed
 * path other than `send()`, which marks every message `isMine = true`. A transcript where the
 * viewer sent everything exercises only one of the two bubble treatments, so a broken
 * counterpart bubble — or a wrong-side alignment — would look fine in the harness.
 *
 * [viewerId] is the booted role's uid, so the same fixture transcript reads correctly from
 * either side: what the artist sees as theirs, the client sees as the counterpart's.
 */
private class HarnessMessagesRepository(viewerId: String?) : MessagesRepository {

    private val viewer = viewerId ?: HarnessFixtures.CLIENT_ID
    private val threads = mutableListOf(HarnessFixtures.thread)
    private val messages = mutableMapOf(
        HarnessFixtures.THREAD_ID to HarnessFixtures.harnessMessages(viewer).toMutableList(),
    )

    override suspend fun listThreadsForUser(): List<Thread> =
        threads.sortedByDescending { it.lastMessageAtEpochMs ?: 0L }

    override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> =
        messages[threadId].orEmpty()
            .filter { before == null || it.sentAtEpochMs <= before }
            .takeLast(limit)

    override suspend fun send(threadId: String, body: String): Message {
        val text = body.trim()
        require(text.isNotEmpty()) { "A message can't be empty." }
        val message = Message(
            id = "harness-sent-${System.nanoTime()}",
            threadId = threadId,
            senderId = viewer,
            body = text,
            sentAtEpochMs = System.currentTimeMillis(),
            isMine = true,
        )
        messages.getOrPut(threadId) { mutableListOf() }.add(message)
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) {
            threads[index] = threads[index].copy(
                lastPreview = text,
                lastMessageAtEpochMs = message.sentAtEpochMs,
            )
        }
        return message
    }

    override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String {
        threads.firstOrNull { it.artistId.equals(artistId, ignoreCase = true) }?.let { return it.id }
        val thread = Thread(
            id = "harness-thread-${threads.size + 1}",
            artistId = artistId.lowercase(),
            bookingId = bookingId,
        )
        threads += thread
        return thread.id
    }

    /** One viewer, one counter — nothing here for the real seam's seat to select (cf. [setMuted]). */
    override suspend fun markThreadRead(threadId: String, viewerIsArtist: Boolean) {
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) threads[index] = threads[index].copy(unreadCount = 0)
    }

    /**
     * The harness holds one viewer, so it stores the resolved viewer-relative
     * flag rather than the server's two columns — enough to drive the details
     * sheet's label flip on device. The per-side column rule is asserted against
     * `FakeMessagesRepository`, which models both columns.
     */
    override suspend fun setMuted(threadId: String, muted: Boolean) {
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) threads[index] = threads[index].copy(muted = muted)
    }

    override suspend fun markThreadReadReceipt(threadId: String) = Unit

    override suspend fun counterpartLastRead(threadId: String): Long? = null

    /** No WebSocket in the harness — hand back a no-op cancel token. */
    override suspend fun subscribeMessages(
        threadId: String,
        onInsert: (Message) -> Unit,
    ): MessagesSubscription = MessagesSubscription {}
}

/**
 * In-memory [ArtistMediaRepository] over locally-generated cover art.
 *
 * Exists rather than reusing the shared `FakeArtistMediaRepository` for the same
 * reason [HarnessSamplesRepository] does: that fake's only way in is
 * `uploadPhoto(File, …)`, which mints its own Supabase storage path, and a path
 * is not a picture — the URL it derives points at a bucket this process cannot
 * reach. These rows carry their image address directly (`directUrl`), which is
 * what lets a device with no network render a real gallery.
 *
 * Writes mutate only this list, so the EPK's add / delete / reorder affordances
 * behave while an operator is poking at the screen. An uploaded photo takes the
 * file it was handed as its own URL — the picker already produced a local file,
 * so the round trip shows the actual image instead of a placeholder.
 */
private class HarnessArtistMediaRepository(
    private val artistId: String,
    covers: List<String>,
) : ArtistMediaRepository {

    private val rows = covers.mapIndexed { index, url ->
        ArtistMediaItem(
            id = "harness-photo-$index",
            artistId = artistId.lowercase(),
            kind = ArtistMediaKind.photo,
            aspect = ArtistMediaAspect.portrait,
            position = index,
            // Blank on purpose: there is no object in any bucket behind this
            // row, and a plausible-looking path would invite someone to go
            // looking for one.
            storagePath = "",
            mimeType = "image/jpeg",
            width = 480,
            height = 600,
            directUrl = url,
        )
    }.toMutableList()

    override suspend fun list(artistId: String): List<ArtistMediaItem> =
        if (artistId.equals(this.artistId, ignoreCase = true)) rows.toList() else emptyList()

    override suspend fun uploadPhoto(
        jpegFile: File,
        artistId: String,
        position: Int?,
    ): ArtistMediaItem {
        val item = ArtistMediaItem(
            id = "harness-photo-${System.nanoTime()}",
            artistId = artistId.lowercase(),
            kind = ArtistMediaKind.photo,
            aspect = ArtistMediaAspect.portrait,
            position = position ?: rows.size,
            storagePath = "",
            mimeType = "image/jpeg",
            directUrl = jpegFile.toURI().toString(),
        )
        rows.add(item)
        return item
    }

    override suspend fun delete(item: ArtistMediaItem) {
        rows.removeAll { it.id == item.id }
    }

    override suspend fun reorder(ids: List<String>) {
        val byId = rows.associateBy { it.id }
        val reordered = ids.mapNotNull(byId::get) + rows.filter { it.id !in ids.toSet() }
        rows.clear()
        rows.addAll(reordered.mapIndexed { index, item -> item.copy(position = index) })
    }
}

/**
 * Read-only [SamplesRepository] over a fixed list. Exists because the shared
 * `FakeSamplesRepository` is write-seeded (its only way in is `upload(File, …)`), and the
 * harness needs a populated samples list without inventing audio files on disk.
 *
 * Writes are accepted so the EPK's add/delete affordances don't throw while an operator is
 * poking at the screen; they mutate only this in-memory list.
 */
private class HarnessSamplesRepository(seed: List<Sample>) : SamplesRepository {
    private val rows = seed.toMutableList()

    override suspend fun list(artistId: String): List<Sample> = rows.toList()

    override suspend fun upload(
        audioFile: File,
        title: String,
        durationSeconds: Double,
        artistId: String,
    ): Sample {
        val minutes = (durationSeconds / 60).toInt()
        val seconds = (durationSeconds % 60).toInt()
        val sample = Sample(
            id = "harness-sample-${rows.size + 1}",
            title = title,
            duration = "%d:%02d".format(minutes, seconds),
        )
        rows += sample
        return sample
    }

    override suspend fun delete(sampleId: String, storagePathOrUrl: String?) {
        rows.removeAll { it.id == sampleId }
    }
}
