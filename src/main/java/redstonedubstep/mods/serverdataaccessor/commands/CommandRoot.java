package redstonedubstep.mods.serverdataaccessor.commands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import redstonedubstep.mods.serverdataaccessor.SDMConfig;
import redstonedubstep.mods.serverdataaccessor.commands.server.CrashReportsCommand;
import redstonedubstep.mods.serverdataaccessor.commands.server.LogsCommand;
import redstonedubstep.mods.serverdataaccessor.commands.server.ServerPropertiesCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.AdvancementsCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.DimensionDataCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.PlayerDataCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.RegionFileCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.StatisticsCommand;
import redstonedubstep.mods.serverdataaccessor.commands.world.WorldDataCommand;

public class CommandRoot {
	public static void registerServerDataCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("serverdata").requires(player -> player.hasPermission(SDMConfig.CONFIG.serverdataCommandPermissionLevel.get()))
				.then(CrashReportsCommand.register())
				.then(LogsCommand.register())
				.then(ServerPropertiesCommand.register()));
	}

	public static void registerWorldDataCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("worlddata").requires(player -> player.hasPermission(SDMConfig.CONFIG.worlddataCommandPermissionLevel.get()))
				.then(AdvancementsCommand.register())
				.then(DimensionDataCommand.register())
				.then(PlayerDataCommand.register())
				.then(RegionFileCommand.register())
				.then(StatisticsCommand.register())
				.then(WorldDataCommand.register()));
	}
}
