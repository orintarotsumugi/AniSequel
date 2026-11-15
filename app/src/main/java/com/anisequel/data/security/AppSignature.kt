package com.anisequel.data.security

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * The certificate this APK is actually signed with.
 *
 * ## Why the app shows this
 *
 * AniSequel is installed from a GitHub release rather than from Play, so Play
 * Protect cannot know the build in advance and offers to scan it. That prompt is
 * the platform being honest, not a verdict, and the app cannot make it disappear.
 * What it *can* do is make the other half checkable: the release page publishes
 * this fingerprint, and a reader who was sent an APK by somebody else can compare
 * the two before trusting it.
 *
 * It is also a diagnostic for the failure that matters: a build signed with a
 * different key cannot install over the previous one. Seeing "debug" here explains
 * an `INSTALL_FAILED_UPDATE_INCOMPATIBLE` that no amount of retrying will fix.
 *
 * The fingerprint is the SHA-256 of the signing certificate, uppercase and
 * colon-separated, which is the form `apksigner verify --print-certs` and the
 * workflow's pinned digest both use.
 */
object AppSignature {

    /** "Unavailable" rather than an exception: this is drawn on an About page. */
    fun sha256(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()
                ?: return "Unavailable"
            MessageDigest.getInstance("SHA-256")
                .digest(signer.toByteArray())
                .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        }.getOrDefault("Unavailable")
    }

    /**
     * A shortened fingerprint for the row, keeping both ends.
     *
     * The first and last groups are enough to compare by eye while still not
     * fitting in a tweet-sized crop that another key could reproduce; the full
     * value is selectable below it.
     */
    fun shortFingerprint(full: String): String {
        if (full == "Unavailable") return full
        val parts = full.split(':')
        if (parts.size <= 6) return full
        return "${parts.take(3).joinToString(":")} … ${parts.takeLast(3).joinToString(":")}"
    }

    /** True for a debug build, which Play Protect treats differently on purpose. */
    fun isDebugBuild(context: Context): Boolean {
        val flags = context.applicationInfo?.flags ?: 0
        return flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
