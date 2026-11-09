package com.anisequel.data.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

private const val EXTENSION_PREFERENCES_FILE = "anisequel_extensions"
private const val KEY_REPOSITORIES = "repositories_v1"
private const val KEY_INSTALLED = "installed_v1"
private const val KEY_RESTART_REQUIRED = "restart_required"
private const val KEY_SHOW_NSFW = "show_nsfw"

/**
 * The repository the Extensions page starts with.
 *
 * It is Yūzōnō's index, the Aniyomi store Wotaku currently recommends. The old
 * Secozzi seed was removed after its live index became an "Outdated App, switch to
 * Animiru" placeholder: keeping it made half the page a permanent failure card
 * and taught readers that repository errors were normal. Seeds are only the
 * first write; removing one never brings it back on the next launch. No
 * CloudStream repository is seeded because the known one (CloudX) carries only
 * Indonesian movie providers, not anime; the empty CloudStream tab is honest and
 * Add Repo accepts one.
 */
private fun defaultRepositories(): List<ExtensionRepository> = listOf(
    ExtensionRepository(
        id = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
        url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
        kind = ExtensionKind.ANIYOMI,
        content = ExtensionContent.ANIME
    )
)

/**
 * Repository and install state for the extension catalogue.
 *
 * SharedPreferences rather than DataStore, for the same reason
 * [com.anisequel.data.repository.DevicePreferences] uses it: the installer runs
 * from an Activity resume and from a button press, both of which need the
 * answer now, and one JSON string per collection is small enough that a
 * synchronous read is cheaper than a suspend hop. Every write updates the
 * StateFlow before it returns, so the screens that read it never wait for disk.
 *
 * The install record is this app's fact, not PackageManager's - see
 * [InstalledExtension].
 */
class ExtensionPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(EXTENSION_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private val _repositories = MutableStateFlow(readRepositories())
    val repositories: StateFlow<List<ExtensionRepository>> = _repositories.asStateFlow()

    private val _installed = MutableStateFlow(readInstalled())
    val installed: StateFlow<Map<String, InstalledExtension>> = _installed.asStateFlow()

    private val _restartRequired = MutableStateFlow(preferences.getBoolean(KEY_RESTART_REQUIRED, false))
    val restartRequired: StateFlow<Boolean> = _restartRequired.asStateFlow()

    private val _showNsfw = MutableStateFlow(preferences.getBoolean(KEY_SHOW_NSFW, false))
    val showNsfw: StateFlow<Boolean> = _showNsfw.asStateFlow()

    fun addRepository(repository: ExtensionRepository) {
        val next = _repositories.value.filterNot { it.id == repository.id } + repository
        writeRepositories(next)
    }

    fun removeRepository(repositoryId: String) {
        writeRepositories(_repositories.value.filterNot { it.id == repositoryId })
    }

    fun recordInstall(extension: CatalogExtension, apkPath: String?, needsRestart: Boolean) {
        val record = InstalledExtension(
            key = extension.key,
            packageName = extension.packageName,
            name = extension.name,
            version = extension.version,
            kind = extension.kind,
            content = extension.content,
            apkPath = apkPath,
            iconUrl = extension.iconUrl,
            installedAt = System.currentTimeMillis(),
            needsRestart = needsRestart,
            sourceNames = extension.sourceNames
        )
        val next = _installed.value + (extension.key to record)
        writeInstalled(next)
        if (needsRestart) setRestartRequired(true)
    }

    fun removeInstall(key: String) {
        writeInstalled(_installed.value - key)
    }

    fun clearRestartRequired() {
        setRestartRequired(false)
    }

    /**
     * Called once per process start, before any new install can happen.
     *
     * The process that is asking is by definition a process created after the
     * install the banner was about, so the banner has done its job. The records'
     * own `needsRestart` flags are cleared here too, otherwise every Extensions
     * row would keep saying "restart to load" in the app that has already
     * restarted.
     */
    fun markProcessRestarted() {
        if (_restartRequired.value) setRestartRequired(false)
        if (_installed.value.values.none { it.needsRestart }) return
        val next = _installed.value.mapValues { (_, record) -> record.copy(needsRestart = false) }
        writeInstalled(next)
    }

    fun setRestartRequired(required: Boolean) {
        preferences.edit().putBoolean(KEY_RESTART_REQUIRED, required).apply()
        _restartRequired.value = required
    }

    fun setShowNsfw(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_NSFW, show).apply()
        _showNsfw.value = show
    }

    /** The installed record for [extension], or null. */
    fun installOf(extension: CatalogExtension): InstalledExtension? = _installed.value[extension.key]

    private fun writeRepositories(next: List<ExtensionRepository>) {
        preferences.edit().putString(KEY_REPOSITORIES, repositoriesToJson(next)).apply()
        _repositories.value = next
    }

    private fun writeInstalled(next: Map<String, InstalledExtension>) {
        preferences.edit().putString(KEY_INSTALLED, installedToJson(next)).apply()
        _installed.value = next
    }

    private fun readRepositories(): List<ExtensionRepository> {
        if (!preferences.contains(KEY_REPOSITORIES)) return defaultRepositories()
        val raw = preferences.getString(KEY_REPOSITORIES, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                // Readers who installed the beta before this change still have
                // the dead Secozzi index in their preferences. Dropping it on
                // read removes the permanent failure card without asking them to
                // clean up a repository that no longer has any entries.
                if (url.contains("Secozzi/aniyomi-extensions", ignoreCase = true)) continue
                val kind = runCatching {
                    ExtensionKind.valueOf(obj.optString("kind"))
                }.getOrDefault(ExtensionIndex.kindForUrl(url))
                val content = runCatching {
                    ExtensionContent.valueOf(obj.optString("content"))
                }.getOrDefault(ExtensionIndex.contentForUrl(url, kind))
                add(
                    ExtensionRepository(
                        id = obj.optString("id").takeIf { it.isNotBlank() } ?: url,
                        url = url,
                        kind = kind,
                        content = content,
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun readInstalled(): Map<String, InstalledExtension> {
        val raw = preferences.getString(KEY_INSTALLED, null) ?: return emptyMap()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, InstalledExtension>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val key = obj.optString("key").takeIf { it.isNotBlank() } ?: continue
            val kind = runCatching {
                ExtensionKind.valueOf(obj.optString("kind"))
            }.getOrDefault(ExtensionKind.ANIYOMI)
            val content = runCatching {
                ExtensionContent.valueOf(obj.optString("content"))
            }.getOrDefault(ExtensionContent.ANIME)
            out[key] = InstalledExtension(
                key = key,
                packageName = obj.optString("packageName"),
                name = obj.optString("name"),
                version = obj.optString("version"),
                kind = kind,
                content = content,
                apkPath = obj.optString("apkPath").takeIf { it.isNotBlank() },
                iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
                installedAt = obj.optLong("installedAt", System.currentTimeMillis()),
                needsRestart = obj.optBoolean("needsRestart", false),
                sourceNames = obj.optString("sourceNames")
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
            )
        }
        return out
    }

    private fun repositoriesToJson(repositories: List<ExtensionRepository>): String {
        val array = JSONArray()
        repositories.forEach { repository ->
            array.put(
                JSONObject()
                    .put("id", repository.id)
                    .put("url", repository.url)
                    .put("kind", repository.kind.name)
                    .put("content", repository.content.name)
                    .put("addedAt", repository.addedAt)
            )
        }
        return array.toString()
    }

    private fun installedToJson(installed: Map<String, InstalledExtension>): String {
        val array = JSONArray()
        installed.values.forEach { record ->
            array.put(
                JSONObject()
                    .put("key", record.key)
                    .put("packageName", record.packageName)
                    .put("name", record.name)
                    .put("version", record.version)
                    .put("kind", record.kind.name)
                    .put("content", record.content.name)
                    .put("apkPath", record.apkPath)
                    .put("iconUrl", record.iconUrl)
                    .put("installedAt", record.installedAt)
                    .put("needsRestart", record.needsRestart)
                    .put("sourceNames", record.sourceNames.joinToString("\n"))
            )
        }
        return array.toString()
    }
}
