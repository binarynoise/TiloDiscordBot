import com.jessecorbett.diskord.api.common.Channel
import com.jessecorbett.diskord.api.common.GuildMember
import com.jessecorbett.diskord.api.common.GuildVoiceChannel
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.api.gateway.events.CreatedGuildVoiceChannel
import com.jessecorbett.diskord.api.guild.GuildClient
import com.jessecorbett.diskord.bot.bot
import com.jessecorbett.diskord.bot.events
import com.jessecorbett.diskord.internal.client.RestClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.gnome.gtk.Gtk
import org.gnome.notify.Notification
import org.gnome.notify.Notify
import kotlin.concurrent.thread
import kotlin.system.exitProcess

val logger = KotlinLogging.logger {}

val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: error("missing bot token")

val guildNames: MutableMap<String, String> = sortedMapOf()
val channelNames: MutableMap<String, String> = sortedMapOf()
val users: MutableMap<String, User> = sortedMapOf()

val channelsInGuild: MutableMap<String, MutableSet<String>> = sortedMapOf()
val guildForChannel: MutableMap<String, String> = sortedMapOf()
val channelIsAfk: MutableMap<String, Boolean> = sortedMapOf()

val usersInVoiceChannel: MutableMap<String, MutableSet<String>> = mutableMapOf()
val voiceChannelForUser: MutableMap<String, String> = mutableMapOf()

//lateinit var ownUserID: String
//var bot: BotBase? = null
val restClient = RestClient.default(BOT_TOKEN)
//val globalClient = GlobalClient(restClient)

val guildClients: MutableMap<String, GuildClient> = sortedMapOf()
fun guildClient(id: String) = guildClients.getOrPut(id) { GuildClient(id, restClient) }

//val channelClients: MutableMap<String, ChannelClient> = sortedMapOf()
//fun channelClient(id: String) = channelClients.getOrPut(id) { ChannelClient(id, restClient) }

@ExperimentalCoroutinesApi
suspend fun main(): Unit = coroutineScope {
    launch(Dispatchers.IO) {
        val runningInteractively = System.getenv("RUNNING_INTERACTIVELY") == "0" // see start.sh
        if (runningInteractively) {
            logger.info { "write exit, quit or stop to shut down the server" }
            while (isActive) {
                when (readlnOrNull()) {
                    null, "exit", "quit", "stop" -> break
                }
            }
            exitProcess(0)
        }
    }
    
    fun CoroutineScope.startBotAsync() = async {
        bot(BOT_TOKEN) {
//            bot = this
            events {
                onReady {
                    logger.info { "started and ready" }
//                    ownUserID = it.user.id
                    Runtime.getRuntime().addShutdownHook(Thread {
                        runBlocking {
                            logger.info { "shutting down" }
                            coroutineScope {
                                shutdown()
                            }
                            logger.info { "bot shut down" }
                        }
                    })
                }
                
                onGuildCreate { guild ->
                    logger.info { "onGuildCreate ${guild.name}" }
                    
                    guildNames[guild.id] = guild.name
                    
                    clearGuild(guild.id)
                    
                    val channels = guild.channels?.filterIsInstance<CreatedGuildVoiceChannel>() ?: emptyList()
                    
                    channels.forEach { channel ->
                        channelNames[channel.id] = channel.name
                        channelsInGuild.getOrCreate(guild.id).add(channel.id)
                        guildForChannel[channel.id] = guild.id
                        channelIsAfk[channel.id] = channel.id == guild.afkChannelId
                    }
                    
                    for (voiceState in guild.voiceStates ?: emptyList()) {
                        logger.info { "onGuildCreate voiceStates: $voiceState" }
                        val (_, channelId, userId) = voiceState
                        if (channelId != null) {
                            usersInVoiceChannel.getOrCreate(channelId).add(userId)
                            voiceChannelForUser[userId] = channelId
                        }
                    }
                    
                    val guildClient = guildClient(guild.id)
                    var members: List<GuildMember> = emptyList()
                    do {
                        val lastId: String = members.lastOrNull()?.user?.id ?: "0"
                        members = guildClient.getMembers(100, lastId)
                        members.mapNotNull(GuildMember::user).forEach { users[it.id] = it; /*logger.info { it.toString() }*/ }
                    } while (members.isNotEmpty())
                    
                    printUsersInChannels()
                }
                
                onGuildUpdate { guild ->
                    logger.info { "onGuildUpdate ${guild.name}" }
                    guildNames[guild.id] = guild.name
                }
                
                onGuildDelete { guild ->
                    val guildId = guild.guildId
                    logger.info { "onGuildDelete ${guildNames[guildId]}" }
                    
                    clearGuild(guildId)
                    printUsersInChannels()
                }
                
                val channelHandler: suspend (Channel) -> Unit = { channel ->
                    if (channel is GuildVoiceChannel) {
                        logger.info { "onChannelCreate/Update ${channel.name}" }
                        channelNames[channel.id] = channel.name
                        channelsInGuild.getOrCreate(channel.guildId).add(channel.id)
                        guildForChannel[channel.id] = channel.guildId
                    }
                }
                onChannelCreate(channelHandler)
                
                onChannelUpdate(channelHandler)
                
                onGuildMemberAdd { guildMemberAdd ->
                    val user = guildMemberAdd.user
                    logger.info { "onGuildMemberAdd ${user.username}" }
                    users[user.id] = user
                }
                
                onGuildMemberUpdate { guildMemberUpdate ->
                    val user = guildMemberUpdate.user
                    logger.info { "onGuildMemberUpdate ${user.username}" }
                    users[user.id] = user
                }
                
                onVoiceStateUpdate { voiceState ->
                    logger.info { "onVoiceStateUpdate" }
                    val (_, newChannelId, userId) = voiceState
                    
                    val oldChannelId = voiceChannelForUser[userId]
                    
                    if (oldChannelId != newChannelId) {
                        if (oldChannelId != null) {
                            usersInVoiceChannel.getOrCreate(oldChannelId).remove(userId)
                        }
                        
                        if (newChannelId != null) {
                            voiceChannelForUser[userId] = newChannelId
                            usersInVoiceChannel.getOrCreate(newChannelId).add(userId)
                        } else {
                            voiceChannelForUser.remove(userId)
                        }
                        printUsersInChannels()
                    }
                }
            }
        }
    }
    
    @ExperimentalCoroutinesApi
    suspend fun startBotWithCrashHandler(): Unit = coroutineScope {
        val startBotAsync = startBotAsync()
        startBotAsync.await()
        val e = startBotAsync.getCompletionExceptionOrNull()
        if (e != null && e !is CancellationException) {
            logger.warn(e) { "Bot starting failed, trying again in 10s" }
            delay(10000)
            startBotWithCrashHandler()
        }
    }
    startBotWithCrashHandler()
}

