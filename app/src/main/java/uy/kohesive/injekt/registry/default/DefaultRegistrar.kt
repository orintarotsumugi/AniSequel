package uy.kohesive.injekt.registry.default

import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean-room Injekt registrar: a concurrent factory map with a singleton cache.
 *
 * Only the runtime process registers anything (a tunnelled [NetworkHelper] and
 * a JSON parser), so this is deliberately small. Loggers are refused rather than
 * faked: an extension that asks for one gets a named error, not a silent no-op.
 */
open class DefaultRegistrar : InjektRegistrar {

    private val factories = ConcurrentHashMap<Type, () -> Any>()
    private val keyedFactories = ConcurrentHashMap<Type, (Any) -> Any>()
    private val singletons = ConcurrentHashMap<Type, Any>()

    override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) {
        singletons[forType.type] = singleInstance
        factories[forType.type] = { singleInstance }
    }

    override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) {
        factories[forType.type] = {
            @Suppress("UNCHECKED_CAST")
            singletons.computeIfAbsent(forType.type) { factoryCalledOnce() } as R
        }
    }

    override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) {
        factories[forType.type] = factoryCalledEveryTime
    }

    override fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) {
        val local = ThreadLocal<Any>()
        factories[forType.type] = {
            local.get() ?: factoryCalledOncePerThread().also { local.set(it) }
        }
    }

    override fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) {
        keyedFactories[forType.type] = { key ->
            @Suppress("UNCHECKED_CAST")
            singletons.computeIfAbsent(KeyedType(forType.type, key)) { factoryCalledPerKey(key as K) } as R
        }
    }

    override fun <R : Any, K : Any> addPerThreadPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKeyPerThread: (K) -> R) {
        val local = ThreadLocal<MutableMap<Any, Any>>()
        keyedFactories[forType.type] = { key ->
            val map = local.get() ?: HashMap<Any, Any>().also { local.set(it) }
            @Suppress("UNCHECKED_CAST")
            map.getOrPut(key) { factoryCalledPerKeyPerThread(key as K) } as R
        }
    }

    override fun <R : Any> addLoggerFactory(forLoggerType: TypeReference<R>, factoryByName: (String) -> R, factoryByClass: (Class<Any>) -> R) {
        throw InjektionException("This runtime does not provide loggers")
    }

    override fun <O : Any, T : O> addAlias(existingRegisteredType: TypeReference<T>, otherAncestorOrInterface: TypeReference<O>) {
        factories[existingRegisteredType.type]?.let { factories[otherAncestorOrInterface.type] = it }
        keyedFactories[existingRegisteredType.type]?.let { keyedFactories[otherAncestorOrInterface.type] = it }
    }

    override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean =
        factories.containsKey(forType.type) || keyedFactories.containsKey(forType.type)

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstance(forType: Type): R =
        (factories[forType] ?: throw InjektionException("No registered instance for $forType")).invoke() as R

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R =
        factories[forType]?.invoke() as R? ?: default

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        factories[forType]?.invoke() as R? ?: default()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? =
        factories[forType]?.invoke() as R?

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R =
        (keyedFactories[forType] ?: throw InjektionException("No keyed factory for $forType")).invoke(key) as R

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R =
        keyedFactories[forType]?.invoke(key) as R? ?: default

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R =
        keyedFactories[forType]?.invoke(key) as R? ?: default()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? =
        keyedFactories[forType]?.invoke(key) as R?

    override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R =
        throw InjektionException("This runtime does not provide loggers")

    override fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R =
        throw InjektionException("This runtime does not provide loggers")

    private data class KeyedType(val type: Type, val key: Any)
}