package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The role × status action matrix, pinned exhaustively.
 *
 * This is the file that stops the wrong CTA shipping. The failure modes it
 * guards are not cosmetic:
 *
 *  - an Accept offered to a client 403s against mig 0083 (only the artist may
 *    flip pending_confirm → confirmed);
 *  - a Message offered to the artist on a PENDING booking is a button that
 *    always errors — no thread exists until confirm, and `findOrCreateThread`
 *    refuses artist-side creation before it;
 *  - anything actionable offered on [BookingStatus.Unknown] acts on a state this
 *    build cannot reason about.
 *
 * The matrix is asserted as whole SETS rather than as individual `assertTrue`s
 * so that an action accidentally leaking into a cell fails the test — a
 * per-action check only catches the ones someone thought to write.
 */
class BookingDetailLogicTest {

    /**
     * The whole cell of the matrix: everything the dock offers plus everything
     * the manage list does. Composed here rather than on [BookingActions],
     * because the screen never asks for the union — it renders the two halves
     * separately, and a production accessor only tests would call is one more
     * thing to keep true.
     */
    private fun actions(viewer: BookingViewer, status: BookingStatus): Set<BookingAction> =
        (BookingActions.primary(viewer, status) + BookingActions.manage(viewer, status)).toSet()

    // --- the matrix, cell by cell -------------------------------------------

    @Test
    fun client_pendingConfirm_canMessageAndWithdraw() {
        assertEquals(
            setOf(BookingAction.Message, BookingAction.Cancel),
            actions(BookingViewer.Client, BookingStatus.PendingConfirm),
        )
    }

    @Test
    fun client_confirmed_getsTheFullManageSet() {
        assertEquals(
            setOf(
                BookingAction.Message,
                BookingAction.OpenMaps,
                BookingAction.CopyAddress,
                BookingAction.Share,
                BookingAction.AddToCalendar,
                BookingAction.Cancel,
            ),
            actions(BookingViewer.Client, BookingStatus.Confirmed),
        )
    }

    @Test
    fun client_completed_canReview() {
        assertEquals(
            setOf(BookingAction.Message, BookingAction.LeaveReview),
            actions(BookingViewer.Client, BookingStatus.Completed),
        )
    }

    @Test
    fun client_cancelled_keepsOnlyTheThread() {
        assertEquals(
            setOf(BookingAction.Message),
            actions(BookingViewer.Client, BookingStatus.Cancelled),
        )
    }

    @Test
    fun client_disputed_keepsOnlyTheThread() {
        assertEquals(
            setOf(BookingAction.Message),
            actions(BookingViewer.Client, BookingStatus.Disputed),
        )
    }

    @Test
    fun artist_pendingConfirm_answersTheRequestAndNothingElse() {
        // Message is REPLACED here, not merely deprioritised: there is no thread
        // to open on a pending booking.
        assertEquals(
            setOf(BookingAction.Accept, BookingAction.Decline),
            actions(BookingViewer.Artist, BookingStatus.PendingConfirm),
        )
    }

    @Test
    fun artist_confirmed_getsTheSameManageSetAsTheClient() {
        // Both sides need to get to the venue, and the artist has had no way out
        // of a gig they accepted — Accept/Decline is only offered while pending.
        assertEquals(
            actions(BookingViewer.Client, BookingStatus.Confirmed),
            actions(BookingViewer.Artist, BookingStatus.Confirmed),
        )
    }

    @Test
    fun artist_completed_cannotReviewTheirOwnGig() {
        assertEquals(
            setOf(BookingAction.Message),
            actions(BookingViewer.Artist, BookingStatus.Completed),
        )
    }

    @Test
    fun artist_cancelled_keepsOnlyTheThread() {
        assertEquals(
            setOf(BookingAction.Message),
            actions(BookingViewer.Artist, BookingStatus.Cancelled),
        )
    }

    @Test
    fun artist_disputed_keepsOnlyTheThread() {
        assertEquals(
            setOf(BookingAction.Message),
            actions(BookingViewer.Artist, BookingStatus.Disputed),
        )
    }

    // --- the Unknown fallback stays inert ------------------------------------

    @Test
    fun unknownStatus_offersNoActionableCta_toEitherSide() {
        val actionable = setOf(
            BookingAction.Accept,
            BookingAction.Decline,
            BookingAction.Cancel,
            BookingAction.AddToCalendar,
            BookingAction.LeaveReview,
        )
        for (viewer in BookingViewer.entries) {
            val offered = actions(viewer, BookingStatus.Unknown)
            assertEquals(
                "$viewer on an unrecognised status may only message",
                setOf(BookingAction.Message),
                offered,
            )
            assertTrue((offered intersect actionable).isEmpty())
        }
    }

