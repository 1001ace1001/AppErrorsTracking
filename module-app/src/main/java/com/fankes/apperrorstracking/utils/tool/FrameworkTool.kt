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
package com.fankes.apperrorstracking.utils.tool

import android.content.*
import android.os.*
import android.content.pm.PackageManager
import com.fankes.apperrorstracking.R
import com.fankes.apperrorstracking.utils.factory.execShell
import com.fankes.apperrorstracking.utils.factory.isRootAccess
import com.fankes.apperrorstracking.utils.factory.showDialog
import com.fankes.apperrorstracking.bean.*
import com.fankes.apperrorstracking.wrapper.BuildConfigWrapper
import com.google.gson.Gson
import java.util.concurrent.Executors

/** Signature-gated discovery; all data travels through authenticated Binder calls. */
object FrameworkTool {
    private val app = BuildConfigWrapper.APPLICATION_ID
    private val permission = "$app.FRAMEWORK_ACCESS"
    private val action = "$app.FRAMEWORK_BINDER"
    private val descriptor = "$app.FrameworkRpc.v1"
    private val gson = Gson()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var remote: IBinder? = null
    @Volatile private var active = false
    val isModuleActive get() = active && remote?.isBinderAlive == true
    val executorName = "libxposed API 102"
    val executorVersion = "102"
    private val handlers = mutableMapOf<String, (Bundle) -> Any?>()
    private data class Snapshot(val uid: Int,val expires: Long,val values: List<String>,val count: Int)
    private val snapshots=LinkedHashMap<String,Snapshot>()
    private class LimitedWriter: java.io.Writer() {
        private val text=StringBuilder()
        override fun write(chars: CharArray,offset: Int,length: Int) { require(text.length+length <= 1048576) { "RPC entry exceeds size limit" }; text.append(chars,offset,length) }
        override fun flush() {}
        override fun close() {}
        override fun toString()=text.toString()
    }
    private fun boundedJson(value: Any?): String { val writer=LimitedWriter(); gson.toJson(value,writer); return writer.toString() }
    private fun snapshotResponse(uid: Int,request: Bundle): Bundle = synchronized(snapshots) {
        val now=SystemClock.elapsedRealtime()
        snapshots.entries.removeAll { it.value.expires<=now }
        val mode=request.getString("mode") ?: "begin"
        if(mode=="release") { val token=request.getString("token"); snapshots[token]?.let { require(it.uid==uid); snapshots.remove(token) }; return@synchronized Bundle() }
        val token: String
        val snapshot: Snapshot
        if(mode=="begin") {
            val op=requireNotNull(request.getString("op"))
            val handler=handlers[op] ?: throw IllegalArgumentException("Unknown framework operation: $op")
            require(snapshots.size<8 && snapshots.values.count { it.uid==uid }<4) { "Too many diagnostic snapshots" }
            val value=handler(request)
            val list=value as? List<*>
            require((list?.size ?: 1)<=10000) { "Diagnostic list exceeds limit" }
            var budget=snapshots.values.sumOf { it.values.sumOf { text -> text.length } }
            val values=(list ?: listOf(value)).map { item -> val json=boundedJson(item); budget+=json.length; require(budget<=4194304) { "Diagnostic snapshot exceeds memory budget" }; json }
            token=java.util.UUID.randomUUID().toString()
            snapshot=Snapshot(uid,now+30000,values,list?.size ?: -1)
            snapshots[token]=snapshot
        } else {
            require(mode=="read") { "Unknown snapshot mode" }
            token=requireNotNull(request.getString("token"))
            snapshot=snapshots[token] ?: throw IllegalStateException("Diagnostic snapshot expired")
            require(snapshot.uid==uid) { "Snapshot identity mismatch" }
        }
        val index=if(snapshot.count<0) 0 else request.getInt("index",-1)
        val json=snapshot.values.getOrNull(index) ?: "null"
        val offset=request.getInt("offset",0)
        require(offset>=0 && offset<=json.length) { "Invalid chunk offset" }
        Bundle().apply { putString("token",token); putInt("count",snapshot.count); putInt("length",json.length); putString("json",json.substring(offset,minOf(offset+8192,json.length))) }
    }
    object Host {
        fun with(context: Context, initiate: Host.() -> Unit) = apply {
            initiate()
            val binder = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code != FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                    data.enforceInterface(descriptor)
                    val uid = getCallingUid()
                    val packages = context.packageManager.getPackagesForUid(uid)
                    if (packages?.toSet() != setOf(app) || context.checkPermission(permission, getCallingPid(), uid) != PackageManager.PERMISSION_GRANTED)
                        throw SecurityException("Framework RPC requires module identity")
                    val request = data.readBundle(javaClass.classLoader) ?: Bundle()
                    val identity = clearCallingIdentity()
                    try {
                        val op = request.getString("op") ?: "ping"
                        val output = Bundle()
                        if (op == "ping") output.putBoolean("active", true)
                        else {
                            output.putAll(snapshotResponse(uid,request))
                        }
                        reply?.writeNoException(); reply?.writeBundle(output)
                    } catch(t: Exception) { reply?.writeException(t) }
                    finally { restoreCallingIdentity(identity) }
                    return true
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    // Only a small Binder capability is returned, never crash/log contents.
                    setResultExtras(Bundle().apply { putBinder("binder", binder) })
                }
            }
            if(Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, IntentFilter(action), permission, main, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, IntentFilter(action), permission, main)
        }
        fun onRefreshFrameworkPrefsData(cb: () -> Unit) { handlers["refresh"] = { cb(); true } }
        fun onOpenAppUsedFramework(cb: (Pair<String,Int>) -> Unit) { handlers["open"] = { cb(Pair(it.getString("package") ?: "",it.getInt("user"))); true } }
        fun onPushAppErrorInfoData(cb: (Int)->AppErrorsInfoBean) { handlers["error"] = { cb(it.getInt("pid")) } }
        fun onPushAppErrorsInfoData(cb: ()->ArrayList<AppErrorsInfoBean>) { handlers["errors"] = { cb() } }
        fun onRemoveAppErrorsInfoData(cb: (AppErrorsInfoBean)->Unit) { handlers["remove"] = { b -> com.fankes.apperrorstracking.data.AppErrorsRecordData.allData.firstOrNull { it.pid == b.getInt("pid") && it.timestamp == b.getLong("timestamp") }?.let(cb); true } }
        fun onClearAppErrorsInfoData(cb: ()->Unit) { handlers["clear"] = { cb(); true } }
        fun onMutedErrorsIfUnlock(cb: (String)->Unit) { handlers["muteUnlock"] = { cb(it.getString("package") ?: ""); true } }
        fun onMutedErrorsIfRestart(cb: (String)->Unit) { handlers["muteRestart"] = { cb(it.getString("package") ?: ""); true } }
        fun onPushMutedErrorsAppsData(cb: ()->ArrayList<MutedErrorsAppBean>) { handlers["muted"] = { cb() } }
        fun onUnmuteErrorsApp(cb: (MutedErrorsAppBean)->Unit) { handlers["unmute"] = { cb(gson.fromJson(it.getString("input"),MutedErrorsAppBean::class.java)); true } }
        fun onUnmuteAllErrorsApps(cb: ()->Unit) { handlers["unmuteAll"] = { cb(); true } }
        fun onPushAppListData(cb: (AppFiltersBean)->ArrayList<AppInfoBean>) { handlers["apps"] = { cb(gson.fromJson(it.getString("input"),AppFiltersBean::class.java)) } }
        fun onPushLoggerData(cb: ()->ArrayList<AppLogData>) { handlers["logger"] = { cb() } }
    }
    private fun connect(context: Context, done: (IBinder?)->Unit) {
        remote?.takeIf { it.isBinderAlive }?.let { done(it); return }
        val delivered=java.util.concurrent.atomic.AtomicBoolean()
        fun complete(b: IBinder?) { if(delivered.compareAndSet(false,true)) { remote=b; if(b==null) active=false; done(b) } }
        main.postDelayed({ complete(null) },5000)
        try { context.sendOrderedBroadcast(Intent(action).setPackage("android"), null, object: BroadcastReceiver() {
            override fun onReceive(c: Context,i: Intent) { complete(getResultExtras(false)?.getBinder("binder")) }
        }, main, 0, null, null) } catch(t: Exception) { AppLog.error("Framework discovery failed",t); complete(null) }
    }
    private fun transact(b: IBinder, request: Bundle): Bundle {
        val input=Parcel.obtain(); val output=Parcel.obtain()
        try { input.writeInterfaceToken(descriptor); input.writeBundle(request); check(b.transact(IBinder.FIRST_CALL_TRANSACTION,input,output,0)); output.readException(); return output.readBundle(FrameworkTool::class.java.classLoader) ?: Bundle() }
        finally { input.recycle(); output.recycle() }
    }
    private fun call(context: Context, op: String, input: Bundle=Bundle(), done: (IBinder,Bundle)->Unit) {
        connect(context) { b -> if(b!=null) worker.execute { runCatching { input.putString("op",op); done(b,input) }.onFailure { remote=null; active=false; AppLog.error("Framework RPC failed: $op",it) } } }
    }
    private fun release(b: IBinder,token: String?) { if(token!=null) runCatching { transact(b,Bundle().apply { putString("op","release"); putString("mode","release"); putString("token",token) }) } }
    private fun read(b: IBinder,request: Bundle,first: Bundle?=null): String {
        val text=StringBuilder(); var response=first ?: transact(b,request)
        do { val chunk=response.getString("json") ?: ""; text.append(chunk); if(text.length>=response.getInt("length")) break; check(chunk.isNotEmpty()); request.putInt("offset",text.length); response=transact(b,request) } while(true)
        return text.toString()
    }
    private fun <T> single(c: Context,op: String,input: Bundle,type: Class<T>,cb: (T)->Unit) = call(c,op,input) { b,r ->
        var token: String?=null
        try { val first=transact(b,r); token=first.getString("token"); r.putString("token",token); r.putString("mode","read"); val value=gson.fromJson(read(b,r,first),type); main.post { cb(value) } } finally { release(b,token) }
    }
    private fun <T> list(c: Context,op: String,input: Bundle,type: Class<T>,cb: (ArrayList<T>)->Unit) = call(c,op,input) { b,r ->
        var token: String?=null
        try { r.putInt("index",-1); val first=transact(b,r); token=first.getString("token"); r.putString("token",token); r.putString("mode","read"); val count=first.getInt("count").coerceAtLeast(0); val values=ArrayList<T>()
            for(index in 0 until count) { r.putInt("index",index); r.putInt("offset",0); val json=read(b,r); if(json!="null") values.add(gson.fromJson(json,type)) }
            main.post { cb(values) }
        } finally { release(b,token) }
    }
    private fun ack(c: Context,op: String,input: Bundle=Bundle(),cb: ()->Unit={}) = call(c,op,input) { b,r -> val response=transact(b,r); release(b,response.getString("token")); main.post { cb() } }
    fun checkingActivated(context: Context,result: (Boolean)->Unit) { connect(context) { b -> if(b==null) { active=false; result(false) } else worker.execute { val success=runCatching { transact(b,Bundle().apply { putString("op","ping") }).getBoolean("active") }.getOrDefault(false); active=success; if(!success) remote=null; main.post { result(success) } } } }
    fun refreshFrameworkPrefsData(context: Context) = ack(context,"refresh")
    fun openAppUsedFramework(context: Context,packageName: String,userId: Int) = ack(context,"open",Bundle().apply { putString("package",packageName); putInt("user",userId) })
    fun fetchAppErrorInfoData(context: Context,pid: Int,result: (AppErrorsInfoBean)->Unit) = single(context,"error",Bundle().apply { putInt("pid",pid) },AppErrorsInfoBean::class.java,result)
    fun fetchAppErrorsInfoData(context: Context,result: (ArrayList<AppErrorsInfoBean>)->Unit) = list(context,"errors",Bundle(),AppErrorsInfoBean::class.java,result)
    fun removeAppErrorsInfoData(context: Context,appErrorsInfo: AppErrorsInfoBean,callback: ()->Unit) = ack(context,"remove",Bundle().apply { putInt("pid",appErrorsInfo.pid); putLong("timestamp",appErrorsInfo.timestamp) },callback)
    fun clearAppErrorsInfoData(context: Context,callback: ()->Unit) = ack(context,"clear",cb=callback)
    fun mutedErrorsIfUnlock(context: Context,packageName: String,callback: ()->Unit) = ack(context,"muteUnlock",Bundle().apply { putString("package",packageName) },callback)
    fun mutedErrorsIfRestart(context: Context,packageName: String,callback: ()->Unit) = ack(context,"muteRestart",Bundle().apply { putString("package",packageName) },callback)
    fun fetchMutedErrorsAppsData(context: Context,result: (ArrayList<MutedErrorsAppBean>)->Unit) = list(context,"muted",Bundle(),MutedErrorsAppBean::class.java,result)
    fun unmuteErrorsApp(context: Context,mutedErrorsApp: MutedErrorsAppBean,callback: ()->Unit) = ack(context,"unmute",Bundle().apply { putString("input",gson.toJson(mutedErrorsApp)) },callback)
    fun unmuteAllErrorsApps(context: Context,callback: ()->Unit) = ack(context,"unmuteAll",cb=callback)
    fun fetchAppListData(context: Context,appFilters: AppFiltersBean,result: (ArrayList<AppInfoBean>)->Unit) = list(context,"apps",Bundle().apply { putString("input",gson.toJson(appFilters)) },AppInfoBean::class.java,result)
    fun fetchLoggerData(context: Context,result: (ArrayList<AppLogData>)->Unit) = list(context,"logger",Bundle(),AppLogData::class.java,result)
    fun restartSystem(context: Context) {
        /** 当 Root 权限获取失败时显示对话框 */
        fun showWhenAccessRootFail() =
            context.showDialog {
                title = context.getString(R.string.access_root_fail)
                msg = context.getString(R.string.access_root_fail_tip)
                confirmButton(context.getString(R.string.got_it))
            }
        context.showDialog {
            title = context.getString(R.string.notice)
            msg = context.getString(R.string.are_your_sure_restart_system)
            confirmButton {
                if (isRootAccess)
                    execShell(cmd = "reboot")
                else showWhenAccessRootFail()
            }
            neutralButton(context.getString(R.string.fast_restart)) {
                context.showDialog {
                    title = context.getString(R.string.warning)
                    msg = context.getString(R.string.fast_restart_problem)
                    confirmButton {
                        if (isRootAccess)
                            execShell(cmd = "killall zygote")
                        else showWhenAccessRootFail()
                    }
                    cancelButton()
                }
            }
            cancelButton()
        }
    }


}