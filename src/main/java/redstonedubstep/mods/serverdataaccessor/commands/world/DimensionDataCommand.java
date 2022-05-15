package redstonedubstep.mods.serverdataaccessor.commands.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.DimensionArgument;
import net.minecraft.command.arguments.NBTPathArgument;
import net.minecraft.command.arguments.NBTPathArgument.NBTPath;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class DimensionDataCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_LEVEL_DATA_FILES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggest(FormatUtil.safeArrayStream(DimensionArgument.getDimension(ctx, "dimension").getDataStorage().dataFolder.listFiles()).map(f -> f.getName().replace(".dat", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("dimensiondata")
				.then(Commands.argument("dimension", DimensionArgument.dimension())
						.then(Commands.argument("file", StringArgumentType.word()).suggests(SUGGEST_LEVEL_DATA_FILES).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("path", NBTPathArgument.nbtPath()).executes(ctx -> getLevelData(ctx, DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "file"), IntegerArgumentType.getInteger(ctx, "page"), NBTPathArgument.getPath(ctx, "path")))))));
	}

	private static int getLevelData(CommandContext<CommandSource> ctx, ServerWorld level, String filename, int page, NBTPath path) throws CommandSyntaxException {
		DimensionSavedDataManager dataStorage = level.getDataStorage();
		ResourceLocation levelName = ctx.getArgument("dimension", ResourceLocation.class);
		CompoundNBT data;

		try {
			 data = dataStorage.readTagFromDisk(filename, SharedConstants.getCurrentVersion().getWorldVersion()).getCompound("data");
		} catch(Exception exception) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Couldn't read data \"%1$s\" of dimension \"%2$s\"", filename, levelName));
			return 0;
		}

		INBT foundTag = path != null ? path.get(data).iterator().next() : data;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Data \"%1$s\" of dimension \"%2$s\" does not contain any tags at given path", filename, levelName));
			return 0;
		}

		if (foundTag instanceof CompoundNBT)
			TagFormatUtil.removeNestedCollectionTags(((CompoundNBT)foundTag));

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending data with name \"%1$s\" at path \"%2$s\" of dimension \"%3$s\" " + (pageTagEntries == -1 ? "" : "(%4$s total entries)") + ": %5$s", new StringTextComponent(filename).withStyle(TextFormatting.GRAY), new StringTextComponent(path != null ? path.toString() : "").withStyle(TextFormatting.AQUA), levelName, totalTagEntries, foundTag.getPrettyDisplay()), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return pageTagEntries;
	}
}