    @Test
    fun isActionable_isTrueOnlyForTheTwoLiveStatuses() {
        assertTrue(BookingStatus.PendingConfirm.isActionable())
        assertTrue(BookingStatus.Confirmed.isActionable())
        assertFalse(BookingStatus.Completed.isActionable())
        assertFalse(BookingStatus.Cancelled.isActionable())
        assertFalse(BookingStatus.Disputed.isActionable())
        assertFalse(BookingStatus.Unknown.isActionable())
    }

    @Test
    fun everyStatusOffersSomethingToEverySide_soNoCellIsAccidentallyEmpty() {
        for (viewer in BookingViewer.entries) {
            for (status in BookingStatus.entries) {
                assertTrue(
                    "$viewer/$status left the dock with no primary action",
                    BookingActions.primary(viewer, status).isNotEmpty(),
                )
            }
        }
    }

    // --- dock vs manage split ------------------------------------------------

    @Test
    fun primary_isAcceptDecline_onlyForTheArtistOnAPendingRequest() {
        assertEquals(
            listOf(BookingAction.Accept, BookingAction.Decline),
            BookingActions.primary(BookingViewer.Artist, BookingStatus.PendingConfirm),
        )
        assertEquals(
            listOf(BookingAction.Message),
            BookingActions.primary(BookingViewer.Client, BookingStatus.PendingConfirm),
        )
        assertEquals(
            listOf(BookingAction.Message),
            BookingActions.primary(BookingViewer.Artist, BookingStatus.Confirmed),
        )
    }

    @Test
    fun manage_ordersTheConfirmedRowsTravelFirstThenAdminThenTheExit() {
        assertEquals(
            listOf(
                BookingAction.OpenMaps,
                BookingAction.CopyAddress,
                BookingAction.Share,
                BookingAction.AddToCalendar,
                BookingAction.Cancel,
            ),
            BookingActions.manage(BookingViewer.Client, BookingStatus.Confirmed),
        )
    }

    @Test
    fun manageRowsAndDockSecondary_partitionTheSecondarySet_soNothingRendersTwice() {
        // The duplicate this guards: a client's pending Cancel used to render as
        // an in-page row AND as a dock button, two controls for one act.
        for (viewer in BookingViewer.entries) {
            for (status in BookingStatus.entries) {
                val rows = BookingActions.manageRows(viewer, status)
                val dock = BookingActions.dockSecondary(viewer, status)

                assertTrue(
                    "$viewer/$status renders ${rows intersect dock.toSet()} twice",
                    (rows intersect dock.toSet()).isEmpty(),
                )
                assertEquals(
                    "$viewer/$status drops a secondary action on the floor",
                    BookingActions.manage(viewer, status).toSet(),
                    (rows + dock).toSet(),
                )
            }
        }
    }

    @Test
    fun aPendingClientsCancelIsPinnedInTheDock_notListedAsARow() {
        assertEquals(
            emptyList<BookingAction>(),
            BookingActions.manageRows(BookingViewer.Client, BookingStatus.PendingConfirm),
        )
        assertEquals(
            listOf(BookingAction.Cancel),
            BookingActions.dockSecondary(BookingViewer.Client, BookingStatus.PendingConfirm),
        )
    }

    @Test
    fun aConfirmedBookingsActionsAreAllRows_soTheDockStaysOneButton() {
        assertEquals(
            emptyList<BookingAction>(),
            BookingActions.dockSecondary(BookingViewer.Client, BookingStatus.Confirmed),
        )
        assertEquals(
            BookingActions.manage(BookingViewer.Artist, BookingStatus.Confirmed),
            BookingActions.manageRows(BookingViewer.Artist, BookingStatus.Confirmed),
        )
    }

    @Test
    fun aCompletedBookingsReviewIsPinnedInTheDock() {
        assertEquals(
            listOf(BookingAction.LeaveReview),
            BookingActions.dockSecondary(BookingViewer.Client, BookingStatus.Completed),
        )
        assertTrue(BookingActions.dockSecondary(BookingViewer.Artist, BookingStatus.Completed).isEmpty())
    }

    @Test
    fun gettingThere_onlyOnceTheGigIsConfirmed() {
        assertTrue(BookingActions.showsGettingThere(BookingStatus.Confirmed))
        assertFalse(BookingActions.showsGettingThere(BookingStatus.PendingConfirm))
        assertFalse(BookingActions.showsGettingThere(BookingStatus.Completed))
        assertFalse(BookingActions.showsGettingThere(BookingStatus.Unknown))
    }

