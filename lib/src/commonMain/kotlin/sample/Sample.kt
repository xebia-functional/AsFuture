package sample

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

class Example(override val coroutineContext: CoroutineContext) : CoroutineScope {
    @AsFuture
    suspend fun myFunction(): Int {
        delay(1000)
        return 1 //argument.toInt()
    }
}
