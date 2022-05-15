package redstonedubstep.mods.serverdataaccessor.commands.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.NbtPathArgument.NbtPath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.ForgeHooks;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class WorldDataCommand {
	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("worlddata")
				.then(Commands.literal("fml")
						.then(Commands.literal("loading-mod-list").executes(ctx -> getFMLWorldData(ctx, "LoadingModList", 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getFMLWorldData(ctx, "LoadingModList", IntegerArgumentType.getInteger(ctx, "page")))))
						.then(Commands.literal("registries").executes(ctx -> getFMLWorldData(ctx, "Registries", 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getFMLWorldData(ctx, "Registries", IntegerArgumentType.getInteger(ctx, "page")))
										.then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(ctx -> getFMLWorldData(ctx, "Registries." + NbtPathArgument.getPath(ctx, "path"), IntegerArgumentType.getInteger(ctx, "page")))))))
				.then(Commands.literal("vanilla").executes(ctx -> getVanillaWorldData(ctx, null, 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getVanillaWorldData(ctx, null, IntegerArgumentType.getInteger(ctx, "page")))
								.then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(ctx -> getVanillaWorldData(ctx, NbtPathArgument.getPath(ctx, "path"), IntegerArgumentType.getInteger(ctx, "page"))))));
	}

	private static int getVanillaWorldData(CommandContext<CommandSourceStack> ctx, NbtPath path, int page) throws CommandSyntaxException {
		WorldData data = ctx.getSource().getServer().getWorldData();
		CompoundTag worldTag = data.createTag(ctx.getSource().getServer().registryAccess(), null);

		Tag foundTag = path != null ? path.get(worldTag).iterator().next() : worldTag;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TextComponent("Vanilla world data does not contain any tags at given path"));
			return 0;
		}

		if (foundTag instanceof CompoundTag compoundTag)
			TagFormatUtil.removeNestedCollectionTags(compoundTag);

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslatableComponent("Sending vanilla world data at path \"%1$s\"" + (totalTagEntries == -1 ? "" : " (%2$s total entries)") + ": %3$s", new TextComponent(path != null ? path.toString() : "").withStyle(ChatFormatting.AQUA), totalTagEntries, NbtUtils.toPrettyComponent(foundTag)), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return 0;
	}

	private static int getFMLWorldData(CommandContext<CommandSourceStack> ctx, String path, int page) {
		WorldData data = ctx.getSource().getServer().getWorldData();
		CompoundTag fmlWorldData = new CompoundTag();
		String[] nodes = path.split("\\.");

		ForgeHooks.writeAdditionalLevelSaveData(data, fmlWorldData);

		Tag foundTag = fmlWorldData.getCompound("fml");
		boolean success = false;
		int depth = 0;

		for (String node : nodes) {
			if (foundTag instanceof CompoundTag && ((CompoundTag)foundTag).contains(node)) {
				foundTag = ((CompoundTag)foundTag).get(node);
				depth++;

				if (depth == nodes.length)
					success = true;
			}
		}

		if (foundTag == null || !success) {
			ctx.getSource().sendFailure(new TranslatableComponent("FML Data with path %s could not be found", path));
			return 0;
		}

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (foundTag instanceof CompoundTag compoundTag)
			TagFormatUtil.removeNestedCollectionTags(compoundTag);
		else if (foundTag instanceof ListTag listTag && path.endsWith(".ids")) //When the id tag gets referenced directly, only show the resource keys as string tags to truncate all the {} and resource ids, since these are not worth showing
			foundTag = TagFormatUtil.formatResourceEntriesToKeys(listTag);

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);

		int pageTagEntries = TagFormatUtil.getTagSize(foundTag);

		ctx.getSource().sendSuccess(new TranslatableComponent("Sending FML world data at path \"%1$s\" (%2$s total entries)" + (totalTagEntries == -1 ? "" : " (%2$s total entries)") + ": %3$s", new TextComponent(path).withStyle(ChatFormatting.AQUA), totalTagEntries, NbtUtils.toPrettyComponent(foundTag)), false);

		if (pageTagEntries >= 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, pageTagEntries), false);

		return pageTagEntries;
	}
}
