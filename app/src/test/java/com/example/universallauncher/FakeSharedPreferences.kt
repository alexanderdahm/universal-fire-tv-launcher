package com.example.universallauncher

import android.content.SharedPreferences

/**
 * In memory [SharedPreferences], written by hand so the settings tests need no
 * mocking framework and no Robolectric. `apply()` writes through immediately,
 * which is what the production code relies on.
 */
class FakeSharedPreferences : SharedPreferences {

    private val values = LinkedHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (values[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {

        private val pending = LinkedHashMap<String, Any?>()
        private val removed = LinkedHashSet<String>()
        private var cleared = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun putStringSet(key: String, value: MutableSet<String>?) =
            apply { pending[key] = value }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }

        override fun remove(key: String) = apply { removed += key }

        override fun clear() = apply { cleared = true }

        override fun commit(): Boolean {
            if (cleared) values.clear()
            removed.forEach { values.remove(it) }
            pending.forEach { (key, value) -> values[key] = value }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