    // --- cancel routing ------------------------------------------------------

    @Test
    fun cancelActor_followsTheSideOfTheRow_notTheAccountRole() {
        // The stamp drives whose cancellation-rate metric moves, and 0083 rejects
        // a client claiming the artist's.
        assertEquals(CancelActor.Artist, cancelActor(BookingViewer.Artist))
        assertEquals(CancelActor.Client, cancelActor(BookingViewer.Client))
        assertEquals("artist", cancelActor(BookingViewer.Artist).dbValue)
        assertEquals("client", cancelActor(BookingViewer.Client).dbValue)
    }

    @Test
    fun cancelReasons_differPerSide_andNeverBlameTheReaderForTheirOwnAct() {
        val client = cancelReasons(BookingViewer.Client).map { it.label }
        val artist = cancelReasons(BookingViewer.Artist).map { it.label }

        assertTrue(client.contains("Artist unresponsive"))
        assertFalse("an artist can't cite themselves as unresponsive", artist.contains("Artist unresponsive"))
        assertTrue(artist.contains("Client unresponsive"))
        // Both lists keep an escape hatch, or the Continue button can't unlock.
        assertTrue(client.contains("Other"))
        assertTrue(artist.contains("Other"))
    }

    @Test
    fun cancelConsequences_areWrittenForTheSideReadingThem() {
        val client = cancelConsequences(BookingViewer.Client, "Nova Beats", daysBefore = 24)
        val artist = cancelConsequences(BookingViewer.Artist, "Riya", daysBefore = 24)

        // Each side is told what it costs THEM. Only an artist's cancellation
        // moves a score metric, and the client's is the one where nothing is
        // refunded because nothing was ever held.
        assertTrue(artist.any { it.title.contains("cancellation rate") })
        assertFalse(client.any { it.title.contains("cancellation rate") })
        assertTrue(client.any { it.title == "No money moves" })

        // The counterparty is named, not called "the artist".
        assertTrue(client.any { it.title.startsWith("Nova Beats") })
        assertTrue(artist.any { it.title.startsWith("Riya") })

        // The thread survives a cancellation on both sides — the copy has to say
        // so, because it is the one thing people assume they lose.
        for (list in listOf(client, artist)) {
            assertTrue(list.any { it.title == "Your thread stays open" })
        }
    }

    @Test
    fun theThirdConsequenceIsSpecificToThisDate() {
        // Screen 52's note: "the second one is specific to this date". What it
        // does NOT say is anything about a 7-day scoring window — that window is
        // the Edge Function's REFUND ladder, and v1 holds no money for it to
        // apply to.
        val far = cancelConsequences(BookingViewer.Client, "Nova", daysBefore = 24)
        assertTrue(far.any { it.title == "This is 24 days before the date" })
        assertFalse(far.any { it.detail.contains("score") })

        assertTrue(
            cancelConsequences(BookingViewer.Client, "Nova", daysBefore = 0)
                .any { it.title == "This is today" },
        )
        assertTrue(
            cancelConsequences(BookingViewer.Client, "Nova", daysBefore = 1)
                .any { it.title == "This is tomorrow" },
        )
        // Short notice changes the wording, never the outcome.
        assertTrue(
            cancelConsequences(BookingViewer.Client, "Nova", daysBefore = 2)
                .any { it.detail.contains("Short notice") },
        )
    }

    @Test
    fun aBookingWithNoReadableDateDropsTheDateSpecificLine() {
        // Guessing at "this is 0 days before the date" would be worse than
        // saying three things instead of four.
        val list = cancelConsequences(BookingViewer.Client, "Nova", daysBefore = null)
        assertEquals(3, list.size)
        assertFalse(list.any { it.title.startsWith("This is") })
    }

    // --- which page is being drawn -------------------------------------------

    @Test
    fun everyStatusMapsToExactlyOneDetailVariant() {
        assertEquals(BookingDetailVariant.Awaiting, variantFor(BookingStatus.PendingConfirm))
        assertEquals(BookingDetailVariant.Confirmed, variantFor(BookingStatus.Confirmed))
        // Completed shares the confirmed page: the night is the same schedule,
        // read afterwards.
        assertEquals(BookingDetailVariant.Confirmed, variantFor(BookingStatus.Completed))
        assertEquals(BookingDetailVariant.Cancelled, variantFor(BookingStatus.Cancelled))
        assertEquals(BookingDetailVariant.Disputed, variantFor(BookingStatus.Disputed))
        // A status this build cannot read degrades to read-only rather than
        // guessing at an action — the same rule `isActionable` encodes.
        assertEquals(BookingDetailVariant.ReadOnly, variantFor(BookingStatus.Unknown))
    }

