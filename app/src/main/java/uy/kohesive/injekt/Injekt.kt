@file:Suppress("NOTHING_TO_INLINE")

package uy.kohesive.injekt

import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.registry.default.DefaultRegistrar

/**
 * Clean-room Injekt global ABI.
 *
 * The file name is load-bearing: a top-level `Injekt` variable compiles to the
 * facade `InjektKt`, and an extension's inlined `injectLazy` calls
 * `InjektKt.getInjekt()`. Renaming this file breaks that linkage silently.
 */
@Volatile
var Injekt: InjektScope = InjektScope(DefaultRegistrar())

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.get(fullType<T>()) }

inline fun <reified T : Any> injectValue(): Lazy<T> = lazyOf(Injekt.get(fullType<T>()))

inline fun <reified T : Any> injectLazy(key: Any): Lazy<T> = lazy { Injekt.get(fullType<T>(), key) }