private fun clearGuild(guildId: String) {
//    logger.info { "cleaning up ${guildNames[guildId]}" }
    
    val channelsInDeletedGuild = channelsInGuild[guildId] ?: return
    val usersInVoiceChannelsOfDeletedGuild = channelsInDeletedGuild.mapNotNull { usersInVoiceChannel[it] }.flatten()
    
    channelsInGuild.remove(guildId)
    channelsInDeletedGuild.forEach {
        guildForChannel.remove(it)
    }
    usersInVoiceChannel.remove(guildId)
    usersInVoiceChannelsOfDeletedGuild.forEach {
        voiceChannelForUser.remove(it)
    }
}

fun CoroutineScope.printUsersInChannels() {
    //        guilds     guildId channels   channelId users     userId
    val tree: MutableMap<String, MutableMap<String, MutableSet<String>>> = sortedMapOf()
    
    voiceChannelForUser.forEach { (userId, channelId) ->
        val guildId = guildForChannel[channelId] ?: return@forEach
        if (channelIsAfk[channelId] == true) return@forEach
        tree.getOrCreate(guildId).getOrCreate(channelId).add(userId)
    }
    
    val message = buildString {
        tree.forEach { (guildId, channels) ->
            appendLine(guildNames[guildId])
            channels.forEach { (channelId, userIds) ->
                append("  ")
                append(channelNames[channelId])
                appendLine()
                userIds.forEach {
                    append("    ")
                    append(users[it]?.username)
                    appendLine()
                }
            }
        }
    }.trim()
    
    if (message.isNotBlank()) logger.info { message }
    sendGtkMessage(message)
}

val notificationMutex = Mutex()
val notification by lazy {
    Gtk.init(emptyArray())
    Notify.init("TiloDiscordBot")
    thread {
        Gtk.main()
    }
    
    val notification = Notification("TiloDiscordBot", "", "discord")
    notification.setHint("suppress-sound", 1)
    
    Runtime.getRuntime().addShutdownHook(Thread {
        notification.update("TiloDiscordBot", "shutting down", "discord")
        notification.show()
        
        Thread.sleep(1000)
        
        notification.close()
        Notify.uninit()
        Gtk.mainQuit()
    })
    notification
    
}

fun CoroutineScope.sendGtkMessage(message: String) = launch(Dispatchers.IO) {
    notificationMutex.withLock {
        try {
            notification.update("TiloDiscordBot", message, "discord")
            notification.show()
        } catch (e: RuntimeException) {
            logger.warn(e) {}
        }
    }
}

fun <T, S : Comparable<S>> MutableMap<T, MutableSet<S>>.getOrCreate(key: T) = getOrPut(key) { sortedSetOf() }
fun <T, M : Comparable<M>, MT> MutableMap<T, MutableMap<M, MT>>.getOrCreate(key: T) = getOrPut(key) { sortedMapOf() }
