package com.anisequel.data.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.anisequel.data.extensions.runtime.AniyomiManifestReader
import com.anisequel.data.extensions.runtime.ExtensionRuntimeClient
import com.anisequel.data.extensions.runtime.RuntimeAnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val runtimeBridge = ExtensionRuntimeBridge(appContext)
    private val runtimeClient = ExtensionRuntimeClient(appContext)
    private val manifestReader = AniyomiManifestReader(appContext)
    private val runtimeAdapters = java.util.concurrent.ConcurrentHashMap<String, RuntimeAnimeSource>()

    /** Compiled-in sources. This beta ships one; the interface is the seam for more. */
    private val nativeSources: List<NativeAnimeSource> = listOf(ArchiveAnimeSource())

    private val catalogMutex = Mutex()
    private val catalogCache = ExtensionCatalogCache(appContext)
    @Volatile
    private var catalog: ExtensionCatalog = ExtensionCatalog()

    init {
        ExtensionHttp.installCache(appContext)
        val cachedCatalog = catalogCache.read()
        val repositoryIds = preferences.repositories.value.map { it.id }.toSet()
        catalog = cachedCatalog?.copy(
            // A cache written before a repository was removed must not keep
            // showing rows that can no longer be refreshed or installed from.
            entries = cachedCatalog.entries.filter { it.repositoryId in repositoryIds }
        ) ?: ExtensionCatalog()
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
     * A repository that fails contributes a [CatalogFailure] and keeps that
     * repository's last good rows, if there are any; the three that answered must
     * still draw, and one flaky host must not blank the whole tab. [force] is the
     * refresh path; a catalogue younger than [CATALOG_TTL_MS] is otherwise reused,
     * so switching tabs does not re-download every index.
     */
    suspend fun loadCatalog(force: Boolean = false): ExtensionCatalog {
        if (!force && isCatalogFresh()) return catalog
        return catalogMutex.withLock {
            if (!force && isCatalogFresh()) return@withLock catalog
            val repos = preferences.repositories.value
            if (repos.isEmpty()) {
                catalog = ExtensionCatalog(loadedAt = System.currentTimeMillis())
                catalogCache.clear()
                return@withLock catalog
            }
            val previousByRepository = catalog.entries.groupBy { it.repositoryId }
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
                    is Throwable -> {
                        entries += previousByRepository[result.first.id].orEmpty()
                        errors += CatalogFailure(
                            repository = result.first,
                            message = value.message ?: "Could not read this repository"
                        )
                    }
                }
            }
            catalog = ExtensionCatalog(
                entries = entries.distinctBy { it.key },
                errors = errors,
                loadedAt = System.currentTimeMillis()
            )
            if (catalog.entries.isNotEmpty()) {
                // Serialising 255 rows is file I/O; doing it on the main thread
                // made the first catalogue paint hitch just after the spinner.
                withContext(Dispatchers.IO) { catalogCache.write(catalog) }
            }
            catalog
        }
    }

    private fun isCatalogFresh(): Boolean =
        !catalog.isCold && System.currentTimeMillis() - catalog.loadedAt < CATALOG_TTL_MS

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
        catalogCache.clear()
        return discovery
    }

    fun removeRepository(repositoryId: String) {
        preferences.removeRepository(repositoryId)
        catalog = ExtensionCatalog()
        catalogCache.clear()
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
            val isModule = isModuleFile(file)
            val info = runtimeBridge.inspect(file)
            if (!isModule && info == null) {
                file.delete()
                return InstallOutcome.Failed(
                    "The download is not a readable APK. Nothing was installed."
                )
            }
            if (!isModule && packageMismatch(extension, info)) {
                file.delete()
                return InstallOutcome.Failed(
                    "This APK is ${info?.packageName ?: "another package"}, not " +
                        "${extension.packageName}. Nothing was installed."
                )
            }
            val resolved = extension.copy(
                packageName = info?.packageName?.takeIf { it.isNotBlank() } ?: extension.packageName,
                version = info?.version?.takeIf { it.isNotBlank() } ?: extension.version,
                sourceNames = extension.sourceNames.ifEmpty { info?.sourceNames.orEmpty() }
            )
            if (isModule) {
                preferences.recordInstall(resolved, file.absolutePath, needsRestart = true)
                return InstallOutcome.Installed(
                    "${resolved.name} installed. Restart AniSequel to register it."
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
            preferences.recordInstall(resolved, file.absolutePath, needsRestart = true)
            InstallOutcome.Installed(
                "${resolved.name} handed to Android. Restart AniSequel after the install finishes."
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
            val info = runtimeBridge.inspect(file)
            val isModule = isModuleFile(file)
            if (!isModule && info == null) {
                file.delete()
                return InstallOutcome.Failed(
                    "That file is not a readable APK. Nothing was installed."
                )
            }
            val packageName = info?.packageName.orEmpty()
            val kind = when {
                file.extension.equals("js", ignoreCase = true) -> ExtensionKind.SORA
                file.extension.equals("jar", ignoreCase = true) ||
                    file.extension.equals("cs3", ignoreCase = true) ||
                    file.extension.equals("dex", ignoreCase = true) -> ExtensionKind.CLOUDSTREAM
                packageName.contains("cloudstream", ignoreCase = true) -> ExtensionKind.CLOUDSTREAM
                packageName.contains("lnreader", ignoreCase = true) -> ExtensionKind.LNREADER
                else -> ExtensionKind.ANIYOMI
            }
            val name = info?.label?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.').ifBlank { file.name }
            val synthetic = CatalogExtension(
                key = "local:${file.name.lowercase()}",
                repositoryId = "local",
                repositoryUrl = "",
                kind = kind,
                content = if (kind == ExtensionKind.LNREADER) {
                    ExtensionContent.NOVEL
                } else {
                    ExtensionContent.ANIME
                },
                name = name,
                packageName = packageName,
                version = info?.version?.takeIf { it.isNotBlank() } ?: "local",
                language = "en",
                nsfw = false,
                iconUrl = null,
                downloadUrl = null,
                description = null,
                sourceNames = info?.sourceNames.orEmpty()
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
            runtimeAdapters.remove(extension.key)
            runtimeClient.forget(packageName)
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
        runtimeAdapters.remove(record.key)
        runtimeClient.forget(record.packageName)
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
    fun sources(): List<PlayableSource> =
        runtimeBridge.sources(nativeSources, preferences.installed.value.values, runtimeSources())

    /** Counts for the manager card; the bridge owns the classification. */
    fun runtimeReport(): ExtensionRuntimeReport =
        runtimeBridge.report(nativeSources, preferences.installed.value.values, runtimeSources())

    /** The built-in source behind [sourceId], or null for an installed APK/module. */
    fun nativeSource(sourceId: String): NativeAnimeSource? =
        nativeSources.firstOrNull { it.id == sourceId }

    /**
     * The staged Aniyomi APKs the runtime can load, as ordinary sources.
     *
     * A record becomes a source only when its manifest declares the anime
     * extension feature and a source class - read without executing anything.
     * Adapters are cached per record so a search does not re-read the manifest.
     */
    private fun runtimeSources(): List<NativeAnimeSource> =
        preferences.installed.value.values.mapNotNull { runtimeAdapter(it) }

    private fun runtimeAdapter(record: InstalledExtension): RuntimeAnimeSource? {
        if (record.kind != ExtensionKind.ANIYOMI || record.content != ExtensionContent.ANIME) return null
        val path = record.apkPath ?: return null
        runtimeAdapters[record.key]?.let { return it }
        val manifest = manifestReader.read(File(path)) ?: return null
        return RuntimeAnimeSource(record, manifest, runtimeClient)
            .also { runtimeAdapters[record.key] = it }
    }

    private fun source(sourceId: String): NativeAnimeSource? =
        nativeSource(sourceId) ?: runtimeSources().firstOrNull { it.id == sourceId }

    suspend fun search(sourceId: String, query: String): List<SourceAnime> {
        val source = source(sourceId)
            ?: throw IOException("This source is not available.")
        return source.search(query)
    }

    suspend fun details(sourceId: String, animeId: String): SourceDetails {
        val source = source(sourceId)
            ?: throw IOException("This source is not available.")
        return source.details(animeId)
    }

    /** Resolves an episode's playable files; native sources answer from [details]. */
    suspend fun resolveServers(sourceId: String, animeId: String, episodeId: String): List<SourceServer> {
        val source = source(sourceId)
            ?: throw IOException("This source is not available.")
        return source.resolveServers(animeId, episodeId)
    }

    /**
     * `.cs3` is a DEX module, not an APK: handing it to Android's package
     * installer fails, and treating it as one made every CloudStream row install
     * into a dead end. It is staged like a JS plugin and is not executed in beta.
     */
    private fun isModuleFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("js", "jar", "cs3", "dex")
    }

    private fun packageMismatch(
        extension: CatalogExtension,
        info: ExtensionFileInfo?
    ): Boolean {
        val claimed = extension.packageName.takeIf { it.isNotBlank() } ?: return false
        val actual = info?.packageName.orEmpty()
        return actual.isBlank() || actual != claimed
    }

    private companion object {
        const val CATALOG_TTL_MS = 15 * 60 * 1_000L
    }
}
