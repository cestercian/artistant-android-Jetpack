package `in`.artistant.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The account page's email row masks at RENDER — the stored address is never
 * touched, we just refuse to put the whole thing on a screen.
 *
 * The interesting cases are all the ones where a naive "keep first and last"
 * would corrupt rather than shorten: a one-character local part (no distinct
 * last character), an address with no local part at all, and anything carrying
 * a second `@`. Every one of those returns the input verbatim, because a value
 * we cannot parse is a value we cannot safely abbreviate — a half-masked string
 * would be worse than either showing it or hiding it.
 */
class MaskEmailTest {

    @Test
    fun `keeps the first and last character of the local part`() {
        assertEquals("y•••d@gmail.com", maskEmail("yashafaid@gmail.com"))
    }

    @Test
    fun `keeps the domain intact so the owner can tell which account it is`() {
        assertEquals("a•••a@artistant.in", maskEmail("asha@artistant.in"))
    }

    @Test
    fun `a two-character local part still has a distinct head and tail`() {
        assertEquals("a•••b@x.com", maskEmail("ab@x.com"))
    }

    @Test
    fun `a one-character local part degrades rather than duplicating itself`() {
        // 'x' is both the first and the last character; the tail must come back
        // empty instead of repeating the head as "x•••x".
        assertEquals("x•••@x.com", maskEmail("x@x.com"))
    }

    @Test
    fun `an address with no local part is returned untouched`() {
        assertEquals("@nolocal.com", maskEmail("@nolocal.com"))
    }

    @Test
    fun `an address with no domain is returned untouched`() {
        assertEquals("trailing@", maskEmail("trailing@"))
    }

    @Test
    fun `a value with no at sign is returned untouched`() {
        assertEquals("not an email", maskEmail("not an email"))
    }

    @Test
    fun `a value with two at signs is returned untouched`() {
        assertEquals("a@b@c.com", maskEmail("a@b@c.com"))
    }

    @Test
    fun `an empty value is returned untouched`() {
        assertEquals("", maskEmail(""))
    }
}
