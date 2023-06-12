package sample

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Only applicable on class methods for now, not top-level functions.
 */
@Retention(BINARY)
@Target(FUNCTION)
annotation class AsFuture