package com.anisequel.ui.components.update

import com.anisequel.data.update.UpdateManifest
import java.io.File

/** What the updater is doing, for the UI to render. */
sealed interface UpdateUiState {
    /** Nothing has happened yet - the state a freshly opened tab sits in. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class UpToDate(val installedVersion: String) : UpdateUiState

    data class Available(val manifest: UpdateManifest) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val bytesRead: Long,
        val totalBytes: Long,
        /**
         * How fast the last couple of seconds went, or null before there is
         * enough of a reading to say.
         *
         * Null rather than zero: "not yet known" and "not moving" are different
         * things to show somebody waiting on a download, and the first one is
         * true for the first second of every transfer.
         */
        val bytesPerSecond: Long? = null
    ) : UpdateUiState {
        /** 0f..1f, or null while the total is still unknown. */
        val progress: Float?
            get() = if (totalBytes > 0) {
                (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(
        val file: File,
        val manifest: UpdateManifest,
        /**
         * Why the last attempt to open the installer did not work, if it did
         * not.
         *
         * The state stays [ReadyToInstall] rather than becoming
         * [UpdateUiState.Failed] on purpose. The APK is downloaded, verified and
         * still on disk; the one thing that failed was handing it to the system
         * installer, and that is worth another press rather than a trip back
         * through "Check again". A `Failed` here also dropped the file from the
         * state entirely, so the UI had nothing left to retry with - the error
         * had thrown away the thing the user had already waited for.
         */
        val notice: String? = null
    ) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState

    /**
     * The APK is downloaded but the app is not allowed to open the installer.
     *
     * A distinct state rather than an error, because the user has not done
     * anything wrong and the fix is one tap into Settings.
     *
     * [sentToSettings] records that the Settings hand-off has already been
     * offered for *this* download. It is what stops the row re-offering the
     * same button every time the user comes back without having granted it, and
     * what lets the copy drop from an instruction to a plain reminder.
     *
     * [notice] is the same failure message [ReadyToInstall] carries, for the
     * same reason: the file is still here and still installable.
     */
    data class NeedsInstallPermission(
        val file: File,
        val manifest: UpdateManifest,
        val sentToSettings: Boolean = false,
        val notice: String? = null
    ) : UpdateUiState
}