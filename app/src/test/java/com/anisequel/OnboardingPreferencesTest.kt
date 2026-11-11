package com.anisequel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anisequel.data.repository.OnboardingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first-run prompt must stay dismissed across app launches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingPreferencesTest {
    private fun preferences() =
        OnboardingPreferences(ApplicationProvider.getApplicationContext<Context>())

    @Before
    fun reset() = runTest {
        preferences().setCompleted(false)
    }

    @Test
    fun `setup is incomplete on a fresh install`() = runTest {
        assertFalse(preferences().completed.first())
    }

    @Test
    fun `completed setup is persisted`() = runTest {
        preferences().setCompleted(true)
        assertTrue(preferences().completed.first())
    }
}
