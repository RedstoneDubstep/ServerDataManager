package redstonedubstep.mods.serverdataaccessor.commands.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtPathArgument.NbtPath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class DimensionDataCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_LEVEL_DATA_FILES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(FormatUtil.safeArrayStream(DimensionArgument.getDimension(ctx, "dimension").getDataStorage().dataFolder.listFiles()).map(f -> f.getName().replace(".dat", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("dimensiondata")
				.then(Commands.argument("dimension", DimensionArgument.dimension())
						.then(Commands.argument("file", StringArgumentType.word()).suggests(SUGGEST_LEVEL_DATA_FILES).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), IntegerArgumentType.getInteger(ctx, "page"), NbtPathArgument.getPath(ctx, "path")))))));
	}

	private static int getLevelData(CommandContext<CommandSourceStack> ctx, ServerLevel level, String filename, int page, NbtPath path) throws CommandSyntaxException {
		DimensionDataStorage dataStorage = level.getDataStorage();
		ResourceLocation levelName = ctx.getArgument("dimension", ResourceLocation.class);
		CompoundTag data;

		try {
			 data = dataStorage.readTagFromDisk(filename, SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getCompound("data");
		} catch(Exception exception) {
			ctx.getSource().sendFailure(new TranslatableComponent("Couldn't read data \"%1$s\" of dimension \"%2$s\"", filename, levelName));
			return 0;
		}

		Tag foundTag = path != null ? path.get(data).iterator().next() : data;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TranslatableComponent("Data \"%1$s\" of dimension \"%2$s\" does not contain any tags at given path", filename, levelName));
			return 0;
		}

		if (foundTag instanceof CompoundTag compoundTag)
			TagFormatUtil.removeNestedCollectionTags(compoundTag);

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslatableComponent("Sending data with name \"%1$s\" at path \"%2$s\" of dimension \"%3$s\" " + (pageTagEntries == -1 ? "" : "(%4$s total entries)") + ": %5$s", new TextComponent(filename).withStyle(ChatFormatting.GRAY), new TextComponent(path != null ? path.toString() : "").withStyle(ChatFormatting.AQUA), levelName, totalTagEntries, NbtUtils.toPrettyComponent(foundTag)), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return pageTagEntries;
	}
}
