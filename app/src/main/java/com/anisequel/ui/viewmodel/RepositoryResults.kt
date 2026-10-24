package com.anisequel.ui.viewmodel

import kotlinx.coroutines.CancellationException

/**
 * A repository call, with an exception turned back into the `Result` its type
 * already promises.
 *
 * The repository is written to classify its own failures - `execute` catches
 * everything from a socket timeout to a Moshi error and answers with
 * `Result.failure` - so in theory every call site can `fold` without a guard.
 * The theory is what failed: a store that only ever handles the `Result` half
 * puts the *other* half, anything thrown on the way to it, into a `launch` on
 * `viewModelScope`. A supervisor job does not contain an uncaught exception; it
 * hands it to the thread's handler, and Android's handler closes the app.
 *
 * That is the shape of the reported force-stop when a comment thread or a title
 * page opened: the screen was drawing a state the coroutine never got far
 * enough to write. A failure here is an ordinary failure the screen already
 * knows how to show - "Couldn't load comments", the summary it already has -
 * so it is returned rather than thrown.
 *
 * [CancellationException] is rethrown because it is not a failure: it is the
 * scope being torn down, and swallowing it would keep a coroutine alive after
 * the screen that owns it is gone.
 */
internal suspend fun <T> repositoryResult(block: suspend () -> Result<T>): Result<T> =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unexpected: Exception) {
        Result.failure(unexpected)
    }
