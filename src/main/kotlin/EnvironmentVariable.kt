import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class EnvironmentVariable<T : Any>(private val name: String? = null, private val transform: (String) -> T) : ReadOnlyProperty<Any?, T> {
    private lateinit var transformedValue: T
    
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val name = this.name ?: property.name
        if (::transformedValue.isInitialized) return transformedValue
        val value = System.getenv(name) ?: error("missing environment variable: $name")
        transformedValue = transform(value)
        return transformedValue
    }
    
    companion object : ReadOnlyProperty<Any?, String> {
        operator fun invoke(name: String): String = System.getenv(name) ?: error("missing environment variable: $name")
        operator fun <T : Any> invoke(name: String, transform: (String) -> T): T =
            System.getenv(name)?.let(transform) ?: error("missing environment variable: $name")
        
        override operator fun getValue(thisRef: Any?, property: KProperty<*>): String = invoke(property.name)
        
        private val cache: MutableMap<KProperty<*>, String> = mutableMapOf()
    }
}

object OptionalEnvironmentVariable {
    operator fun invoke(name: String): String? = System.getenv(name)
    operator fun <T> invoke(name: String, transform: (String) -> T?): T? = System.getenv(name)?.let(transform)
    operator fun getValue(thisRef: Nothing?, property: KProperty<*>): String? = invoke(property.name)
}
