package com.anisequel.data.backup

/** The parts of app storage a reader can choose to include in a backup. */
enum class BackupContent(val storageKey: String, val displayName: String) {
    SETTINGS("settings", "Settings and local data"),
    OFFLINE("offline", "Saved profile and list snapshots"),
    ARTWORK("artwork", "Saved cover artwork")
}

/** Which parts of a backup to write, or which parts of one to restore. */
data class BackupSelection(
    val settings: Boolean = true,
    val offline: Boolean = true,
    val artwork: Boolean = false
) {
    fun includes(content: BackupContent): Boolean = when (content) {
        BackupContent.SETTINGS -> settings
        BackupContent.OFFLINE -> offline
        BackupContent.ARTWORK -> artwork
    }

    fun contents(): Set<BackupContent> = BackupContent.entries.filterTo(linkedSetOf())
        { includes(it) }

    fun isEmpty(): Boolean = !settings && !offline && !artwork
}

/**
 * What an AniSequel backup zip says about itself.
 *
 * The manifest is deliberately plain text: it is the first entry in the archive,
 * it has to survive being read by a build that predates a future field, and a
 * malformed manifest must be rejected before anything is written to app storage.
 * A version mismatch is allowed - the archive format is stable - but the package
 * name is not: importing another app's zip would otherwise look like a restore
 * and silently leave the reader with settings this app never wrote.
 */
data class BackupManifest(
    val schema: Int,
    val packageName: String,
    val appVersion: String,
    val createdAtMillis: Long,
    val contents: Set<BackupContent>
) {
    fun encode(): String = buildString {
        appendLine("schema=$schema")
        appendLine("package=$packageName")
        appendLine("version=$appVersion")
        appendLine("created=$createdAtMillis")
        appendLine("contents=${contents.joinToString(",") { it.storageKey }}")
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        const val ENTRY_NAME = "anisequel-backup.txt"

        fun parse(text: String): BackupManifest? {
            val values = text.lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null
                    else line.substring(0, separator) to line.substring(separator + 1)
                }
                .toMap()
            val schema = values["schema"]?.toIntOrNull() ?: return null
            if (schema <= 0 || schema > CURRENT_SCHEMA) return null
            val packageName = values["package"]?.takeIf { it.isNotBlank() } ?: return null
            val version = values["version"]?.takeIf { it.isNotBlank() } ?: return null
            val created = values["created"]?.toLongOrNull() ?: return null
            val contents = values["contents"]
                ?.split(',')
                ?.mapNotNull(String::trim)
                ?.mapNotNull { key -> BackupContent.entries.firstOrNull { it.storageKey == key } }
                ?.toSet()
                ?: return null
            return BackupManifest(schema, packageName, version, created, contents)
        }
    }
}
