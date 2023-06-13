package sample

import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class Example @JvmOverloads constructor(
    override val coroutineContext: CoroutineContext = Dispatchers.Default
) : CoroutineScope{
    @AsFuture
    suspend fun <A> myFunction(argument: A): String {
        delay(1000)
        return argument.toString()
    }
}
