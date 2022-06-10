package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.io.File;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

public class RegionFileCommand {
	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("regions")
				.then(Commands.literal("entities").executes(ctx -> getRegionFolderInformation(ctx, "entities")))
				.then(Commands.literal("points-of-interests").executes(ctx -> getRegionFolderInformation(ctx, "poi")))
				.then(Commands.literal("region").executes(ctx -> getRegionFolderInformation(ctx, "region")));
	}

	private static int getRegionFolderInformation(CommandContext<CommandSourceStack> ctx, String path) {
		File regionFolder = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve(path).toFile();
		File[] regionFiles = regionFolder.listFiles();
		long totalSize = 0;

		if (regionFiles == null || regionFiles.length == 0) {
			ctx.getSource().sendFailure(Component.translatable("Could not find region files in folder \"%s\"", path));
			return 0;
		}

		int notEmptyFiles = 0;

		for (File regionFile : regionFiles) {
			if (regionFile.isFile() && regionFile.length() > 0) {
				notEmptyFiles++;
				totalSize += regionFile.length();
			}
		}

		ctx.getSource().sendSuccess(Component.translatable("Found %1$s region files of type \"%2$s\", of which %3$s are empty, with a total size of %4$s kilobytes", regionFiles.length, path, regionFiles.length - notEmptyFiles, totalSize / 1024), false);
		return regionFiles.length;
	}
}
