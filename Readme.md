# TiloDiscordBot

Notifies you when people join or leave voice channels in Discord for all
guilds where this Bot is a member.

## Dependencies
- currently, only working on Linux with a Gnome based DE (such as Gnome or Cinnamon)
- depends on [`java-gnome`](http://java-gnome.sourceforge.net/) for the notification
- other dependencies are automatically pulled by gradle

## Set-up
- install `java-gnome` for your distribution
- create a file named `start.sh` with the following contents:
  ```sh
  #!/usr/bin/sh
  
  cd "$(dirname "$(realpath "$0")")" || exit 1
  export BOT_TOKEN=""
  
  tty -s
  export RUNNING_INTERACTIVELY="$?"
  
  ./gradlew shadowJar && exec java -jar ./build/libs/TiloDiscordBot-standalone.jar 
  ```
- make the file executable: run `chmod +x start.sh`
- create an application for your Discord Bot at [the Discord Developer Portal](https://discord.com/developers/applications)
- add a bot
- give the bot a name and an app icon if you like
- fill in the BOT_TOKEN in the `start.sh` file ("Click to Reveal Token")
- add the bot to the guilds (servers) where you want to get notified about activities in voice channels
  or ask an admin to add it to the server (only admins are allowed to add bots to servers);
  you can also add the bot to other guilds later, it will automatically pick them up

## Run
- run `start.sh` or put it into autostart
- get notified