    @Test
    fun theHeaderQuotesTheSharedBookingReference() {
        // The same reference the invoice and the confirmed screen print, derived
        // from the row's own UUID — one booking reads identically wherever it
        // appears, and support can resolve it by prefix.
        assertEquals(
            "Booking #AR-4F2A11",
            bookingTitle("4f2a1111-2222-3333-4444-5555556666bb"),
        )
    }

    @Test
    fun anIdWithNoReferenceInItDropsTheHash() {
        // "Booking #" with nothing after it reads as a rendering fault.
        assertEquals("Booking", bookingTitle(""))
        assertEquals("Booking", bookingTitle("----"))
    }

    // --- the night, and the wait ---------------------------------------------

    @Test
    fun theRunOfShowIsBuiltOnlyFromWhatTheRowHolds() {
        // No invented load-in or soundcheck: `bookings` carries a start, an end,
        // the labels and one free-text note, and nothing else.
        val b = booking(id = "b-1", startIso = "2026-10-12T14:30:00Z").copy(
            endDatetimeIso = "2026-10-12T16:30:00Z",
            venueNotes = "Gate 3, load in from the lane",
        )
        val moments = runOfShow(b, nowMs = 0L)

        assertEquals(2, moments.size)
        assertTrue(moments[0].title.endsWith("Set starts"))
        assertEquals("Gate 3, load in from the lane", moments[0].detail)
        assertTrue(moments[1].title.endsWith("Set ends"))
    }

    @Test
    fun aRowWithNoClockHasNoRunOfShow() {
        val b = booking(id = "b-1", date = "TBD", time = "")
        assertTrue(runOfShow(b, nowMs = 0L).isEmpty())
    }

    @Test
    fun momentsAlreadyPastAreMarkedDone() {
        val b = booking(id = "b-1", startIso = "2026-10-12T14:30:00Z")
        val before = runOfShow(b, nowMs = 1_000L).first()
        val after = runOfShow(b, nowMs = Long.MAX_VALUE / 2).first()
        assertFalse(before.done)
        assertTrue(after.done)
    }

    @Test
    fun theRequestProgressNeverMarksAnAnswerThatHasNotCome() {
        val steps = requestProgress(booking(id = "b-1", createdAtEpochMs = 1_000L), nowMs = 61_000L)
        assertEquals(listOf(true, false, false), steps.map { it.done })
        assertEquals("1 minute ago", steps.first().detail)
    }

    @Test
    fun aTimestampWeDoNotHaveDropsTheLineRatherThanFillingIt() {
        // `created_at` absent from a projection decodes to 0. "in -1 minutes" and
        // a date in 1970 are both worse than no line at all.
        assertNull(relativeSince(0L, nowMs = 1_000L))
        assertNull(relativeSince(5_000L, nowMs = 1_000L))
        assertEquals("Just now", relativeSince(1_000L, nowMs = 1_030L))
        assertEquals("2 hours ago", relativeSince(1_000L, nowMs = 1_000L + 2 * 3_600_000L))
        assertEquals("3 days ago", relativeSince(1_000L, nowMs = 1_000L + 3 * 86_400_000L))
    }

    // --- the cancelled record ------------------------------------------------

    @Test
    fun theCancelledPageNamesWhoPulledOut_inTheSecondPersonWhereItWasYou() {
        val byClient = booking(id = "b-1", status = BookingStatus.Cancelled)
            .copy(cancelledBy = "client")
        assertEquals(
            "You cancelled this booking",
            cancelledByLine(byClient, BookingViewer.Client, "Nova Beats"),
        )
        assertEquals(
            "Nova Beats cancelled this booking",
            cancelledByLine(byClient, BookingViewer.Artist, "Nova Beats"),
        )
    }

    @Test
    fun aRowWithNoCancellationStampClaimsNothingAboutWho() {
        val b = booking(id = "b-1", status = BookingStatus.Cancelled)
        assertEquals(
            "This booking was cancelled",
            cancelledByLine(b, BookingViewer.Client, "Nova Beats"),
        )
        // …and the header degrades to the bare word rather than a 1970 date.
        assertEquals("Cancelled", cancelledOnLabel(b))
    }

    // --- terms ---------------------------------------------------------------

