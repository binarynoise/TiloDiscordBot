import kotlin.concurrent.thread
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.gnome.gtk.Gtk
import org.gnome.notify.Notification
import org.gnome.notify.Notify

private val logger = KotlinLogging.logger {}

val notificationMutex = Mutex()
var allowNotifications by atomic(true)
val notification by lazy {
    Gtk.init(emptyArray())
    Notify.init("TiloDiscordBot")
    thread {
        Gtk.main()
    }
    
    val notification = Notification("TiloDiscordBot", "", "discord")
    notification.setHint("suppress-sound", 1)
    notification.setTimeout(2000)
    
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            notificationMutex.withLock {
                allowNotifications = false
                notification.update("TiloDiscordBot", "shutting down", "discord")
                notification.show()
                
                Thread.sleep(1000)
                
                notification.close()
                Notify.uninit()
                Gtk.mainQuit()
            }
        }
    })
    notification
    
}

fun CoroutineScope.sendGtkMessage(message: String) = launch(Dispatchers.IO) {
    notificationMutex.withLock {
        logger.info { "\n\"\"\"\n$message\n\"\"\"" }
        if (!allowNotifications) return@launch
        try {
            notification.update("TiloDiscordBot", message, "discord")
            notification.show()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to show notification" }
        }
        logger.debug { "ready for next notification" }
    }
}
