package com.anisequel

import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.friendlyCopy
import com.anisequel.data.network.isConnectionTrouble
import com.anisequel.data.network.isWorthProbing
import com.anisequel.data.network.toReachability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the contract of the "AniList is down" surface.
 *
 * The copy is user-facing and deliberately large, which makes it easy to break
 * in ways the compiler cannot see: an error kind added without a headline, a
 * quip list that shrinks back to one line (the retry loop this feature exists
 * for), a duplicate that makes two different failures show the same joke, or a
 * selection function that can throw on a hash of [Int.MIN_VALUE].
 *
 * These are requirements, not preferences: the app promises every failure has a
 * friendly sentence and that the down pool is big enough to be funny more than
 * once.
 */
class ApiStatusCopyTest {

    @Test
    fun `every failure kind has a headline, a reason and jokes`() {
        AniListErrorKind.values().forEach { kind ->
            val copy = kind.friendlyCopy()

            assertTrue("$kind has no headline", copy.headline.isNotBlank())
            assertTrue("$kind has no detail", copy.detail.isNotBlank())
            assertTrue("$kind needs at least four jokes", copy.quips.size >= 4)
            copy.quips.forEach { quip ->
                assertTrue("$kind has a blank joke", quip.isNotBlank())
            }
            assertEquals(
                "$kind repeats a joke; the point of a pool is that it does not",
                copy.quips.size,
                copy.quips.distinct().size
            )
        }
    }

    @Test
    fun `the down pool is large enough that retrying does not loop the same line`() {
        val server = AniListErrorKind.SERVER.friendlyCopy()
        val offline = AniListErrorKind.OFFLINE.friendlyCopy()

        assertTrue(
            "the server-down pool has shrunk to ${server.quips.size} lines; " +
                "it was deliberately forty-plus",
            server.quips.size >= 40
        )
        // Offline is the same pool plus its own lines, because the reader's
        // experience is "no answer" in both cases.
        assertTrue(offline.quips.size > server.quips.size)
        assertTrue(
            "the line the feature was asked for is gone",
            server.quips.contains("Shhh. Ani-chan is sleeping.")
        )
    }

    @Test
    fun `a probe is only spent on failures that leave reachability ambiguous`() {
        assertTrue(AniListErrorKind.OFFLINE.isWorthProbing())
        assertTrue(AniListErrorKind.SERVER.isWorthProbing())
        assertTrue(AniListErrorKind.UNKNOWN.isWorthProbing())

        assertFalse(AniListErrorKind.INVALID_SESSION.isWorthProbing())
        assertFalse(AniListErrorKind.RATE_LIMITED.isWorthProbing())
        assertFalse(AniListErrorKind.NOT_FOUND.isWorthProbing())
        assertFalse(AniListErrorKind.INVALID_REQUEST.isWorthProbing())
        assertFalse(AniListErrorKind.MALFORMED_RESPONSE.isWorthProbing())
    }

    @Test
    fun `a failure seeds the reachability it already proves`() {
        assertEquals(ApiReachability.OFFLINE, AniListErrorKind.OFFLINE.toReachability())
        assertEquals(ApiReachability.DOWN, AniListErrorKind.SERVER.toReachability())
        assertEquals(ApiReachability.RATE_LIMITED, AniListErrorKind.RATE_LIMITED.toReachability())
        assertEquals(ApiReachability.UNKNOWN, AniListErrorKind.UNKNOWN.toReachability())
        assertEquals(ApiReachability.UNKNOWN, (null as AniListErrorKind?).toReachability())

        // These all came from AniList, so saying "unreachable" would be a lie.
        listOf(
            AniListErrorKind.INVALID_SESSION,
            AniListErrorKind.NOT_FOUND,
            AniListErrorKind.INVALID_REQUEST,
            AniListErrorKind.MALFORMED_RESPONSE
        ).forEach { kind ->
            assertEquals(ApiReachability.ONLINE, kind.toReachability())
        }
    }

    @Test
    fun `only connection failures get the broken heart`() {
        assertTrue((null as AniListErrorKind?).isConnectionTrouble())
        assertTrue(AniListErrorKind.OFFLINE.isConnectionTrouble())
        assertTrue(AniListErrorKind.SERVER.isConnectionTrouble())
        assertTrue(AniListErrorKind.RATE_LIMITED.isConnectionTrouble())
        assertTrue(AniListErrorKind.UNKNOWN.isConnectionTrouble())

        assertFalse(AniListErrorKind.INVALID_SESSION.isConnectionTrouble())
        assertFalse(AniListErrorKind.NOT_FOUND.isConnectionTrouble())
        assertFalse(AniListErrorKind.INVALID_REQUEST.isConnectionTrouble())
        assertFalse(AniListErrorKind.MALFORMED_RESPONSE.isConnectionTrouble())
    }

    @Test
    fun `quip selection is deterministic and always in range`() {
        val copy = AniListErrorKind.SERVER.friendlyCopy()
        val seeds = listOf(
            "",
            "x",
            "HTTP 500",
            "AniList is having a day",
            "\u0000\u0001\u0002",
            "üñïçødé",
            "a".repeat(2_000)
        )

        seeds.forEach { seed ->
            val first = copy.quipFor(seed)
            assertEquals("the same seed must not shuffle under recomposition", first, copy.quipFor(seed))
            assertTrue("quipFor returned a line that is not in the pool", copy.quips.contains(first))
        }

        // A negative hash is the case a naive `abs`/`%` pair gets wrong: `%` in
        // Kotlin can return a negative index, which would crash the error screen.
        val negativeSeed = generateSequence("quip") { it + "s" }
            .take(100)
            .first { it.hashCode() < 0 }
        assertTrue(copy.quips.contains(copy.quipFor(negativeSeed)))
    }

    @Test
    fun `every reachability has copy and a latency-aware detail`() {
        ApiReachability.values().forEach { reachability ->
            val copy = reachability.friendlyCopy()
            assertTrue("$reachability has no headline", copy.headline.isNotBlank())
            assertTrue("$reachability has no detail", copy.detail.isNotBlank())
            assertTrue("$reachability has no jokes", copy.quips.isNotEmpty())
        }

        assertTrue(ApiReachability.ONLINE.detail(240).contains("240 ms"))
        assertTrue(ApiReachability.SLOW.detail(3_200).contains("3.2 s"))
    }

    @Test
    fun `reachability labels are distinct`() {
        val labels = ApiReachability.values().map { it.label }
        assertEquals(labels.size, labels.distinct().size)
    }
}
