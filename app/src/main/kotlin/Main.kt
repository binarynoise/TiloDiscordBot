import Logger.log
import com.jessecorbett.diskord.api.common.Channel
import com.jessecorbett.diskord.api.common.GuildMember
import com.jessecorbett.diskord.api.common.GuildVoiceChannel
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.api.gateway.events.CreatedGuildVoiceChannel
import com.jessecorbett.diskord.api.guild.GuildClient
import com.jessecorbett.diskord.bot.BotBase
import com.jessecorbett.diskord.bot.bot
import com.jessecorbett.diskord.bot.events
import com.jessecorbett.diskord.internal.client.RestClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.gnome.gtk.Gtk
import org.gnome.notify.Notification
import org.gnome.notify.Notify
import kotlin.concurrent.thread
import kotlin.system.exitProcess

val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: error("missing bot token")

val guildNames: MutableMap<String, String> = sortedMapOf()
val channelNames: MutableMap<String, String> = sortedMapOf()
val users: MutableMap<String, User> = sortedMapOf()

val channelsInGuild: MutableMap<String, MutableSet<String>> = sortedMapOf()
val guildForChannel: MutableMap<String, String> = sortedMapOf()
val channelIsAfk: MutableMap<String, Boolean> = sortedMapOf()

val usersInVoiceChannel: MutableMap<String, MutableSet<String>> = mutableMapOf()
val voiceChannelForUser: MutableMap<String, String> = mutableMapOf()

lateinit var ownUserID: String
var bot: BotBase? = null
val restClient = RestClient.default(BOT_TOKEN)
//val globalClient = GlobalClient(restClient)

val guildClients: MutableMap<String, GuildClient> = sortedMapOf()
fun guildClient(id: String) = guildClients.getOrPut(id) { GuildClient(id, restClient) }

//val channelClients: MutableMap<String, ChannelClient> = sortedMapOf()
//fun channelClient(id: String) = channelClients.getOrPut(id) { ChannelClient(id, restClient) }

suspend fun main(): Unit = coroutineScope {
    launch(Dispatchers.IO) {
        while (this@coroutineScope.isActive) {
            delay(1000)
            1 + 1
        }
    }
    
    suspend fun shutdown() {
        log("shutting down")
        bot?.shutdown()
        log("bot shut down")
    }
    
    launch(Dispatchers.IO) {
        log("write exit or quit to shut down the server")
        var line: String?
        do {
            line = readLine()
        } while (line != "exit" && line != "quit" && line != "stop")
        shutdown()
        exitProcess(0)
    }
    
    launch {
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                shutdown()
                Runtime.getRuntime().halt(0)
            }
        })
    }
    
    launch {
        bot(BOT_TOKEN) {
            bot = this
            events {
                onReady {
                    log("started and ready")
                    ownUserID = it.user.id
                }
                
                onGuildCreate { guild ->
                    log("onGuildCreate ${guild.name}")
                    
                    guildNames[guild.id] = guild.name
                    
                    val channels = guild.channels?.filterIsInstance<CreatedGuildVoiceChannel>() ?: emptyList()
                    channels.forEach { channel ->
                        channelNames[channel.id] = channel.name
                        channelsInGuild.getOrCreate(guild.id).add(channel.id)
                        guildForChannel[channel.id] = guild.id
                        channelIsAfk[channel.id] = channel.id == guild.afkChannelId
                    }
                    
                    for (voiceState in guild.voiceStates ?: emptyList()) {
                        log("onGuildCreate voiceStates: $voiceState")
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
                        members.mapNotNull(GuildMember::user).forEach { users[it.id] = it; /*log(it.toString())*/ }
                    } while (members.isNotEmpty())
                    
                    printUsersInChannels()
                }
                
                onGuildUpdate { guild ->
                    log("onGuildUpdate ${guild.name}")
                    guildNames[guild.id] = guild.name
                }
                
                val channelHandler: suspend (Channel) -> Unit = { channel ->
                    if (channel is GuildVoiceChannel) {
                        log("onChannelCreate/Update ${channel.name}")
                        channelNames[channel.id] = channel.name
                        channelsInGuild.getOrCreate(channel.guildId).add(channel.id)
                        guildForChannel[channel.id] = channel.guildId
                    }
                }
                onChannelCreate(channelHandler)
                
                onChannelUpdate(channelHandler)
                
                onGuildMemberAdd { guildMemberAdd ->
                    val user = guildMemberAdd.user
                    log("onGuildMemberAdd ${user.username}")
                    users[user.id] = user
                }
                
                onGuildMemberUpdate { guildMemberUpdate ->
                    val user = guildMemberUpdate.user
                    log("onGuildMemberUpdate ${user.username}")
                    users[user.id] = user
                }
                
                onVoiceStateUpdate { voiceState ->
                    log("onVoiceStateUpdate")
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
    
    log(message)
    sendGtkMessage(message)
}

val notificationMutex = Mutex()
val notification by lazy {
    Gtk.init(emptyArray())
    Notify.init("TiloDiscordBot")
    thread {
        Gtk.main()
    }
    
    val notification = Notification("TiloDiscordBot", "\n".repeat(4), "discord")
//    notification.setCategory("presence")
//    notification.setUrgency(Urgency.LOW)
    notification.setHint("suppress-sound", 1)
    notification.show()
    
    Runtime.getRuntime().addShutdownHook(Thread {
        notification.close()
        Notify.uninit()
        Gtk.mainQuit()
    })
    notification
    
}

fun CoroutineScope.sendGtkMessage(message: String) = launch(Dispatchers.IO) {
    notificationMutex.withLock {
        notification.update("TiloDiscordBot", message, "discord")
        notification.show()
    }
}

fun <T, S : Comparable<S>> MutableMap<T, MutableSet<S>>.getOrCreate(key: T) = getOrPut(key) { sortedSetOf() }
fun <T, M : Comparable<M>, MT> MutableMap<T, MutableMap<M, MT>>.getOrCreate(key: T) = getOrPut(key) { sortedMapOf() }

