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
 * This file is created by fankes on 2022/5/12.
 */
package com.fankes.apperrorstracking.hook.entity
import android.content.Context
import android.content.res.Resources
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import com.fankes.apperrorstracking.utils.tool.AppLog
import com.fankes.apperrorstracking.data.ConfigData
import com.fankes.apperrorstracking.wrapper.BuildConfigWrapper
object FrameworkRuntime {
    lateinit var module: XposedModule
    lateinit var loader: ClassLoader
    var context: Context? = null
    lateinit var resources: Resources
    private val started=AtomicBoolean()
    fun initialize(c: Context) {
        if(!started.compareAndSet(false,true)) return
        try { resources=c.createPackageContext(BuildConfigWrapper.APPLICATION_ID,0).resources; ConfigData.initFramework(remotePreferencesOrDefaults()); FrameworkHooker.initialize(c); context=c }
        catch(t: Throwable) { context=null; started.set(false); AppLog.error("System framework initialization failed",t) }
    }
    private fun remotePreferencesOrDefaults(): android.content.SharedPreferences {
        return runCatching {
            check(module.frameworkProperties and XposedInterface.PROP_CAP_REMOTE != 0L) { "Framework has no remote preferences capability" }
            module.getRemotePreferences("app_errors_tracking")
        }.getOrElse { AppLog.warn("Remote preferences unavailable, using safe defaults",it); EmptyPreferences }
    }
    private object EmptyPreferences: android.content.SharedPreferences {
        override fun getAll(): MutableMap<String,*> = mutableMapOf<String,Any>()
        override fun getString(key: String?,defValue: String?): String? = defValue
        override fun getStringSet(key: String?,defValues: MutableSet<String>?): MutableSet<String>? = defValues?.toMutableSet()
        override fun getInt(key: String?,defValue: Int)=defValue
        override fun getLong(key: String?,defValue: Long)=defValue
        override fun getFloat(key: String?,defValue: Float)=defValue
        override fun getBoolean(key: String?,defValue: Boolean)=defValue
        override fun contains(key: String?)=false
        override fun edit(): android.content.SharedPreferences.Editor = throw UnsupportedOperationException("Framework preferences are read-only")
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
    @Suppress("UNCHECKED_CAST")
    fun clazz(name: String): Class<Any> = Class.forName(name,false,loader) as Class<Any>
    fun lazyClass(name: String) = lazy { clazz(name) }
    fun lazyClassOrNull(vararg names: String) = lazy { names.firstNotNullOfOrNull { runCatching { clazz(it) }.getOrNull() } }
    fun field(instance: Any?,name: String): Any? { if(instance==null) return null; var type: Class<*>?=instance.javaClass; while(type!=null) { val f=runCatching { type!!.getDeclaredField(name) }.getOrNull(); if(f!=null) return f.apply { setAccessible(true) }.get(instance); type=type.superclass }; return null }
    fun methods(type: Class<*>?,name: String) = type?.declaredMethods?.filter { it.name==name } ?: emptyList()
    fun hook(e: Executable,before: Boolean=false,callback: (HookParam)->Unit) {
        module.hook(e).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain -> execute(chain,before,callback) }
    }
    internal fun execute(chain: XposedInterface.Chain,before: Boolean,cb: (HookParam)->Unit): Any? {
        val p=HookParam(chain)
        if(before) { val saved=p.args.copyOf(); try { cb(p) } catch(t: Throwable) { runCatching { AppLog.error("Before hook failed",t) }; return chain.proceed(saved) }; return if(p.early) p.finish() else chain.proceed(p.args) }
        try { p.value=chain.proceed(p.args) } catch(t: Throwable) { p.failure=t }
        val value=p.value; val failure=p.failure
        try { cb(p) } catch(t: Throwable) { runCatching { AppLog.error("After hook failed",t) }; p.value=value; p.failure=failure }
        return p.finish()
    }
}
class HookParam(chain: XposedInterface.Chain) {
    val instance=chain.thisObject
    val args: Array<Any?> = chain.args.toTypedArray()
    internal var value: Any?=null
    internal var failure: Throwable?=null
    internal var early=false
    var result: Any?
        get()=value
        set(v) { value=v; failure=null; early=true }
    internal fun finish(): Any? { failure?.let { throw it }; return value }
}