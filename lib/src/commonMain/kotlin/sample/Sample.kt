package sample

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

class Example(override val coroutineContext: CoroutineContext) : CoroutineScope {
    @AsFuture
    suspend fun myFunction(): Unit = Unit
}