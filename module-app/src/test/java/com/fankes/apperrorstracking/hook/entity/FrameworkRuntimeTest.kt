package com.fankes.apperrorstracking.hook.entity
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
class FrameworkRuntimeTest {
    private class Invocation {
        var calls=0
        var received: Array<out Any?>?=null
        var failure: RuntimeException?=null
        val original=java.util.Collections.unmodifiableList(listOf<Any?>(1))
        val chain=Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,arrayOf(XposedInterface.Chain::class.java)) { _,m,a ->
            when(m.name) {
                "getExecutable" -> String::class.java.getDeclaredMethod("substring",Int::class.javaPrimitiveType)
                "getThisObject" -> "receiver"
                "getArgs" -> original
                "proceed" -> { calls++; @Suppress("UNCHECKED_CAST") val args=a!![0] as Array<out Any?>; received=args.copyOf(); failure?.let { throw it }; "original" }
                else -> error(m.name)
            }
        } as XposedInterface.Chain
    }
    @Test fun nullEarlyReturn() { val i=Invocation(); assertNull(FrameworkRuntime.execute(i.chain,true) { it.result=null }); assertEquals(0,i.calls) }
    @Test fun copiesImmutableArguments() { val i=Invocation(); assertEquals("original",FrameworkRuntime.execute(i.chain,true) { it.args[0]=7 }); assertEquals(1,i.calls); assertEquals(listOf<Any?>(1),i.original); assertArrayEquals(arrayOf<Any?>(7),i.received) }
    @Test fun afterPreservesOriginalFailure() { val i=Invocation(); val t=IllegalStateException("original"); i.failure=t; try { FrameworkRuntime.execute(i.chain,false) {}; fail() } catch(actual: IllegalStateException) { assertSame(t,actual) }; assertEquals(1,i.calls) }
    @Test fun afterCanRecoverFailure() { val i=Invocation(); i.failure=IllegalStateException("original"); assertEquals("recovered",FrameworkRuntime.execute(i.chain,false) { it.result="recovered" }); assertEquals(1,i.calls) }
    @Test fun failingBeforeRollsBack() { val i=Invocation(); assertEquals("original",FrameworkRuntime.execute(i.chain,true) { it.args[0]=7; it.result=null; throw IllegalArgumentException() }); assertArrayEquals(arrayOf<Any?>(1),i.received); assertEquals(1,i.calls) }
    @Test fun failingAfterRestoresOriginal() { val i=Invocation(); assertEquals("original",FrameworkRuntime.execute(i.chain,false) { it.result="changed"; throw IllegalArgumentException() }); assertEquals(1,i.calls) }
}