package com.anisequel.data.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/** What [ExtensionManager.install] tells the screen. */
sealed interface InstallOutcome {
    data class Installed(val message: String) : InstallOutcome
    data class NeedsPermission(val message: String) : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
}

/**
 * The one owner of extension state for the process.
 *
 * It is built once in `AppNavigation`, like the AniList repository and the
 * preferences beside it, so the Extensions page, the Watch page's source sheet
 * and the manager all read the same records. Two managers would mean two
 * catalogue caches and two install maps that could disagree about whether the
 * extension the reader just installed is present.
 */
class ExtensionManager(context: Context) {

    private val appContext = context.applicationContext
    val preferences = ExtensionPreferences(appContext)
    val watchPreferences = WatchPreferences(appContext)
    val playerPreferences = PlayerPreferences(appContext)
    private val installer = ExtensionInstaller(appContext)
    private val repositoryClient = ExtensionRepositoryClient()

    /** Compiled-in sources. This beta ships one; the interface is the seam for more. */
    private val nativeSources: List<NativeAnimeSource> = listOf(ArchiveAnimeSource())

    private val catalogMutex = Mutex()
    @Volatile
    private var catalog: ExtensionCatalog = ExtensionCatalog()

    init {
        // This process is the restart the previous install asked for. Clearing here,
        // before a screen can read the flow, is what turns the banner off exactly
        // once - see `ExtensionPreferences.markProcessRestarted`.
        preferences.markProcessRestarted()
    }

    val repositories get() = preferences.repositories
    val installed get() = preferences.installed
    val restartRequired get() = preferences.restartRequired
    val showNsfw get() = preferences.showNsfw

    /**
     * Reads every repository once, in parallel.
     *
     * A repository that fails contributes a [CatalogFailure] and nothing else:
     * the three that answered must still draw. [force] is the pull-to-refresh
     * path; the cached answer is otherwise reused for the life of the process, so
     * switching tabs does not re-download three indexes.
     */
    suspend fun loadCatalog(force: Boolean = false): ExtensionCatalog {
        if (!force && !catalog.isCold) return catalog
        return catalogMutex.withLock {
            if (!force && !catalog.isCold) return@withLock catalog
            val repos = preferences.repositories.value
            if (repos.isEmpty()) {
                catalog = ExtensionCatalog(loadedAt = System.currentTimeMillis())
                return@withLock catalog
            }
            val results = coroutineScope {
                repos.map { repo ->
                    async {
                        runCatching { repositoryClient.fetch(repo) }
                            .fold(
                                onSuccess = { entries -> repo to entries },
                                onFailure = { error -> repo to error }
                            )
                    }
                }.awaitAll()
            }
            val entries = mutableListOf<CatalogExtension>()
            val errors = mutableListOf<CatalogFailure>()
            for (result in results) {
                when (val value = result.second) {
                    is List<*> -> entries += value.filterIsInstance<CatalogExtension>()
                    is Throwable -> errors += CatalogFailure(
                        repository = result.first,
                        message = value.message ?: "Could not read this repository"
                    )
                }
            }
            catalog = ExtensionCatalog(
                entries = entries,
                errors = errors,
                loadedAt = System.currentTimeMillis()
            )
            catalog
        }
    }

    /** The catalogue rows visible under [showNsfw], newest repository order preserved. */
    fun visibleEntries(showNsfw: Boolean): List<CatalogExtension> {
        val all = catalog.entries
        return if (showNsfw) all else all.filterNot { it.nsfw }
    }

    /**
     * Adds a repository and returns what was found.
     *
     * Discovery is the only place that probes several URLs; the resolved index
     * URL is what is stored, so every later refresh is one request.
     */
    suspend fun addRepository(url: String): RepositoryDiscovery {
        val discovery = repositoryClient.discover(url)
        preferences.addRepository(discovery.repository)
        catalog = ExtensionCatalog()
        return discovery
    }

    fun removeRepository(repositoryId: String) {
        preferences.removeRepository(repositoryId)
        catalog = ExtensionCatalog()
    }

