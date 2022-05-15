[![](http://cf.way2muchnoise.eu/full_serverdataaccessor_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/serverdataaccessor) [![](http://cf.way2muchnoise.eu/versions/For%20MC_serverdataaccessor_all.svg)](https://www.curseforge.com/minecraft/mc-mods/serverdataaccessor/files)

# ServerDataAccessor
Source code for the mod "ServerDataAccessor".

This Forge mod allows server admins to get ingame access to the server's data. Accessing the data is done via two commands and their respective sub-commands, which are described below. The op permission level required for executing these commands is configurable within the mod's serverconfig file.
### Command Usage
This mod adds two commands that are usable by server admins. The former one is for reading the server data, the latter one's purpose is accessing the world data (and can thus also be used in singleplayer). Both commands have subcommands with which specific data can be retrieved.

*/serverdataaccess [subcommand]*:
- *crash-reports* allows you to get access to the content of crash reports, either via sending them into the chat or copying the content to clipboard
- *logs* allows you to get access to the content of log files, either via sending them into the chat or copying the content to clipboard
- *properties* allows you to read the server's properties, with all the property keys and their respective values

*/worlddataaccess [subcommand]*:
- *advancements* allows you to count or read out the advancements of specific players
- *dimensiondata* allows you to read the data of all registered dimensions, sorted by dimension type
- *playerdata* allows you to read the data of specific players
- *region* allows you to get useful information about all region files registered in that world instance (for example, the space they are taking up on disk)
- *statistics* allows you to read and compare either all or single statistics of specific or all players
- *worlddata* allows you to read the data of the world instance

Accessing the server or world data from ingame commands might be useful for server admins that don't have direct file access to the server's files, and additionally, accessing the data via this mod is also a bit more user-friendly than through a file explorer.

This mod does not add support for all types of server or world data, for example managing datapacks or configuration files, since there are either already commands or similar means in Vanilla or Forge that handle this data well enough, or the data is for internal use only and thus irrelevant for server admins.

Finally, this mod is serverside only. Clientside support may be added in the future (in form of reading log files, crash reports etc. in a custom screen) if there is demand.
