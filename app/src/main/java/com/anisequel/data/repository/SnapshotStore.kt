package com.anisequel.data.repository

import android.content.Context
import com.anisequel.BuildConfig
import com.anisequel.data.network.NetworkClient
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One entry of a snapshot file: what was stored, and when.
 *
 * The payload is the value's own JSON as a string rather than a nested object.
 * That costs one extra parse on read and a little escaping on write, and buys a
 * file whose shape cannot depend on the shape of any model: a stored snapshot
 * either parses into today's model or it is a miss, and the file format itself
 * never needs changing when a model does.
 */
@JsonClass(generateAdapter = true)
internal data class SnapshotEntry(
    val storedAtMillis: Long,
    val payload: String
)

/** One kind's whole file: the format version, the release that wrote it, the entries. */
@JsonClass(generateAdapter = true)
internal data class SnapshotFile(
    val schema: Int,
    val formatTag: String,
    val entries: Map<String, SnapshotEntry>
)

/**
 * The disk tier under the repository's in-memory caches.
 *
 * ## The failure this fixes
 *
 * Every cache in [AniListRepositoryImpl] used to live for exactly one process.
 * Kill the app - or let Android do it while the user is in the browser signing
 * in - and the next launch knew nothing: the whole list was re-bought from
 * AniList (one request per chunk of it), the viewer's own profile had to be
 * fetched before the dashboard could even name the account, and every title
 * whose synopsis, cast, studios and release year had already been read was read
 * again. None of that had changed in the ten seconds the app was gone.
 *
 * ## What is stored, and for how long
 *
 * The caller decides both: the `kind` picks the file, and the `ttlMillis` passed
 * to [read] picks the window. A value that comes back off disk is returned with
 * the moment it was *written*, so it is exactly as old as it was when the
 * process died - reading a snapshot never extends its own life.
 *
 * ## The rules that keep a cache from becoming a bug
 *
 * - **A file is never trusted.** A parse failure, a payload today's models
 *   cannot read, a schema or app-version mismatch: each is a miss and the file
 *   is deleted. A corrupt cache must never be the reason a screen fails to
 *   open, and it must not be re-attempted on every open either.
 * - **A write is atomic.** The file is written beside itself and renamed over
 *   the old one, so a process killed mid-write leaves either the old file or the
 *   new one - never half of either.
 * - **The cache is bounded.** Each kind may hold [MAX_BYTES_PER_KIND] of JSON;
 *   past that, the oldest entries are dropped first. The newest entry is never
 *   dropped, even when it alone is over the budget: discarding what was just
 *   written would make the store a no-op for exactly the payloads too big to
 *   fetch casually.
 * - **The app version is part of the key.** A snapshot written by the previous
 *   release can parse into today's models and still be missing a field the new
 *   release's query asks for - so an update drops the whole cache rather than
 *   trusting it. The cost is one re-fetch; the alternative is a title page that
 *   is quietly incomplete until its window runs out and nobody can tell why.
 */
