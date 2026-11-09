package uy.kohesive.injekt.api

import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Clean-room Injekt factory ABI. Extensions inline `Injekt.get<T>()` into
 * `getInstance(fullType<T>().type)`, so [getInstance] is the method their dex
 * actually calls; the rest of the surface is kept for signature fidelity.
 */
interface InjektFactory {
    fun <R : Any> getInstance(forType: Type): R
    fun <R : Any> getInstanceOrElse(forType: Type, default: R): R
    fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R
    fun <R : Any> getInstanceOrNull(forType: Type): R?

    fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R
    fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R
    fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R
    fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R?

    fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R
    fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R
}

inline fun <R : Any> InjektFactory.get(forType: TypeReference<R>): R = getInstance(forType.type)
inline fun <reified R : Any> InjektFactory.get(): R = getInstance(fullType<R>().type)
inline fun <reified R : Any> InjektFactory.getOrNull(): R? = getInstanceOrNull(fullType<R>().type)
inline fun <reified R : Any> InjektFactory.getOrElse(default: R): R =
    getInstanceOrElse(fullType<R>().type, default)
inline fun <reified R : Any> InjektFactory.get(key: Any): R =
    getKeyedInstance(fullType<R>().type, key)
inline fun <R : Any> InjektFactory.get(forType: TypeReference<R>, key: Any): R =
    getKeyedInstance(forType.type, key)
inline fun <reified R : Any, T : Any> InjektFactory.logger(forClass: Class<T>): R =
    getLogger(fullType<R>().type, forClass)
inline fun <reified R : Any, T : Any> InjektFactory.logger(forClass: KClass<T>): R =
    getLogger(fullType<R>().type, forClass.java)