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

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtPathArgument.NbtPath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.storage.LevelResource;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class PlayerDataCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_PLAYER_DATA_FILES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(FormatUtil.safeArrayStream(ctx.getSource().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile().listFiles()).filter(f -> f.getName().endsWith(".dat")).map(f -> f.getName().replace(".dat", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("playerdata")
				.then(Commands.literal("count").executes(PlayerDataCommand::countPlayerDataFiles))
				.then(Commands.literal("get")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_PLAYER_DATA_FILES).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), null, 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), null, IntegerArgumentType.getInteger(ctx, "page")))
										.then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(ctx -> sendPlayerData(ctx, StringArgumentType.getString(ctx, "name"), NbtPathArgument.getPath(ctx, "path"), IntegerArgumentType.getInteger(ctx, "page")))))));
	}

	private static int sendPlayerData(CommandContext<CommandSourceStack> ctx, String name, NbtPath path, int page) throws CommandSyntaxException {
		File[] playerDataFiles = ctx.getSource().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile().listFiles();

		if (playerDataFiles == null || playerDataFiles.length == 0) {
			ctx.getSource().sendFailure(new TextComponent("No playerdata files could be found"));
			return 0;
		}

		File playerDataFile;
		String fileName;

		if (name.length() != 36) { //if the name input is not a valid UUID, try to treat it as a player name
			UUID uuid = ctx.getSource().getServer().getProfileCache().get(name).map(GameProfile::getId).orElse(null);

			if (uuid != null) {
				playerDataFile = Arrays.stream(playerDataFiles).filter(f -> f.getName().contains(uuid.toString()) && f.getName().endsWith(".dat")).findFirst().orElse(null);
				fileName = uuid.toString();
			}
			else {
				ctx.getSource().sendFailure(new TranslatableComponent("UUID of player with name \"%s\" could not be determined", name));
				return 0;
			}
		}
		else {
			playerDataFile = Arrays.stream(playerDataFiles).filter(f -> f.getName().contains(name) && f.getName().endsWith(".dat")).findFirst().orElse(null);
			fileName = name;
		}

		if (playerDataFile == null) {
			ctx.getSource().sendFailure(new TranslatableComponent("No playerdata file of player with UUID \"%s\" could be found", fileName));
			return 0;
		}

		CompoundTag playerData;

		try {
			playerData = NbtIo.readCompressed(playerDataFile);
		} catch(IOException e) {
			ctx.getSource().sendFailure(new TranslatableComponent("Failed to read playerdata of player with UUID \"%s\"", fileName));
			return 0;
		}

		Tag foundTag = path != null ? path.get(playerData).iterator().next() : playerData;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TranslatableComponent("Playerdata of player with UUID \"%s\" does not contain any tags at given path", fileName));
			return 0;
		}

		if (foundTag instanceof CompoundTag compoundTag)
			TagFormatUtil.removeNestedCollectionTags(compoundTag);

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslatableComponent("Sending playerdata of player with UUID \"%1$s\" at path \"%2$s\"" + (pageTagEntries == -1 ? "" : " (%3$s total entries)") + ": %4$s", new TextComponent(fileName).withStyle(ChatFormatting.AQUA), new TextComponent(path != null ? path.toString() : "").withStyle(ChatFormatting.AQUA), totalTagEntries, NbtUtils.toPrettyComponent(foundTag)), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return pageTagEntries;
	}

	private static int countPlayerDataFiles(CommandContext<CommandSourceStack> ctx) {
		File[] playerDataFiles = ctx.getSource().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile().listFiles();
		int count = playerDataFiles == null ? 0 : (int)Arrays.stream(playerDataFiles).filter(f -> f.getName().endsWith(".dat")).count();

		if (count == 0)
			ctx.getSource().sendFailure(new TextComponent("No playerdata files found"));
		else
			ctx.getSource().sendSuccess(new TranslatableComponent("Found %s playerdata files", count), false);

		return count;
	}
}
