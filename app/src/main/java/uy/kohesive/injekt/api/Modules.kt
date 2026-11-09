package uy.kohesive.injekt.api

/** Clean-room Injekt module ABI. */
interface InjektModule {
    fun registerWith(into: InjektRegistrar)
}