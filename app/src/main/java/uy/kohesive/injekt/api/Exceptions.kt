package uy.kohesive.injekt.api

/** Clean-room Injekt exception ABI, thrown when a type was never registered. */
class InjektionException(message: String) : RuntimeException(message)