package com.anisequel.ui.components.detail

/**
 * A theme write, where a failure is cosmetic rather than a screen.
 *
 * Opening a title writes the cover's colour into the theme preferences, and
 * closing it schedules the revert. Both are `DataStore` round trips inside a
 * coroutine the sheet owns - and a `DataStore` write answers a full disk or a
 * corrupted file by *throwing*. An uncaught throw there does not fail the colour;
 * it kills the coroutine, reaches the thread's handler and closes the app, which
 * was the reported force-stop on opening a title. The accent is not worth that:
 * a colour that could not be saved leaves the app looking like it did before the
 * sheet opened, which is a quiet, survivable outcome.
 *
 * Cancellation is rethrown: it means the sheet is gone, not that the write
 * failed.
 */
internal suspend fun themeWrite(block: suspend () -> Unit) {
    try {
        block()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Deliberately swallowed: see above.
    }
}
