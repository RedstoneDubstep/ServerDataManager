package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class StructuresCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_STRUCTURES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(getAllStructures(ctx), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("structures")
				.then(Commands.literal("count").executes(StructuresCommand::countStructureDataFiles))
				.then(Commands.literal("get")
						.then(Commands.argument("name", ResourceLocationArgument.id()).suggests(SUGGEST_STRUCTURES).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), IntegerArgumentType.getInteger(ctx, "page"), NbtPathArgument.getPath(ctx, "path")))))));
	}

	private static int sendStructureData(CommandContext<CommandSourceStack> ctx, ResourceLocation name, int page, NbtPath path) throws CommandSyntaxException {
		Path generatedFolderPath = ctx.getSource().getServer().getWorldPath(LevelResource.GENERATED_DIR);
		File structureFile = generatedFolderPath.resolve(Path.of(name.getNamespace(), "structures", name.getPath() + ".nbt")).toFile();

		if (!structureFile.isFile()) {
			ctx.getSource().sendFailure(Component.translatable("No structure with name %s could be found", name.toString()));
			return 0;
		}

		CompoundTag structureData;

		try {
			structureData = NbtIo.readCompressed(structureFile.toPath(), NbtAccounter.unlimitedHeap());
		} catch(IOException e) {
			ctx.getSource().sendFailure(Component.translatable("Failed to read structure with name %s", name.toString()));
			return 0;
		}

		Tag foundTag = path != null ? path.get(structureData).iterator().next() : structureData;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(Component.translatable("Data of structure with name %s does not contain any tags at given path", name.toString()));
			return 0;
		}

		if (foundTag instanceof CompoundTag compoundTag)
			TagFormatUtil.removeNestedCollectionTags(compoundTag);

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);
		ctx.getSource().sendSuccess(() -> Component.translatable("Sending data of structure with name %1$s at path \"%2$s\" (%3$s total entries): %4$s", Component.literal(name.toString()).withStyle(ChatFormatting.AQUA), Component.literal(path != null ? path.toString() : "").withStyle(ChatFormatting.AQUA), totalTagEntries, NbtUtils.toPrettyComponent(foundTag)), false);

		if (totalPages > 1)
			ctx.getSource().sendSuccess(() -> Component.translatable("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, TagFormatUtil.getTagSize(foundTag)), false);

		return totalTagEntries;
	}

	private static int countStructureDataFiles(CommandContext<CommandSourceStack> ctx) {
		List<ResourceLocation> structureFiles = getAllStructures(ctx);
		int count = structureFiles.size();
		int namespaces = (int)structureFiles.stream().map(ResourceLocation::getNamespace).distinct().count();

		if (count == 0)
			ctx.getSource().sendFailure(Component.literal("No structures found"));
		else
			ctx.getSource().sendSuccess(() -> Component.translatable("Found %1$s structures with %2$s different namespaces", count, namespaces), false);

		return count;
	}

	private static List<ResourceLocation> getAllStructures(CommandContext<CommandSourceStack> ctx) {
		Path generatedFolderPath = ctx.getSource().getServer().getWorldPath(LevelResource.GENERATED_DIR);
		File generatedFolder = generatedFolderPath.toFile();
		List<ResourceLocation> structures = new ArrayList<>();

		if (generatedFolder.exists() && generatedFolder.listFiles() != null) {
			for (File namespaceFolder : generatedFolder.listFiles()) {
				File structuresFolder = generatedFolderPath.resolve(Paths.get(namespaceFolder.getName(), "structures")).toFile();

				if (structuresFolder.exists() && structuresFolder.listFiles() != null) {
					for (File pathFile : structuresFolder.listFiles()) {
						if (pathFile.getName().endsWith(".nbt"))
							structures.add(new ResourceLocation(namespaceFolder.getName(), pathFile.getName().replace(".nbt", "")));
					}
				}
			}
		}

		return structures;
	}
}
