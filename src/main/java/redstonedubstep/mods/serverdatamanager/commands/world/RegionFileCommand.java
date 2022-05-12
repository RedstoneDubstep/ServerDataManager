package redstonedubstep.mods.serverdatamanager.commands.world;

import java.io.File;
import java.nio.file.Paths;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fml.loading.FMLPaths;

public class RegionFileCommand {
	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("regions")
				.then(Commands.literal("entities").executes(ctx -> getRegionFolderInformation(ctx, "entities")))
				.then(Commands.literal("points-of-interests").executes(ctx -> getRegionFolderInformation(ctx, "poi")))
				.then(Commands.literal("region").executes(ctx -> getRegionFolderInformation(ctx, "region")));
	}

	private static int getRegionFolderInformation(CommandContext<CommandSourceStack> ctx, String path) {
		File regionFolder = FMLPaths.GAMEDIR.get().resolve(Paths.get("world", path)).toFile();
		File[] regionFiles = regionFolder.listFiles();
		long totalSize = 0;

		if (regionFiles == null || regionFiles.length == 0) {
			ctx.getSource().sendFailure(new TranslatableComponent("Could not find region files in folder \"%s\"", path));
			return 0;
		}

		int notEmptyFiles = 0;

		for (File regionFile : regionFiles) {
			if (regionFile.isFile() && regionFile.length() > 0) {
				notEmptyFiles++;
				totalSize += regionFile.length();
			}
		}

		ctx.getSource().sendSuccess(new TranslatableComponent("Found %1$s region files of type \"%2$s\", of which %3$s are empty, with a total size of %4$s kilobytes", regionFiles.length, path, regionFiles.length - notEmptyFiles, totalSize / 1024), false);
		return regionFiles.length;
	}
}
