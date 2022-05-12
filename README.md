[![](http://cf.way2muchnoise.eu/full_serverdatamanager_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/serverdatamanager) [![](http://cf.way2muchnoise.eu/versions/For%20MC_serverdatamanager_all.svg)](https://www.curseforge.com/minecraft/mc-mods/serverdatamanager/files)

A server utility mod that gives admins ingame insight into the server's data.
### Usage
This mod adds two commands that are usable by server admins. The prior one is for managing the server data, the latter one's purpose is managing the world data (and can thus also be used in singleplayer). Both commands have subcommands with which specific data can be retrieved.

*/serverdata [subcommand]*:
- *crash-reports* allows you to get access to the content of crash reports, either via sending them into the chat or copying the content to clipboard
- *logs* allows you to get access to the content of log files, either via sending them into the chat or copying the content to clipboard
- *properties* allows you to read out the server's properties and their respective values

*/worlddata [subcommand]*:
- *advancements* allows you to count or read out the advancements of specific players
- *dimensiondata* allows you to read the data of all registered dimensions, sorted by dimension type
- *playerdata* allows you to read the data of specific players
- *region* allows you to get useful information about all region files registered in that world instance (for example, the space they are taking up on disk)
- *statistics* allows you to read and compare either all or single statistics of specific or all players
- *worlddata* allows you to read the data of the world instance

Accessing the server- or world data might be useful for server admins that don't have direct file access (via e.g. a server manager) to the server's files.

This mod does not add support for all types of server- or world data, for exammple managing datapacks or configuration files, since there are either already commands or similar means in Vanilla or Forge that handle this data well enough, or the data is for internal use only and thus irrelevant for server admins.

Finally, this mod is serverside only.
