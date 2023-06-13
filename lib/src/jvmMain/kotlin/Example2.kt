package test

import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import sample.AsFuture

class Example2(override val coroutineContext: CoroutineContext) : CoroutineScope {
  suspend fun myFunction(): Int {
    delay(1000)
    return 1 //argument.toInt()
  }

  fun myFunctionFuture(): CompletableFuture<Int> =
    future { myFunction() }
}