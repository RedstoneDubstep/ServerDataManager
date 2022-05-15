package redstonedubstep.mods.serverdataaccessor.commands.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.NBTPathArgument;
import net.minecraft.command.arguments.NBTPathArgument.NBTPath;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraftforge.fml.WorldPersistenceHooks;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class WorldDataCommand {
	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("worlddata")
				.then(Commands.literal("fml")
						.then(Commands.literal("loading-mod-list").executes(ctx -> getFMLWorldData(ctx, 1, "LoadingModList"))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getFMLWorldData(ctx, IntegerArgumentType.getInteger(ctx, "page"), "LoadingModList"))))
						.then(Commands.literal("registries").executes(ctx -> getFMLWorldData(ctx, 1, "Registries"))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getFMLWorldData(ctx, IntegerArgumentType.getInteger(ctx, "page"), "Registries"))
										.then(Commands.argument("path", NBTPathArgument.nbtPath()).executes(ctx -> getFMLWorldData(ctx, IntegerArgumentType.getInteger(ctx, "page"), "Registries." + NBTPathArgument.getPath(ctx, "path")))))))
				.then(Commands.literal("vanilla").executes(ctx -> getVanillaWorldData(ctx, 1, null))
						.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getVanillaWorldData(ctx, IntegerArgumentType.getInteger(ctx, "page"), null))
								.then(Commands.argument("path", NBTPathArgument.nbtPath()).executes(ctx -> getVanillaWorldData(ctx, IntegerArgumentType.getInteger(ctx, "page"), NBTPathArgument.getPath(ctx, "path"))))));
	}

	private static int getVanillaWorldData(CommandContext<CommandSource> ctx, int page, NBTPath path) throws CommandSyntaxException {
		IServerConfiguration data = ctx.getSource().getServer().getWorldData();
		CompoundNBT worldTag = data.createTag(ctx.getSource().getServer().registryAccess(), null);

		INBT foundTag = path != null ? path.get(worldTag).iterator().next() : worldTag;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new StringTextComponent("Vanilla world data does not contain any tags at given path"));
			return 0;
		}

		if (foundTag instanceof CompoundNBT)
			TagFormatUtil.removeNestedCollectionTags(((CompoundNBT)foundTag));

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending vanilla world data at path \"%1$s\"" + (totalTagEntries == -1 ? "" : " (%2$s total entries)") + ": %3$s", new StringTextComponent(path != null ? path.toString() : "").withStyle(TextFormatting.AQUA), totalTagEntries, foundTag.getPrettyDisplay()), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return totalTagEntries;
	}

	private static int getFMLWorldData(CommandContext<CommandSource> ctx, int page, String path) {
		IServerConfiguration data = ctx.getSource().getServer().getWorldData();
		CompoundNBT fmlWorldData = new CompoundNBT();
		String[] nodes = path.split("\\.");

		WorldPersistenceHooks.handleWorldDataSave( ctx.getSource().getServer().storageSource, data, fmlWorldData);

		INBT foundTag = fmlWorldData.getCompound("fml");
		boolean success = false;
		int depth = 0;

		for (String node : nodes) {
			if (foundTag instanceof CompoundNBT && ((CompoundNBT)foundTag).contains(node)) {
				foundTag = ((CompoundNBT)foundTag).get(node);
				depth++;

				if (depth == nodes.length)
					success = true;
			}
		}

		if (foundTag == null || !success) {
			ctx.getSource().sendFailure(new TranslationTextComponent("FML Data with path %s could not be found", path));
			return 0;
		}

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (foundTag instanceof CompoundNBT)
			TagFormatUtil.removeNestedCollectionTags(((CompoundNBT)foundTag));
		else if (foundTag instanceof ListNBT && path.endsWith(".ids")) //When the id tag gets referenced directly, only show the resource keys as string tags to truncate all the {} and resource ids, since these are not worth showing
			foundTag = TagFormatUtil.formatResourceEntriesToKeys(((ListNBT)foundTag));

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending FML world data at path \"%1$s\" (%2$s total entries)" + (totalTagEntries == -1 ? "" : " (%2$s total entries)") + ": %3$s", new StringTextComponent(path).withStyle(TextFormatting.AQUA), totalTagEntries, foundTag.getPrettyDisplay()), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return totalTagEntries;
	}
}
