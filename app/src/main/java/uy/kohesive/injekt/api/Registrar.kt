package uy.kohesive.injekt.api

/** Clean-room Injekt registrar ABI combining the registry and factory surfaces. */
interface InjektRegistrar : InjektRegistry, InjektFactory {
    fun importModule(submodule: InjektModule) {
        submodule.registerWith(this)
    }
}