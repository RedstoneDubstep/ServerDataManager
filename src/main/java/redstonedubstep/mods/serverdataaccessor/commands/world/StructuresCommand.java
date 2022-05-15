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

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.NBTPathArgument;
import net.minecraft.command.arguments.NBTPathArgument.NBTPath;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.storage.FolderName;
import redstonedubstep.mods.serverdataaccessor.util.TagFormatUtil;

public class StructuresCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_STRUCTURES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggestResource(getAllStructures(ctx), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("structures")
				.then(Commands.literal("count").executes(StructuresCommand::countStructureDataFiles))
				.then(Commands.literal("get")
						.then(Commands.argument("name", ResourceLocationArgument.id()).suggests(SUGGEST_STRUCTURES).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("path", NBTPathArgument.nbtPath()).executes(ctx -> sendStructureData(ctx, ResourceLocationArgument.getId(ctx, "name"), IntegerArgumentType.getInteger(ctx, "page"), NBTPathArgument.getPath(ctx, "path")))))));
	}

	private static int sendStructureData(CommandContext<CommandSource> ctx, ResourceLocation name, int page, NBTPath path) throws CommandSyntaxException {
		Path generatedFolderPath = ctx.getSource().getServer().getWorldPath(FolderName.GENERATED_DIR);
		File structureFile = generatedFolderPath.resolve(Paths.get(name.getNamespace(), "structures", name.getPath() + ".nbt")).toFile();

		if (!structureFile.isFile()) {
			ctx.getSource().sendFailure(new TranslationTextComponent("No structure with name %s could be found", name));
			return 0;
		}

		CompoundNBT structureData;

		try {
			structureData = CompressedStreamTools.readCompressed(structureFile);
		} catch(IOException e) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Failed to read structure with name %s", name));
			return 0;
		}

		INBT foundTag = path != null ? path.get(structureData).iterator().next() : structureData;

		int totalTagEntries = TagFormatUtil.getTagSize(foundTag);
		int totalPages = (int)Math.ceil(totalTagEntries / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		if (totalTagEntries == 0) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Data of structure with name %s does not contain any tags at given path", name));
			return 0;
		}

		if (foundTag instanceof CompoundNBT)
			TagFormatUtil.removeNestedCollectionTags(((CompoundNBT)foundTag));

		TagFormatUtil.splitTagToPage(foundTag, currentPage, 50);
		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending data of structure with name %1$s at path \"%2$s\" (%3$s total entries): %4$s", new StringTextComponent(name.toString()).withStyle(TextFormatting.AQUA), new StringTextComponent(path != null ? path.toString() : "").withStyle(TextFormatting.AQUA), totalTagEntries, foundTag.getPrettyDisplay()), false);

		if (totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, TagFormatUtil.getTagSize(foundTag)), false);

		return totalTagEntries;
	}

	private static int countStructureDataFiles(CommandContext<CommandSource> ctx) {
		List<ResourceLocation> structureFiles = getAllStructures(ctx);
		int count = structureFiles.size();
		int namespaces = (int)structureFiles.stream().map(ResourceLocation::getNamespace).distinct().count();

		if (count == 0)
			ctx.getSource().sendFailure(new StringTextComponent("No structures found"));
		else
			ctx.getSource().sendSuccess(new TranslationTextComponent("Found %1$s structures with %2$s different namespaces", count, namespaces), false);

		return count;
	}

	private static List<ResourceLocation> getAllStructures(CommandContext<CommandSource> ctx) {
		Path generatedFolderPath = ctx.getSource().getServer().getWorldPath(FolderName.GENERATED_DIR);
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
