@file:Suppress("unused", "MemberVisibilityCanBePrivate")

import com.jessecorbett.diskord.api.gateway.EventDispatcher
import kotlinx.coroutines.CoroutineScope
import java.lang.ref.Reference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

object Logger {
    
    fun log(message: CharSequence) {
        val callingClassTag = callingClassTag
        message.toString().lines().forEach {
            println("$callingClassTag: $it")
        }
    }
    
    fun log(message: CharSequence, t: Throwable) {
        val callingClassTag = callingClassTag
        val logString = "$message\n${t.stackTraceToString()}"
        logString.lines().forEach {
            println("$callingClassTag: $it")
        }
    }
    
    private val currentDateTimeString get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS", Locale.GERMAN).format(Date()).toString()
    
    fun logSplit(message: String?, delimiter: String, head: String? = null, end: String? = null) {
        if (head != null) log(head)
        val indent = if (head == null && end == null) "" else "\t"
        (message ?: "null").split(delimiter).forEach { log(indent + it.trim()) }
        if (end != null) log(end) else if (head != null) log("end $head")
    }
    
    fun Any?.dump(name: String, forceInclude: Set<Any> = emptySet(), forceIncludeClasses: Set<Class<*>> = emptySet()) {
        
        log("dumping $name")
        dump(name, 0, mutableSetOf(), forceInclude, forceIncludeClasses)
        System.out.flush()
    }
    
    private fun Any?.dump(name: String, indent: Int, processed: MutableSet<Any>, forceInclude: Set<Any>, forceIncludeClasses: Set<Class<*>>) {
        //<editor-fold defaultstate="collapsed" desc="...">
        
        val tabs = " ".repeat(indent * 2)
        val nextIndent = indent + 1
        print("$tabs$name ")
        if (this == null || this is Nothing? || this::class.qualifiedName == "null") {
            println("-> null")
            return
        }
        if (this::class.javaPrimitiveType != null || this is CharSequence) {
            println(this.toString())
            return
        }
        print("(${this::class.qualifiedName}@${hashCode()}) -> ")
        if (processed.contains(this)) {
            println("already dumped")
            return
        }
        processed.add(this)
        when {
            indent > 3 -> {
                println("[...]")
            }
            this::class.java.isArray -> {
                if (this is Array<*>) { // Object Arrays
                    if (this.isEmpty()) {
                        println("[]")
                    } else {
                        println()
                        this.forEachIndexed { index, value -> value.dump(index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                    }
                } else { // primitive Array like int[]
                    println(Arrays::class.java.getMethod("toString", this::class.java).invoke(null, this))
                }
            }
            
            this is Collection<*> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEachIndexed { index, value -> value.dump(index.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            this is Map<*, *> -> {
                if (this.isEmpty()) {
                    println("[]")
                } else {
                    println()
                    this.forEach { (k, v) -> v.dump(k.toString(), nextIndent, processed, forceInclude, forceIncludeClasses) }
                }
            }
            forceInclude.none { it == this } && forceIncludeClasses.none { it.isInstance(this) } && (this is Thread || this is ThreadGroup || this is ClassLoader || this is Function<*> || this is CoroutineContext || this is CoroutineScope || this is EventDispatcher<*>) -> {
                println(this.toString())
            }
            this is Reference<*> -> {
//                println(get().toString())
                println()
                get().dump("referenced", nextIndent, processed, forceInclude, forceIncludeClasses)
            }
            this is Class<*> -> {
                println(this.canonicalName)
            }
            this is KClass<*> -> {
                println(this.java.canonicalName)
            }
            this::class.java.declaredFields.find { it.name.equals("INSTANCE", true) } != null -> {
                println("kotlin object")
                return
            }
            else -> {
                println()
                val fields = mutableSetOf<Field>()
                var cls: Class<*>? = this::class.java
                while (cls != null && cls != Any::class.java && cls != Object::class.java) {
                    fields.addAll(cls.declaredFields)
                    cls = cls.superclass
                }
                
                fields.sortedBy { it.name }.forEach {
                    try {
                        it.isAccessible = true
                        if (!Modifier.isStatic(it.modifiers)) {
                            it.get(this).dump(it.name, nextIndent, processed, forceInclude, forceIncludeClasses)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
//        processed.remove(this)
        //</editor-fold>
    }
    
    private val callingClassTag: String
        get() {
            val stackTraceElement = callingClassStackTraceElement
            
            val simpleClassName = stackTraceElement.simpleClassName
            val lineNumber = stackTraceElement.lineNumber
            val file = stackTraceElement.fileName ?: "unknown"
//                val substringAfterLast = proc.substringAfterLast(":", missingDelimiterValue = "x")
//                val proc = if (substringAfterLast != "x") "$substringAfterLast:" else ""
//                val thread = Thread.currentThread().name
            return simpleClassName.padEnd(15) + (" ($file:$lineNumber)").padStart(20)
        }
    
    private val StackTraceElement.simpleClassName: String
        get() = className.split("$")[0].split(".").last()
    
    val callingClassName
        get() = callingClassStackTraceElement.simpleClassName.apply {
            if (endsWith("Logger")) System.err.println("invalid callingClassName")
        }
    
    private val callingClassStackTraceElement: StackTraceElement
        get() {
            val stackTrace = Thread.currentThread().stackTrace
            
            var foundOwn = false
            stackTrace.forEach { ste ->
                val isLogger = ste.className == Logger::class.qualifiedName
                if (isLogger) {
                    foundOwn = true
                } else if (foundOwn) {
                    return ste
                }
            }
            
            System.err.println(stackTrace.joinToString("\t\n"))
            throw IllegalStateException("invalid stack")
        }
}


