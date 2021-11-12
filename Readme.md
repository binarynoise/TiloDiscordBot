# TiloDiscordBot

Notifies you when people join or leave voice channels in Discord for all
Guild where this Bot is a member (needs to be added by an admin).

## Dependencies
- currently, only working on Linux with a Gnome based DE (such as Gnome or Cinnamon)
- depends on [`java-gnome`](http://java-gnome.sourceforge.net/) for the notification
- other dependencies are automatically pulled by gradle

## Building
- Clone this repository
- Build using IntelliJ
- Add run task for the main function in Main.kt

## Set-up
- create an application for your Discord Bot at [the Discord Developer Portal](https://discord.com/developers/applications)
- add a bot
- edit the run task and add an environment variable with the bot token as `BOT_TOKEN`
- give the bot a name and an app icon if you like
- add the bot to the servers where you want to get notified about activities in voice channels
  or ask an admin to add it to the server (only admins are allowed to add bots to servers)

## Run
- run the bot with the run task
- get notified
