package com.anisequel

import com.anisequel.domain.jokes.JokeDwell
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.domain.jokes.JokeTone
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The joke settings' contract.
 *
 * These are the rules the settings screen promises in prose and the toast has to
 * keep. A frequency dropdown is easy to build and easy to make a lie: if the host
 * keeps its own constants, "Chatty" can be slower than "Calm" and nothing on the
 * screen would notice. The timing lives in the enums precisely so this can be
 * pinned without a device, a clock or a coroutine.
 */
class JokeSettingsTest {

    @Test
    fun `off never waits and never schedules`() {
        assertFalse(JokeFrequency.OFF.showsOnItsOwn)
        repeat(50) { seed ->
            val random = Random(seed)
            assertEquals(0L, JokeFrequency.OFF.firstWaitMillis(random))
            assertEquals(0L, JokeFrequency.OFF.repeatWaitMillis(random))
        }
    }

    @Test
    fun `off wins over the switch`() {
        assertFalse(
            JokeSettings(enabled = true, frequency = JokeFrequency.OFF).autoJokesEnabled
        )
    }

    @Test
    fun `the master switch wins over chatty`() {
        assertFalse(JokeSettings(enabled = false, frequency = JokeFrequency.CHATTY).autoJokesEnabled)
        assertTrue(JokeSettings(enabled = true, frequency = JokeFrequency.CHATTY).autoJokesEnabled)
    }

    @Test
    fun `every wait is inside its own frequency's window`() {
        JokeFrequency.entries
            .filter { it.showsOnItsOwn }
            .forEach { frequency ->
                repeat(100) { seed ->
                    val random = Random(seed)
                    val first = frequency.firstWaitMillis(random)
                    val repeat = frequency.repeatWaitMillis(random)

                    assertTrue(
                        "${frequency.displayName} first wait $first is outside its window",
                        first in frequency.firstDelayMinMillis until frequency.firstDelayMaxMillis
                    )
                    assertTrue(
                        "${frequency.displayName} repeat wait $repeat is outside its window",
                        repeat in frequency.repeatDelayMinMillis until frequency.repeatDelayMaxMillis
                    )
                }
            }
    }

    @Test
    fun `chatty is faster than calm at both waits`() {
        assertTrue(JokeFrequency.CHATTY.firstDelayMaxMillis < JokeFrequency.CALM.firstDelayMinMillis)
        assertTrue(
            JokeFrequency.CHATTY.repeatDelayMaxMillis < JokeFrequency.CALM.repeatDelayMinMillis
        )
    }

    @Test
    fun `normal sits between calm and chatty`() {
        // Shorter waits mean more jokes, so Chatty's window is the smallest and
        // Calm's is the largest. Comparing the minima keeps the assertion true by
        // construction rather than relying on where the two ranges touch.
        assertTrue(JokeFrequency.CHATTY.firstDelayMinMillis < JokeFrequency.NORMAL.firstDelayMinMillis)
        assertTrue(JokeFrequency.NORMAL.firstDelayMinMillis < JokeFrequency.CALM.firstDelayMinMillis)
    }

    @Test
    fun `every dwell is positive and exactly one arm waits for a tap`() {
        JokeDwell.entries
            .filterNot { it.staysUntilDismissed }
            .forEach { dwell ->
                val millis = dwell.visibleMillis
                assertTrue("${dwell.displayName} has no duration", millis != null && millis > 0)
            }

        assertEquals(
            "more than one dwell means 'until tapped'; the host branches on a " +
                "single nullable value and two manual arms would make one of them " +
                "silently a timer",
            1,
            JokeDwell.entries.count { it.staysUntilDismissed }
        )
        assertNull(JokeDwell.UNTIL_DISMISSED.visibleMillis)
    }

    @Test
    fun `the dwell arms are ordered from shortest to longest`() {
        val timed = JokeDwell.entries.mapNotNull { it.visibleMillis }

        assertEquals(
            "the dwell chips read shortest to longest, so the numbers behind " +
                "them have to as well",
            timed.sorted(),
            timed
        )
    }

