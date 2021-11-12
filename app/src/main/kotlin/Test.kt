import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object Test {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        var message = "Test"
        repeat(3) {
            message += "\n" + it
            sendGtkMessage(message)
            delay(2000)
        }
        
        delay(10000)
    }
}
