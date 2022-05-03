import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.gateway.*
import dev.kord.rest.service.RestClient
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}.apply {
    System.getProperties()["kotlin-logging.throwOnMessageError"] = true
}

val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: error("missing bot token")

val guildNames: MutableMap<Snowflake, String> = sortedMapOf()
val channelNames: MutableMap<Snowflake, String> = sortedMapOf()
val users: MutableMap<Snowflake, DiscordUser> = sortedMapOf()
val nicknames: MutableMap<Snowflake, MutableMap<Snowflake, String>> = sortedMapOf()

val channelsInGuild: MutableMap<Snowflake, MutableSet<Snowflake>> = sortedMapOf()
val guildForChannel: MutableMap<Snowflake, Snowflake> = sortedMapOf()
val afkChannels: MutableSet<Snowflake> = sortedSetOf()

val usersInVoiceChannel: MutableMap<Snowflake, MutableSet<Snowflake>> = mutableMapOf()
val voiceChannelForUser: MutableMap<Snowflake, Snowflake> = mutableMapOf()

val restClient = RestClient(BOT_TOKEN)

suspend fun main(): Unit = coroutineScope {
    val mainScope = this
    
    logger.debug { "Hello..." }
    sendGtkMessage("Starte...")

//    thread(name = "Debug") {
//        while (!Thread.interrupted()) {
//            Thread.sleep(1000L)
//            true
//        }
//    }
    
    launch(Dispatchers.IO) {
        val getenv = System.getenv("RUNNING_INTERACTIVELY")
        val runningInteractively = (getenv == "0" || getenv == null) // see start.sh
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
    
    logger.debug { "creating gateway..." }
    val gateway = DefaultGateway()
    logger.debug { "created gateway" }
    
    logger.debug { "setting up gateway..." }
    with(gateway) {
        on<Ready> {
            logger.info { "started and ready" }
            
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info { "shutting down" }
                runBlocking {
                    gateway.stop()
                    delay(1000)
                    mainScope.coroutineContext.cancel()
                    while (mainScope.isActive) delay(100L)
                }
                logger.info { "bot shut down" }
            })
        }
        
        on<GuildCreate> {
            logger.info { "onGuildCreate ${guild.name}" }
            
            guildNames[guild.id] = guild.name
            clearGuild(guild.id)
            
            logger.trace { "onGuildCreate (${guild.name}): voiceChannel start" }
            val allChannels = guild.channels.value
            if (!allChannels.isNullOrEmpty()) {
                val voiceChannels = allChannels.filter { it.type == ChannelType.GuildVoice }
                if (voiceChannels.isNotEmpty()) {
                    voiceChannels.forEach { channel ->
                        val name = channel.name.value ?: return@forEach
                        logger.trace { "onGuildCreate (${guild.name}): voiceChannel $name" }
                        channelNames[channel.id] = name
                        channelsInGuild.getOrCreate(guild.id).add(channel.id)
                        guildForChannel[channel.id] = guild.id
                        if (channel.id == guild.afkChannelId) afkChannels += channel.id
                    }
                } else logger.warn { "onGuildCreate (${guild.name}): No voiceChannels in Guild" }
            } else logger.warn { "onGuildCreate (${guild.name}): No channels in Guild" }
            logger.trace { "onGuildCreate (${guild.name}): voiceChannel done" }
            
            val guildNickNames: MutableMap<Snowflake, String> = nicknames.getOrCreate(guild.id)
            
            logger.debug { "onGuildCreate (${guild.name}): requestGuildMembers start" }
            requestGuildMembers(guild.id).collect { chunk ->
                @Suppress("UNCHECKED_CAST") //
                val userMap = chunk.data.members.associateWith { it.user.value }.filterValues { it != null } as Map<DiscordGuildMember, DiscordUser>
                userMap.values.forEach { users[it.id] = it }
                userMap.forEach { (member, user) ->
                    val nick = member.nick.value
                    if (nick != null) guildNickNames[user.id] = nick
                }
                logger.debug { "onGuildCreate (${guild.name}): got ${userMap.size} users for ${guild.name}" }
            }
            logger.debug { "onGuildCreate (${guild.name}): requestGuildMembers done" }
            
            logger.trace { "onGuildCreate (${guild.name}): voiceState start" }
            val voiceStates = guild.voiceStates.value
            if (!voiceStates.isNullOrEmpty()) {
                for ((_, channelId, userId) in voiceStates) {
                    if (channelId != null) {
                        logger.trace { "onGuildCreate (${guild.name}): voiceState ${users[userId]?.username} is active in ${channelNames[channelId]}" }
                        usersInVoiceChannel.getOrCreate(channelId).add(userId)
                        voiceChannelForUser[userId] = channelId
                    }
                }
            } else logger.warn { "onGuildCreate (${guild.name}): No voiceStates in Guild" }
            logger.trace { "onGuildCreate (${guild.name}): voiceState done" }
            
            printUsersInChannels()
            logger.debug { "onGuildCreate (${guild.name}) done" }
        }
        
        on<GuildUpdate> {
            logger.info { "onGuildUpdate ${guild.name}" }
            guildNames[guild.id] = guild.name
        }
        
        on<GuildDelete> {
            val guildId = guild.id
            logger.info { "onGuildDelete ${guildNames[guildId]}" }
            
            clearGuild(guildId)
            printUsersInChannels()
        }
        
        fun handleChannelCreateOrUpdate(channel: DiscordChannel) {
            if (channel.type == ChannelType.GuildVoice) {
                val name = channel.name.value ?: return
                val channelGuild = channel.guildId.value ?: return
                logger.info { "onChannelCreate/Update $name" }
                channelNames[channel.id] = name
                channelsInGuild.getOrCreate(channelGuild).add(channel.id)
                guildForChannel[channel.id] = channelGuild
            }
        }
        on<ChannelUpdate> {
            handleChannelCreateOrUpdate(channel)
        }
        on<ChannelCreate> {
            handleChannelCreateOrUpdate(channel)
        }
        
        fun handleGuildMemberAddOrUpdate(guild: Snowflake, user: DiscordUser?) {
            user ?: return
            logger.debug { "onGuildMemberAdd/Update ${guildNames[guild]} ${user.username}" }
            users[user.id] = user
        }
        on<GuildMemberAdd> {
            handleGuildMemberAddOrUpdate(member.guildId, member.user.value)
        }
        on<GuildMemberUpdate> {
            handleGuildMemberAddOrUpdate(member.guildId, member.user)
        }
        
        on<VoiceStateUpdate> {
            val (_, newChannelId, userId) = voiceState
            val oldChannelId = voiceChannelForUser[userId]
            
            if (oldChannelId != newChannelId) {
                logger.info { "onVoiceStateUpdate for ${users[userId]?.username}" }
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
    logger.debug { "set up gateway" }
    logger.info { "starting gateway" }
    
    @OptIn(PrivilegedIntent::class) //
    val gatewayConfiguration = GatewayConfigurationBuilder(
        BOT_TOKEN,
        presence = DiscordPresence(PresenceStatus.Online, false),
        intents = Intents.nonPrivileged + Intent.GuildMembers,
    ).build()
    gateway.start(gatewayConfiguration)
    logger.info { "stopped gateway" }
}

private fun clearGuild(guildId: Snowflake) {
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

var lastMessage: String? = null
suspend fun printUsersInChannels() {
    //        guilds     guildId    channels   channelId  users      userId
    val tree: MutableMap<Snowflake, MutableMap<Snowflake, MutableSet<Snowflake>>> = sortedMapOf()
    
    voiceChannelForUser.forEach { (userId, channelId) ->
        val guildId = guildForChannel[channelId] ?: return@forEach
        if (afkChannels.contains(channelId)) return@forEach
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
                    val nick = nicknames[guildId]?.get(it)
                    if (nick == null) {
                        append(users[it]?.username)
                    } else {
                        append(nick)
                        append(" (")
                        append(users[it]?.username)
                        append(")")
                    }
                    appendLine()
                }
            }
        }
    }.trim()
    
    if (lastMessage != message) {
        lastMessage = message
        if (message.isEmpty()) sendGtkMessage("-")
        else sendGtkMessage(message)
    }
}

fun <T, S : Comparable<S>> MutableMap<T, MutableSet<S>>.getOrCreate(key: T) = getOrPut(key) { sortedSetOf() }
fun <T, M : Comparable<M>, MT> MutableMap<T, MutableMap<M, MT>>.getOrCreate(key: T) = getOrPut(key) { sortedMapOf() }

/**
 * allows launching a block in a suspend-fun without a CoroutineScope available
 */
suspend fun launch(block: suspend CoroutineScope.() -> Unit): Job {
    val job = coroutineContext.job
    return if (job is CoroutineScope) job.launch(coroutineContext, block = block)
    else coroutineScope { this.launch(block = block) } // `this` keyword to avoid recursion
}
