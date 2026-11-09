package com.anisequel.data.extensions.runtime

import android.content.SharedPreferences

/**
 * An in-memory [SharedPreferences] for the isolated runtime.
 *
 * The runtime process cannot write the app's files, and an extension's
 * preferences only need to survive the session that loaded it. Persisting them
 * across launches is a later phase; a store that silently dropped writes would
 * be worse than one whose lifetime is stated.
 */
class RuntimeSharedPreferences : SharedPreferences {

    private val values = LinkedHashMap<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = synchronized(values) { LinkedHashMap(values) }
    override fun getString(key: String?, defValue: String?): String? = synchronized(values) {
        (values[key] as? String) ?: defValue
    }
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(values) {
            @Suppress("UNCHECKED_CAST")
            (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
    override fun getInt(key: String?, defValue: Int): Int = synchronized(values) {
        (values[key] as? Int) ?: defValue
    }
    override fun getLong(key: String?, defValue: Long): Long = synchronized(values) {
        (values[key] as? Long) ?: defValue
    }
    override fun getFloat(key: String?, defValue: Float): Float = synchronized(values) {
        (values[key] as? Float) ?: defValue
    }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(values) {
        (values[key] as? Boolean) ?: defValue
    }
    override fun contains(key: String?): Boolean = synchronized(values) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = also { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = also {
            pending[key] = values?.toSet()
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = also { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = also { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = also { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = also { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = also { pending[key] = null }
        override fun clear(): SharedPreferences.Editor = also { clear = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changed = synchronized(values) {
                if (clear) values.clear()
                val keys = pending.keys.toSet()
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                keys
            }
            val snapshot = synchronized(listeners) { listeners.toList() }
            changed.forEach { key -> snapshot.forEach { it.onSharedPreferenceChanged(this@RuntimeSharedPreferences, key) } }
        }
    }
}