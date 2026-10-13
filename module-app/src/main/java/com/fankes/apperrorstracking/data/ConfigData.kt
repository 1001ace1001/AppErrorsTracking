/*
 * AppErrorsTracking - Added more features to app's crash dialog, fixed custom rom deleted dialog, the best experience to Android developer.
 * Copyright (C) 2017 Fankes Studio(qzmmcn@163.com)
 * https://github.com/KitsunePie/AppErrorsTracking
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 *
 * This file is created by fankes on 2022/10/1.
 */
@file:Suppress("MemberVisibilityCanBePrivate")

package com.fankes.apperrorstracking.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.fankes.apperrorstracking.utils.tool.AppLog
import com.fankes.apperrorstracking.data.ConfigKey

/**
 * 全局配置存储控制类
 */
object ConfigData {

    /** 显示开发者提示 */
    val SHOW_DEVELOPER_NOTICE = ConfigKey("_show_developer_notice", true)

    /** 启用 Material 3 风格的错误对话框 */
    val ENABLE_MATERIAL3_STYLE_APP_ERRORS_DIALOG = ConfigKey("_enable_material3_style_dialog", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    /** 仅对前台应用显示错误对话框 */
    val ENABLE_ONLY_SHOW_ERRORS_IN_FRONT = ConfigKey("_enable_only_show_errors_in_front", false)

    /** 仅对应用主进程显示错误对话框 */
    val ENABLE_ONLY_SHOW_ERRORS_IN_MAIN = ConfigKey("_enable_only_show_errors_in_main", false)

    /** 错误对话框始终显示“重新打开”选项 */
    val ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS = ConfigKey("_enable_always_shows_reopen_app_options", false)

    /** 启用应用配置模板 */
    val ENABLE_APP_CONFIG_TEMPLATE = ConfigKey("_enable_app_config_template", false)

    /** 启用对话框防误触 */
    val ENABLE_PREVENT_MISOPERATION_FOR_DIALOG = ConfigKey("_enable_prevent_misoperation_for_dialog", false)

    /** 禁止异常堆栈内容自动换行 */
    val DISABLE_AUTO_WRAP_ERROR_STACK_TRACE = ConfigKey("_disable_auto_wrap_error_stack_trace", false)

    /** 分享时使用文件 */
    val SHARE_WITH_FILE = ConfigKey("_share_with_file", false)

    const val PREFERENCE_GROUP = "app_errors_tracking"
    private const val MIGRATED = "_api102_migrated"
    private var preferences: SharedPreferences? = null
    var isFramework = false
        private set
    val isPreferencesAvailable get() = preferences != null
    fun init(context: Context) = initApp(context)
    fun initApp(context: Context) {
        isFramework = false
        val target = context.getSharedPreferences(PREFERENCE_GROUP, Context.MODE_PRIVATE)
        // YukiHookPrefsBridge.currentPrefsName defaults to modulePackageName + "_preferences".
        // Copy missing values only; retain the legacy store intact for rollback.
        if (!target.getBoolean(MIGRATED, false)) {
            val legacy = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            val editor = target.edit()
            legacy.all.forEach { (key, value) ->
                if (!target.contains(key)) putValue(editor, key, value)
            }
            check(editor.putBoolean(MIGRATED, true).commit()) { "Legacy preferences migration failed" }
        }
        preferences = target
        AppErrorsConfigData.refresh()
        PreferenceMirror.start(target)
    }
    fun initFramework(shared: SharedPreferences) {
        isFramework = true
        preferences = shared
        AppErrorsConfigData.refresh()
    }
    private fun store() = preferences ?: error("ConfigData not initialized")
    private fun edit(block: (SharedPreferences.Editor) -> Unit) {
        if (isFramework) { AppLog.warn("Framework preferences are read-only"); return }
        store().edit().also(block).apply()
    }
    internal fun getStringSet(key: String): Set<String> = store().getStringSet(key, emptySet())?.toSet() ?: emptySet()
    internal fun putStringSet(key: String, value: Set<String>) = edit { it.putStringSet(key, value.toSet()) }
    internal fun getInt(data: ConfigKey<Int>) = store().getInt(data.key, data.default)
    internal fun putInt(data: ConfigKey<Int>, value: Int) = edit { it.putInt(data.key, value) }
    internal fun getBoolean(data: ConfigKey<Boolean>) = store().getBoolean(data.key, data.default)
    internal fun putBoolean(data: ConfigKey<Boolean>, value: Boolean) = edit { it.putBoolean(data.key, value) }
    internal fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    /**
     * 是否显示开发者提示
     * @return [Boolean]
     */
    var isShowDeveloperNotice
        get() = getBoolean(SHOW_DEVELOPER_NOTICE)
        set(value) {
            putBoolean(SHOW_DEVELOPER_NOTICE, value)
        }

    /**
     * 是否仅对前台应用显示错误对话框
     * @return [Boolean]
     */
    var isEnableOnlyShowErrorsInFront
        get() = getBoolean(ENABLE_ONLY_SHOW_ERRORS_IN_FRONT)
        set(value) {
            putBoolean(ENABLE_ONLY_SHOW_ERRORS_IN_FRONT, value)
        }

    /**
     * 是否仅对应用主进程显示错误对话框
     * @return [Boolean]
     */
    var isEnableOnlyShowErrorsInMain
        get() = getBoolean(ENABLE_ONLY_SHOW_ERRORS_IN_MAIN)
        set(value) {
            putBoolean(ENABLE_ONLY_SHOW_ERRORS_IN_MAIN, value)
        }

    /**
     * 错误对话框是否始终显示“重新打开”选项
     * @return [Boolean]
     */
    var isEnableAlwaysShowsReopenAppOptions
        get() = getBoolean(ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS)
        set(value) {
            putBoolean(ENABLE_ALWAYS_SHOWS_REOPEN_APP_OPTIONS, value)
        }

    /**
     * 是否启用应用配置模板
     * @return [Boolean]
     */
    var isEnableAppConfigTemplate
        get() = getBoolean(ENABLE_APP_CONFIG_TEMPLATE)
        set(value) {
            putBoolean(ENABLE_APP_CONFIG_TEMPLATE, value)
        }

    /**
     * 是否启用对话框防误触
     * @return [Boolean]
     */
    var isEnablePreventMisoperation
        get() = getBoolean(ENABLE_PREVENT_MISOPERATION_FOR_DIALOG)
        set(value) {
            putBoolean(ENABLE_PREVENT_MISOPERATION_FOR_DIALOG, value)
        }

    /**
     * 是否启用 Material 3 风格的错误对话框
     * @return [Boolean]
     */
    var isEnableMaterial3StyleAppErrorsDialog
        get() = getBoolean(ENABLE_MATERIAL3_STYLE_APP_ERRORS_DIALOG)
        set(value) {
            putBoolean(ENABLE_MATERIAL3_STYLE_APP_ERRORS_DIALOG, value)
        }

    /**
     * 是否以文件方式分享
     */
    var isShareWithFile
        get() = getBoolean(SHARE_WITH_FILE)
        set(value) {
            putBoolean(SHARE_WITH_FILE, value)
        }
}