package uy.kohesive.injekt.api

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Clean-room declaration of the Injekt type-token ABI.
 *
 * Aniyomi extensions inline `fullType<T>()` into their own dex, which emits an
 * anonymous subclass of [FullTypeReference]. The class must exist under this
 * exact name and compute its type the same way, or the extension's generated
 * token resolves to the wrong type. No upstream code is copied; the type-token
 * pattern is the published interface.
 */
interface TypeReference<T> {
    val type: Type
}

abstract class FullTypeReference<T> protected constructor() : TypeReference<T> {
    override val type: Type = javaClass.genericSuperclass.let { superClass ->
        require(superClass is ParameterizedType) {
            "TypeReference constructed without actual type information"
        }
        superClass.actualTypeArguments[0]
    }
}

inline fun <reified T : Any> fullType(): FullTypeReference<T> = object : FullTypeReference<T>() {}

inline fun <reified T : Any> typeRef(): FullTypeReference<T> = fullType<T>()