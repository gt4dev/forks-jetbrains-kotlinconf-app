package org.jetbrains.kotlinconf

/** Opens only explicitly annotated classes and their members for common-test mocks. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
internal annotation class OpenForMokkery
