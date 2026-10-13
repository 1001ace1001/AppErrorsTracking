package com.fankes.apperrorstracking.data

import android.content.SharedPreferences
import com.fankes.apperrorstracking.utils.tool.AppLog
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** Private app preferences are authoritative; each framework receives a complete mirror. */
internal object PreferenceMirror {
    private val services = LinkedHashSet<XposedService>()
    private var source: SharedPreferences? = null
    private var registered = false
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        synchronized(this) { services.toList() }.forEach(::mirror)
    }
    // The helper keeps a weak reference. Retain exactly one listener for the process lifetime.
    private val listener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            synchronized(this@PreferenceMirror) { services.add(service) }
            mirror(service)
        }
        override fun onServiceDied(service: XposedService) {
            synchronized(this@PreferenceMirror) { services.remove(service) }
        }
    }
    @Synchronized fun start(preferences: SharedPreferences) {
        if (source !== preferences) {
            source?.unregisterOnSharedPreferenceChangeListener(changed)
            source = preferences
            preferences.registerOnSharedPreferenceChangeListener(changed)
        }
        if (!registered) {
            registered = true
            XposedServiceHelper.registerListener(listener)
        }
        services.toList().forEach(::mirror)
    }
    private fun mirror(service: XposedService) {
        runCatching {
            val values = source?.all ?: return
            val editor = service.getRemotePreferences(ConfigData.PREFERENCE_GROUP).edit().clear()
            values.forEach { (key, value) ->
                if (key != "_api102_migrated") ConfigData.putValue(editor, key, value)
            }
            check(editor.commit()) { "Remote preferences commit failed" }
        }.onFailure { AppLog.warn("Could not mirror preferences to framework", it) }
    }
}