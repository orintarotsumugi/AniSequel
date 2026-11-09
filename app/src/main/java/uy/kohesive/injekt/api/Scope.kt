package uy.kohesive.injekt.api

/** Clean-room Injekt scope ABI. The global `Injekt` variable is one of these. */
open class InjektScope(val registrar: InjektRegistrar) : InjektRegistrar by registrar