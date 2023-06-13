package test

import sample.Example
import kotlin.coroutines.EmptyCoroutineContext

suspend fun main() {
  Example(EmptyCoroutineContext).myFunction(1)
}
