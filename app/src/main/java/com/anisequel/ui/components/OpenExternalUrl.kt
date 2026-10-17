package com.anisequel.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Hands a URL to whatever app the user has for it, and says whether it went.
 *
 * Moved out of `ui/screens/settings/SettingsComponents.kt` when the profile screen
 * needed it too. It is a `Context`-and-`String` helper with nothing to do with
 * settings, and a profile that opens its favourites on AniList had two choices:
 * import a function named out of a settings package, or copy it. Copying is how two
 * implementations of "start an ACTION_VIEW intent and hope" end up disagreeing about
 * the `NEW_TASK` flag, which is the one part of it that matters when the launching
 * context is not an Activity.
 *
 * ## Why it returns a `Boolean` now
 *
 * It used to be `Unit`, and every one of its eighteen call sites discarded whatever it
 * produced - which is what made the failure invisible. A `Uri.parse` of a malformed
 * link, a scheme no app handles, or a device with no browser for `https` all threw an
 * `ActivityNotFoundException` into a `runCatching` that threw it away, so tapping a
 * link in a person's bio did *nothing at all*: no browser, no error, no hint. The
 * reader has no way to tell a link the app failed to open from one that is simply
 * broken on AniList.
 *
 * Returning the outcome is source-compatible with every existing call, because Kotlin
 * lets a value-returning function be called as a statement and coerces the last
 * expression of a lambda to `Unit`. Nothing had to change to start being able to know.
 *
 * Callers that want to react should use [openExternalUrlOrCopy]; a caller that
 * genuinely does not care can keep discarding the result.
 */
fun openExternalUrl(context: Context, url: String): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}.isSuccess

/**
 * Opens a URL, and if nothing on the device can open it, puts it on the clipboard and
 * says so.
 *
 * ## Why a dead link should still leave the reader with the URL
 *
 * The links in a bio are the one place in this app where the content is authored by
 * somebody else and the app cannot vouch for the target. A dozen of them are routine;
 * the ones that fail are the odd protocol links, the `steam://` and `spotify:` URLs
 * with no app installed, and the bare `anilist.co/anime/one-piece` slugs. In every one
 * of those cases the reader still wants the address - to paste into a browser, or to
 * send to themselves.
 *
 * So the failure path is not an error screen: it is the URL in the clipboard and a
 * message saying where it went. That is strictly more useful than the tap doing
 * nothing, and it costs one clipboard write.
 *
 * [ClipboardManager] through `as?` rather than a cast, because a clipboard is a
 * service a device could in principle not publish; returning false rather than
 * crashing leaves the caller exactly as stuck as it was, which is the honest outcome.
 */
fun openExternalUrlOrCopy(context: Context, url: String): Boolean {
    if (openExternalUrl(context, url)) return true

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Link", url))
    Toast.makeText(
        context,
        "Nothing here can open that link, so it is copied instead",
        Toast.LENGTH_SHORT
    ).show()
    return true
}