    @Test
    fun bookingTerms_carryTheWholeAgreement_inReadingOrder() {
        val terms = bookingTerms(booking(id = "4f2a1111-2222-3333-4444-5555556666bb"), "Evening set")

        assertEquals(
            listOf("Package", "Date", "Time", "Venue", "Guests", "Booking ID"),
            terms.map { it.label },
        )
        assertEquals("Evening set", terms.first().value)
        assertEquals("80", terms.first { it.label == "Guests" }.value)
    }

    @Test
    fun bookingTerms_dropThePackageRow_ratherThanInventingATier() {
        // A dangling package index (the artist republished) and a package-less
        // artist both arrive here as null. Neither may render "Custom": that
        // asserts a tier the artist never published.
        val terms = bookingTerms(booking(), packageName = null)

        assertFalse(terms.any { it.label == "Package" })
        assertEquals("Date", terms.first().label)
    }

    @Test
    fun bookingTerms_dropABlankPackageName() {
        assertFalse(bookingTerms(booking(), "   ").any { it.label == "Package" })
    }

    @Test
    fun bookingTerms_dropAnyBlankRow_soNoLabelSitsBesideNothing() {
        // A projection that skipped a column arrives here as "". A "Time" label
        // with nothing beside it reads as a rendering fault — and the hero line
        // on the same screen already drops the same blank, so leaving it in had
        // the two halves of one page disagreeing about one booking.
        val terms = bookingTerms(booking(time = "", venue = "  "), packageName = "Evening set")

        assertEquals(listOf("Package", "Date", "Guests", "Booking ID"), terms.map { it.label })
        assertTrue(terms.none { it.value.isBlank() })
    }

    @Test
    fun bookingId_isElidedInTheMiddle_andMarkedAsAMachineValue() {
        val row = bookingTerms(booking(id = "4f2a1111-2222-3333-4444-5555556666bb"), null)
            .first { it.label == "Booking ID" }

        assertEquals("4f2a…66bb", row.value)
        assertTrue(row.mono)
    }

    @Test
    fun shortBookingIds_areShownWhole() {
        assertEquals("b-1", truncatedBookingId("b-1"))
        assertEquals("123456789012", truncatedBookingId("123456789012"))
    }

    // --- hero line + address -------------------------------------------------

    @Test
    fun heroWhereLine_readsVenueThenShortDateThenTime() {
        assertEquals("Rooftop · May 16 · 8:30 PM", heroWhereLine(booking()))
    }

    @Test
    fun heroWhereLine_dropsBlankParts_ratherThanDanglingASeparator() {
        assertEquals("May 16 · 8:30 PM", heroWhereLine(booking(venue = "")))
        assertEquals("Rooftop · May 16", heroWhereLine(booking(time = "")))
    }

    @Test
    fun shortDate_collapsesTheStoredLabel() {
        assertEquals("May 16", shortDate("Sat, May 16, 2026"))
        assertEquals("Dec 1", shortDate("Tue, Dec 1, 2026"))
    }

    @Test
    fun shortDate_passesThroughAnythingItDoesNotRecognise() {
        // A wrong slice reads as a wrong date, which is worse than a long one.
        assertEquals("2026-05-16", shortDate("2026-05-16"))
        assertEquals("TBD", shortDate("TBD"))
    }

    @Test
    fun venueAddress_appendsTheArtistsCity_becauseTheRowHasNoneOfItsOwn() {
        assertEquals("Rooftop, Bangalore", venueAddress("Rooftop", "Bangalore"))
    }

    @Test
    fun venueAddress_degradesWithoutATrailingComma() {
        assertEquals("Rooftop", venueAddress("Rooftop", null))
        assertEquals("Rooftop", venueAddress("Rooftop", "   "))
        assertEquals("Bangalore", venueAddress("", "Bangalore"))
        assertEquals("", venueAddress("", null))
    }

    // --- share ---------------------------------------------------------------

    @Test
    fun shareText_namesTheCounterparty_theWindow_andThePlace() {
        val text = shareGigText("Nova Beats", booking(), "Bangalore")

        assertEquals(
            listOf("Nova Beats", "Sat, May 16, 2026 · 8:30 PM", "Rooftop, Bangalore"),
            text.lines(),
        )
    }

    @Test
    fun shareText_appendsLoadInNotesOnlyWhenThereAreSome() {
        val withNotes = booking().copy(venueNotes = "Gate 3, load-in at the back")
        assertTrue(shareGigText("Nova Beats", withNotes, "Bangalore").contains("Getting there: Gate 3"))

        assertFalse(shareGigText("Nova Beats", booking().copy(venueNotes = "  "), null).contains("Getting there"))
        assertFalse(shareGigText("Nova Beats", booking(), null).contains("Getting there"))
    }
}
