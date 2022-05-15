package redstonedubstep.mods.serverdataaccessor.commands.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.ClickEvent.Action;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;

public class LogsCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_LOG_FILES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggest(FormatUtil.safeArrayStream(FMLPaths.GAMEDIR.get().resolve("logs").toFile().listFiles()).map(f -> f.getName().replace(".gz", "").replace(".log", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("logs")
				.then(Commands.literal("count").executes(LogsCommand::countLogFiles))
				.then(Commands.literal("debug").executes(ctx -> sendLogFile(ctx, "debug", false, 1))
						.then(Commands.argument("sendInChat", BoolArgumentType.bool()).executes(ctx -> sendLogFile(ctx, "debug", BoolArgumentType.getBool(ctx, "sendInChat"), 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendLogFile(ctx, "debug", BoolArgumentType.getBool(ctx, "sendInChat"), IntegerArgumentType.getInteger(ctx, "page"))))))
				.then(Commands.literal("latest").executes(ctx -> sendLogFile(ctx, "latest", false, 1))
						.then(Commands.argument("sendInChat", BoolArgumentType.bool()).executes(ctx -> sendLogFile(ctx, "latest", BoolArgumentType.getBool(ctx, "sendInChat"), 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendLogFile(ctx, "latest", BoolArgumentType.getBool(ctx, "sendInChat"), IntegerArgumentType.getInteger(ctx, "page"))))))
				.then(Commands.literal("all")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_LOG_FILES).executes(ctx -> sendLogFile(ctx, StringArgumentType.getString(ctx, "name"), false, 1))
								.then(Commands.argument("sendInChat", BoolArgumentType.bool()).executes(ctx -> sendLogFile(ctx, StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "sendInChat"), 1))
										.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendLogFile(ctx, StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "sendInChat"), IntegerArgumentType.getInteger(ctx, "page")))))));
	}

	private static int sendLogFile(CommandContext<CommandSource> ctx, String name, boolean sendInChat, int page) {
		Path logPath = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs",name + ".log"));
		Path logGzPath = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs",name + ".log.gz"));

		if (logPath.toFile().isFile() && logGzPath.toFile().isFile()) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Could not find log file with name %s", name));
			return 0;
		}

		InputStream log;

		try {
			log = logPath.toFile().isFile() ? Files.newInputStream(logPath.toFile().toPath()) : new GZIPInputStream(Files.newInputStream(logGzPath.toFile().toPath()));
		} catch(IOException e) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Couldn't find log file %s", name));
			return 0;
		}

		List<String> logLines = new BufferedReader(new InputStreamReader(log)).lines().collect(Collectors.toList());
		int totalLines = logLines.size();

		if (!sendInChat) {
			int listContentSize = logLines.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
			int totalPages = (int)Math.ceil(listContentSize / 260000.0D);
			int currentPage = page > totalPages ? totalPages - 1 : page - 1;
			HoverEvent infoText = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("Click to copy log content"));

			logLines = FormatUtil.splitStringsToPage(logLines, currentPage, 260000);

			ClickEvent copyToClipboard = new ClickEvent(Action.COPY_TO_CLIPBOARD, String.join("\n", logLines));

			ctx.getSource().sendSuccess(new TranslationTextComponent("Sending log \"%1$s\" (%2$s total lines): %3$s", name, totalLines, new StringTextComponent("Log content").withStyle(s -> s.applyFormat(TextFormatting.UNDERLINE).withClickEvent(copyToClipboard).withHoverEvent(infoText))), false);

			if (logLines.size() > 0 && totalPages > 1)
				ctx.getSource().sendSuccess(new TranslationTextComponent("Sent page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, logLines.size()), false);
		}
		else {
			int totalPages = (int)Math.ceil(totalLines / 20D);
			int currentPage = page > totalPages ? totalPages - 1 : page - 1;

			logLines = FormatUtil.splitToPage(logLines, currentPage, 20);

			List<Pair<String, String>> splitLogLines = FormatUtil.splitLogLines(logLines);

			ctx.getSource().sendSuccess(new TranslationTextComponent("Sending log \"%1$s\" (%2$s total lines): %3$s", name, totalLines, TextComponentUtils.formatList(splitLogLines, l -> new StringTextComponent("\n" + l.getLeft()).withStyle(TextFormatting.DARK_GRAY).append(new StringTextComponent(l.getRight()).withStyle(TextFormatting.GREEN)))), false);

			if (logLines.size() > 0 && totalPages > 1)
				ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, splitLogLines.size()), false);
		}

		return totalLines;
	}

	private static int countLogFiles(CommandContext<CommandSource> ctx) {
		File[] logFiles = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs")).toFile().listFiles();
		int totalCount = logFiles == null ? 0 : logFiles.length;

		if (totalCount == 0) {
			ctx.getSource().sendFailure(new StringTextComponent("No log files found"));
			return 0;
		}

		int debugFileCount = (int)Arrays.stream(logFiles).filter(f -> f.getName().contains("debug")).count();

		ctx.getSource().sendSuccess(new TranslationTextComponent("Found %1$s log files, %2$s of which are debug log files", totalCount, debugFileCount), false);
		return totalCount;
	}
}