class SnapshotStore(
    private val directory: File,
    private val moshi: Moshi,
    /**
     * The release that wrote these files, as part of the file's identity.
     *
     * `BuildConfig` by default; injectable so a test can prove that a snapshot
     * from another release is a miss rather than a stale hit.
     */
    private val formatTag: String = BuildConfig.VERSION_CODE.toString(),
    /** Injectable so a test can move the clock without sleeping. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxBytesPerKind: Long = MAX_BYTES_PER_KIND
) {

    /** A value read back from disk, with the moment it was written. */
    data class Stored<T>(val value: T, val storedAtMillis: Long)

    private val lock = Mutex()

    /** One parsed file per kind, loaded on first touch and kept for the session. */
    private val kinds = mutableMapOf<String, MutableMap<String, SnapshotEntry>>()

    private val fileAdapter = moshi.adapter(SnapshotFile::class.java)

    /**
     * The value stored under [key], or null when there is none, when it is older
     * than [ttlMillis], or when it cannot be read back into [type].
     *
     * A `ttlMillis` of `Long.MAX_VALUE` is the repository's "manual only" choice:
     * an interval no elapsed clock reaches, so it needs no special case here.
     */
    suspend fun <T : Any> read(
        kind: String,
        key: String,
        type: Class<T>,
        ttlMillis: Long
    ): Stored<T>? = lock.withLock {
        val entry = entries(kind)[key] ?: return@withLock null
        if (nowMillis() - entry.storedAtMillis > ttlMillis) {
            drop(kind, key)
            return@withLock null
        }
        // Parsed off the main thread. A completed list is multi-megabyte JSON,
        // and this read is on the cold-start path: decoding it inline showed up
        // as first-frame jank in exactly the launch the disk tier exists to make
        // fast. The mutex is already suspend, so the context switch is free here.
        val value = withContext(Dispatchers.IO) {
            runCatching { moshi.adapter(type).fromJson(entry.payload) }.getOrNull()
        }
        if (value == null) {
            // Distinct from expiry: these bytes will never parse, so leaving
            // them would buy a file read on every open to answer the same "no".
            drop(kind, key)
            return@withLock null
        }
        Stored(value, entry.storedAtMillis)
    }

    /**
     * Stores [value] under [key] and writes the file.
     *
     * [storedAtMillis] is passed by the caller when the same moment is already
     * being recorded elsewhere - the repository stamps its memory cache and the
     * disk copy with one clock reading, so the two can never disagree about the
     * age of the same answer.
     */
    suspend fun <T : Any> write(
        kind: String,
        key: String,
        type: Class<T>,
        value: T,
        storedAtMillis: Long = nowMillis()
    ) {
        lock.withLock {
            // Serialised on the IO dispatcher for the same reason the read is
            // parsed there: a list snapshot is megabytes, and encoding it on the
            // caller's thread is jank in the middle of a refresh.
            val payload = withContext(Dispatchers.IO) { moshi.adapter(type).toJson(value) }
            entries(kind)[key] = SnapshotEntry(storedAtMillis, payload)
            persist(kind)
        }
    }

    /**
     * One file rewrite for many entries.
     *
     * Covers arrive a dozen at a time for one bio; storing them one by one would
     * re-serialize and re-write the whole kind a dozen times for one screen.
     */
    suspend fun <T : Any> writeAll(
        kind: String,
        values: Map<String, T>,
        type: Class<T>,
        storedAtMillis: Long = nowMillis()
    ) {
        if (values.isEmpty()) return
        lock.withLock {
            val target = entries(kind)
            // One context switch for the whole batch, and off the caller's
            // thread - see [write].
            val encoded = withContext(Dispatchers.IO) {
                val adapter = moshi.adapter(type)
                values.mapValues { (_, value) -> adapter.toJson(value) }
            }
            encoded.forEach { (key, payload) ->
                target[key] = SnapshotEntry(storedAtMillis, payload)
            }
            persist(kind)
        }
    }

    /**
     * Drops one kind entirely - memory and disk.
     *
     * This is what a mutation calls: a changed list must not leave the
     * pre-mutation copy on disk, or the next lookup would resurrect exactly the
     * stale list the mutation was supposed to invalidate.
     */
    suspend fun removeKind(kind: String) {
        lock.withLock {
            kinds.remove(kind)
            withContext(Dispatchers.IO) { fileFor(kind).delete() }
        }
    }

    /** Every kind, memory and disk. Sign-out calls this. */
    suspend fun clear() {
        lock.withLock {
            kinds.clear()
            withContext(Dispatchers.IO) { directory.listFiles()?.forEach { it.delete() } }
        }
    }

    private suspend fun drop(kind: String, key: String) {
        entries(kind).remove(key)
        persist(kind)
    }

    private suspend fun entries(kind: String): MutableMap<String, SnapshotEntry> =
        kinds.getOrPut(kind) { withContext(Dispatchers.IO) { load(kind) }.toMutableMap() }

    private suspend fun persist(kind: String) {
        val stored = kinds[kind] ?: return
        evictToFit(stored)
        val text = fileAdapter.toJson(SnapshotFile(SCHEMA, formatTag, stored))
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val file = fileFor(kind)
            val temp = File(directory, "${file.name}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                // A rename inside one directory does not fail on a healthy
                // filesystem; if it somehow does, a copy is still correct and
                // the next write replaces it.
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    private fun evictToFit(entries: MutableMap<String, SnapshotEntry>) {
        var total = entries.entries.sumOf { sizeOf(it.key, it.value) }
        if (total <= maxBytesPerKind) return

        // Oldest first, and never the newest: dropping what was just written
        // would turn "this payload is too big to keep" into "this payload is
        // never kept", which is the one case the store exists for.
        val evictable = entries.entries
            .sortedBy { it.value.storedAtMillis }
            .dropLast(1)
        for (entry in evictable) {
            if (total <= maxBytesPerKind) break
            entries.remove(entry.key)
            total -= sizeOf(entry.key, entry.value)
        }
    }

    private fun sizeOf(key: String, entry: SnapshotEntry): Long =
        key.length + entry.payload.length + ENTRY_OVERHEAD_BYTES

    private fun load(kind: String): Map<String, SnapshotEntry> {
        val file = fileFor(kind)
        if (!file.isFile) return emptyMap()

        val parsed = runCatching { fileAdapter.fromJson(file.readText()) }.getOrNull()
        if (parsed == null || parsed.schema != SCHEMA || parsed.formatTag != formatTag) {
            // A file this build cannot read is a miss, and it is deleted so the
            // next launch does not pay for the same parse to reach the same
            // answer. `formatTag` covers the app-update case above; `schema`
            // covers a change to the file layout itself.
            file.delete()
            return emptyMap()
        }
        return parsed.entries
    }

    private fun fileFor(kind: String): File = File(directory, "$kind.json")

    private companion object {
        /** Bumped when the *file layout* changes. Not the app version - that is `formatTag`. */
        const val SCHEMA = 1

        /**
         * Approximate ceiling on one kind's JSON on disk.
         *
         * A completed list is the only payload measured in megabytes; a detail
         * node is a few tens of kilobytes and a cover a few hundred bytes. Four
         * megabytes holds the signed-in account's own lists - written most
         * often, so always the newest - with a couple of scanned profiles or a
         * hundred opened titles behind them.
         */
        const val MAX_BYTES_PER_KIND = 4L * 1024 * 1024

        /** An entry's own JSON around the payload: braces, its key, its timestamp. */
        const val ENTRY_OVERHEAD_BYTES = 48L
    }
}

/**
 * The one [SnapshotStore] for this process.
 *
 * ## Why a singleton and not one per repository
 *
 * There are two repositories in the running app - the auth ViewModel's, built in
 * `MainActivity` so a pasted token can be checked against AniList, and the
 * dashboards', built in `AppNavigation` - and each keeps a parsed copy of these
 * files for as long as it lives.
 *
 * Two stores would mean two copies of the same file, and the copy that did not
 * just clear would happily write the cleared entries back on its next write:
 * sign out, and the other store's in-memory map still holds the signed-out
 * person's list, which it re-persists the moment anything else is written into
 * that kind. One store also means one [SnapshotStore.read]/[SnapshotStore.write]
 * lock instead of two racing over the same file.
 *
 * Holds a `File` derived from the application context, never the context itself,
 * so it cannot leak an Activity.
 */
object AniListSnapshots {

    @Volatile
    private var instance: SnapshotStore? = null

    fun of(context: Context): SnapshotStore =
        instance ?: synchronized(this) {
            instance ?: SnapshotStore(
                directory = File(context.applicationContext.cacheDir, SNAPSHOT_DIRECTORY),
                moshi = NetworkClient.json
            ).also { instance = it }
        }

    private const val SNAPSHOT_DIRECTORY = "anilist_snapshots"
}
