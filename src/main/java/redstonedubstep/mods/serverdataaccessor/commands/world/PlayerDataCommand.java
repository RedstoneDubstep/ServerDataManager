package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.NBTPathArgument;
import net.minecraft.command.arguments.NBTPathArgument.NBTPath;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.storage.FolderName;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class PlayerDataCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_PLAYER_DATA_FILES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggest(FormatUtil.safeArrayStream(ctx.getSource().getServer().getWorldPath(FolderName.PLAYER_DATA_DIR).toFile().listFiles()).filter(f -> f.getName().endsWith(".dat")).map(f -> f.getName().replace(".dat", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("playerdata")
				.then(Commands.literal("count").executes(PlayerDataCommand::countPlayerDataFiles))
				.then(Commands.literal("get")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_PLAYER_DATA_FILES).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), null, 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), null, IntegerArgumentType.getInteger(ctx, "page")))
										.then(Commands.argument("path", NBTPathArgument.nbtPath()).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), NBTPathArgument.getPath(ctx, "path"), IntegerArgumentType.getInteger(ctx, "page")))))));
	}

	private static int sendPlayerData(CommandContext<CommandSource> ctx, String name, NBTPath path, int page) throws CommandSyntaxException {
		File[] playerDataFiles = ctx.getSource().getServer().getWorldPath(FolderName.PLAYER_DATA_DIR).toFile().listFiles();

		if (playerDataFiles == null || playerDataFiles.length == 0) {
			ctx.getSource().sendFailure(new StringTextComponent("No playerdata files could be found"));
			return 0;
		}

		File playerDataFile;
		String fileName;

		if (name.length() != 36) { //if the name input is not a valid UUID, try to treat it as a player name
			GameProfile profile = ctx.getSource().getServer().getProfileCache().get(name);
			UUID uuid = profile != null ? profile.getId() : null;

			if (uuid != null) {
				playerDataFile = Arrays.stream(playerDataFiles).filter(f -> f.getName().contains(uuid.toString()) && f.getName().endsWith(".dat")).findFirst().orElse(null);
				fileName = uuid.toString();
			}
			else {
				ctx.getSource().sendFailure(new TranslationTextComponent("UUID of player with name \"%s\" could not be determined", name));
				return 0;
			}
		}
		else {
			playerDataFile = Arrays.stream(playerDataFiles).filter(f -> f.getName().contains(name) && f.getName().endsWith(".dat")).findFirst().orElse(null);
			fileName = name;
		}

		if (playerDataFile == null) {
			ctx.getSource().sendFailure(new TranslationTextComponent("No playerdata file of player with UUID \"%s\" could be found", fileName));
			return 0;
		}

		CompoundNBT playerData;

		try {
			playerData = CompressedStreamTools.readCompressed(playerDataFile);
		} catch(IOException e) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Failed to read playerdata of player with UUID \"%s\"", fileName));
			return 0;
		}

		INBT foundTag = path != null ? path.get(playerData).iterator().next() : playerData;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Playerdata of player with UUID \"%s\" does not contain any tags at given path", fileName));
			return 0;
		}

		if (foundTag instanceof CompoundNBT)
			TagFormatUtil.removeNestedCollectionTags(((CompoundNBT)foundTag));

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending playerdata of player with UUID \"%1$s\" at path \"%2$s\"" + (pageTagEntries == -1 ? "" : " (%3$s total entries)") + ": %4$s", new StringTextComponent(fileName).withStyle(TextFormatting.AQUA), new StringTextComponent(path != null ? path.toString() : "").withStyle(TextFormatting.AQUA), totalTagEntries, foundTag.getPrettyDisplay()), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return pageTagEntries;
	}

	private static int countPlayerDataFiles(CommandContext<CommandSource> ctx) {
		File[] playerDataFiles = ctx.getSource().getServer().getWorldPath(FolderName.PLAYER_DATA_DIR).toFile().listFiles();
		int count = playerDataFiles == null ? 0 : (int)Arrays.stream(playerDataFiles).filter(f -> f.getName().endsWith(".dat")).count();

		if (count == 0)
			ctx.getSource().sendFailure(new StringTextComponent("No playerdata files found"));
		else
			ctx.getSource().sendSuccess(new TranslationTextComponent("Found %s playerdata files", count), false);

		return count;
	}
}