    /**
     * Downloads and installs one entry.
     *
     * APKs go through the platform installer; JS modules are written beside them
     * and marked installed. Neither is executed by this beta. The install is
     * recorded only after the installer was actually launched, so a reader who
     * backs out of Android's confirmation is not told the extension is present.
     */
    suspend fun install(
        extension: CatalogExtension,
        onProgress: (Float) -> Unit = {}
    ): InstallOutcome {
        val url = extension.downloadUrl
            ?: return InstallOutcome.Failed("This repository gave no download address")
        return try {
            val file = installer.download(url, extension.fileName, onProgress)
            val isModule = file.extension.equals("js", ignoreCase = true) ||
                file.extension.equals("jar", ignoreCase = true)
            if (isModule) {
                preferences.recordInstall(extension, file.absolutePath, needsRestart = true)
                return InstallOutcome.Installed(
                    "${extension.name} installed. Restart AniSequel to register it."
                )
            }
            if (!installer.canInstallPackages()) {
                installer.openInstallPermissionSettings()
                return InstallOutcome.NeedsPermission(
                    "Allow AniSequel to install unknown apps, then tap Install again."
                )
            }
            if (!installer.launchInstaller(file)) {
                return InstallOutcome.Failed(
                    "Android's installer did not open. Check the install permission."
                )
            }
            preferences.recordInstall(extension, file.absolutePath, needsRestart = true)
            InstallOutcome.Installed(
                "${extension.name} handed to Android. Restart AniSequel after the install finishes."
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            InstallOutcome.Failed(error.message ?: "The install did not finish")
        }
    }

    /**
     * Installs an APK the reader picked from storage.
     *
     * The Extension Manager's "Load Plugin APK from Storage" is the same install
     * path as a catalogue row, with a synthetic record so the file can be
     * uninstalled and listed afterwards. It deliberately does not claim a
     * repository or a package name it cannot read before the platform installer
     * does.
     */
    suspend fun installFromUri(uri: android.net.Uri, displayName: String): InstallOutcome {
        return try {
            val file = installer.copyFromUri(uri, displayName)
            val isModule = file.extension.equals("js", ignoreCase = true) ||
                file.extension.equals("jar", ignoreCase = true)
            val name = displayName.substringBeforeLast('.').ifBlank { file.name }
            val synthetic = CatalogExtension(
                key = "local:${file.name.lowercase()}",
                repositoryId = "local",
                repositoryUrl = "",
                kind = if (isModule) ExtensionKind.SORA else ExtensionKind.ANIYOMI,
                content = ExtensionContent.ANIME,
                name = name,
                packageName = "",
                version = "local",
                language = "en",
                nsfw = false,
                iconUrl = null,
                downloadUrl = null,
                description = null
            )
            if (isModule) {
                preferences.recordInstall(synthetic, file.absolutePath, needsRestart = true)
                return InstallOutcome.Installed("$name installed. Restart AniSequel to register it.")
            }
            if (!installer.canInstallPackages()) {
                installer.openInstallPermissionSettings()
                return InstallOutcome.NeedsPermission(
                    "Allow AniSequel to install unknown apps, then pick the APK again."
                )
            }
            if (!installer.launchInstaller(file)) {
                return InstallOutcome.Failed("Android's installer did not open.")
            }
            preferences.recordInstall(synthetic, file.absolutePath, needsRestart = true)
            InstallOutcome.Installed(
                "$name handed to Android. Restart AniSequel after the install finishes."
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            InstallOutcome.Failed(error.message ?: "That APK could not be installed")
        }
    }

    /**
     * Removes the record and, for an installed package, asks Android to uninstall
     * it. The system dialog is the actual removal; this app cannot silently
     * uninstall another package, and should not pretend it did.
     */
    fun uninstall(extension: CatalogExtension): String {
        val record = preferences.installed.value[extension.key]
        if (record != null) {
            val packageName = record.packageName
            if (packageName.isNotBlank() && installer.isPackageInstalled(packageName)) {
                val intent = Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:$packageName")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { appContext.startActivity(intent) }
            }
            record.apkPath?.let { path ->
                runCatching { File(path).delete() }
            }
            preferences.removeInstall(extension.key)
            preferences.setRestartRequired(true)
        }
        return "${extension.name} removed. Restart AniSequel to refresh the extension list."
    }

    fun uninstall(record: InstalledExtension) {
        val packageName = record.packageName
        if (packageName.isNotBlank() && installer.isPackageInstalled(packageName)) {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(intent) }
        }
        record.apkPath?.let { path -> runCatching { File(path).delete() } }
        preferences.removeInstall(record.key)
        preferences.setRestartRequired(true)
    }

    fun openInstallPermissionSettings() = installer.openInstallPermissionSettings()

    /** Re-read on resume, because the grant lives in another app's Settings screen. */
    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun restart(): Boolean = installer.restartApp()

    /**
     * Every source the Watch page can show.
     *
     * The native source is playable. Installed third-party extensions are listed
     * with [PlayableSource.playable] false and a note that says why - hiding them
     * would make the manager's "installed" count and the source sheet disagree,
     * which reads as the install having done nothing.
     */
    fun sources(): List<PlayableSource> {
        val native = nativeSources.map { source ->
            PlayableSource(
                id = source.id,
                name = source.name,
                kind = source.kind,
                content = source.content,
                language = source.language,
                nsfw = source.nsfw,
                iconUrl = null,
                playable = true,
                note = source.note
            )
        }
        val installedSources = preferences.installed.value.values
            .filter { it.content == ExtensionContent.ANIME }
            .sortedBy { it.name.lowercase() }
            .map { record ->
                PlayableSource(
                    id = record.key,
                    name = record.name,
                    kind = record.kind,
                    content = record.content,
                    language = "en",
                    nsfw = false,
                    iconUrl = record.iconUrl,
                    playable = false,
                    note = "Installed. The ${record.kind.label} APK runtime is Phase 2."
                )
            }
        return native + installedSources
    }

    /** The built-in source behind [sourceId], or null for an installed APK/module. */
    fun nativeSource(sourceId: String): NativeAnimeSource? =
        nativeSources.firstOrNull { it.id == sourceId }

    suspend fun search(sourceId: String, query: String): List<SourceAnime> {
        val source = nativeSource(sourceId)
            ?: throw IOException("This source runs on the Phase 2 extension runtime.")
        return source.search(query)
    }

    suspend fun details(sourceId: String, animeId: String): SourceDetails {
        val source = nativeSource(sourceId)
            ?: throw IOException("This source runs on the Phase 2 extension runtime.")
        return source.details(animeId)
    }
}