    @Test
    fun `an empty tone set draws from everything`() {
        // The Settings screen refuses to switch the last tone off, but a cleared
        // preference or a downgrade can still produce an empty set. All tones is
        // the only answer that cannot look like a broken toast.
        assertEquals(JokeTone.entries.toSet(), JokeSettings(tones = emptySet()).activeTones)
        assertEquals(setOf(JokeTone.META), JokeSettings(tones = setOf(JokeTone.META)).activeTones)
    }

    @Test
    fun `moments default to the timer only`() {
        assertTrue(
            "a new install should behave like the feature always did: the timer, " +
                "nothing reacting to a gesture",
            JokeSettings().moments.isEmpty()
        )
    }

    @Test
    fun `moment gaps are ordered from the greeting to the quietest moment`() {
        assertEquals(0L, JokeMoment.OPEN.minGapMillis)
        assertTrue(JokeMoment.OPEN.minGapMillis < JokeMoment.REFRESH.minGapMillis)
        assertTrue(JokeMoment.REFRESH.minGapMillis < JokeMoment.TAB_CHANGE.minGapMillis)
    }

    @Test
    fun `unknown stored values fall back to a working default`() {
        // A downgrade can read a value a newer build wrote. Losing the choice is
        // recoverable; throwing on launch is not.
        assertEquals(JokeFrequency.NORMAL, JokeFrequency.fromStorage("warp-speed"))
        assertEquals(JokePosition.BOTTOM, JokePosition.fromStorage(null))
        assertEquals(JokeScope.MAIN_PAGES, JokeScope.fromStorage("somewhere-new"))
        assertEquals(JokeDwell.STANDARD, JokeDwell.fromStorage("forever-and-a-day"))
        assertEquals(emptySet<JokeTone>(), JokeTone.fromStorage(setOf("vibes", "craft-ish")))
        assertEquals(emptySet<JokeMoment>(), JokeMoment.fromStorage(setOf("whenever")))
    }

    @Test
    fun `stored values round-trip`() {
        JokeFrequency.entries.forEach {
            assertEquals(it, JokeFrequency.fromStorage(it.storageValue))
        }
        JokePosition.entries.forEach {
            assertEquals(it, JokePosition.fromStorage(it.storageValue))
        }
        JokeScope.entries.forEach {
            assertEquals(it, JokeScope.fromStorage(it.storageValue))
        }
        JokeDwell.entries.forEach {
            assertEquals(it, JokeDwell.fromStorage(it.storageValue))
        }
        JokeTone.entries.forEach { tone ->
            assertEquals(setOf(tone), JokeTone.fromStorage(setOf(tone.storageValue)))
        }
        JokeMoment.entries.forEach { moment ->
            assertEquals(setOf(moment), JokeMoment.fromStorage(setOf(moment.storageValue)))
        }
    }

    @Test
    fun `the stored tone values are unique`() {
        assertEquals(
            "two tones sharing a storage value would make one of them unreachable " +
                "from Settings",
            JokeTone.entries.size,
            JokeTone.entries.map { it.storageValue }.distinct().size
        )
        assertEquals(
            JokeMoment.entries.size,
            JokeMoment.entries.map { it.storageValue }.distinct().size
        )
    }

    @Test
    fun `the shipped default is a working, quiet toast`() {
        val defaults = JokeSettings()

        assertTrue("jokes should ship on", defaults.enabled)
        assertTrue(defaults.autoJokesEnabled)
        assertEquals(JokeFrequency.NORMAL, defaults.frequency)
        assertEquals(JokePosition.BOTTOM, defaults.position)
        assertEquals(JokeScope.MAIN_PAGES, defaults.scope)
        assertEquals(JokeDwell.STANDARD, defaults.dwell)
        assertTrue(defaults.haptics)
        assertEquals(JokeTone.entries.toSet(), defaults.tones)
        assertTrue(defaults.moments.isEmpty())
    }
}
