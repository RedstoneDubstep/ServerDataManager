package redstonedubstep.mods.serverdatamanager.commands.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fml.loading.FMLPaths;
import redstonedubstep.mods.serverdatamanager.util.FormatUtil;

public class LogsCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_LOG_FILES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(FormatUtil.safeArrayStream(FMLPaths.GAMEDIR.get().resolve("logs").toFile().listFiles()).map(f -> f.getName().replace(".gz", "").replace(".log", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
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

	private static int sendLogFile(CommandContext<CommandSourceStack> ctx, String name, boolean sendInChat, int page) {
		Path logPath = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs",name + ".log"));
		Path logGzPath = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs",name + ".log.gz"));

		if (logPath.toFile().isFile() && logGzPath.toFile().isFile()) {
			ctx.getSource().sendFailure(new TextComponent("Could not find log file with name " + name));
			return 0;
		}

		InputStream log;

		try {
			log = logPath.toFile().isFile() ? new FileInputStream(logPath.toFile()) : new GZIPInputStream(new FileInputStream(logGzPath.toFile()));
		} catch(IOException e) {
			ctx.getSource().sendFailure(new TextComponent("Couldn't find log file " + name));
			return 0;
		}

		List<String> logLines = new BufferedReader(new InputStreamReader(log)).lines().toList();
		int totalLines = logLines.size();

		if (!sendInChat) {
			int listContentSize = logLines.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
			int totalPages = (int)Math.ceil(listContentSize / 260000.0D);
			int currentPage = page > totalPages ? totalPages - 1 : page - 1;
			HoverEvent infoText = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Click to copy log content"));

			logLines = FormatUtil.splitStringsToPage(logLines, currentPage, 260000);

			ClickEvent copyToClipboard = new ClickEvent(Action.COPY_TO_CLIPBOARD, ComponentUtils.formatList(logLines, new TextComponent("\n"), TextComponent::new).getString());

			ctx.getSource().sendSuccess(new TranslatableComponent("Sending log \"%1$s\" (%2$s total lines): %3$s", name, totalLines, new TextComponent("Log content").withStyle(s -> s.applyFormat(ChatFormatting.UNDERLINE).withClickEvent(copyToClipboard).withHoverEvent(infoText))), false);

			if (logLines.size() > 0 && totalPages > 1)
				ctx.getSource().sendSuccess(new TranslatableComponent("Sent page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, logLines.size()), false);
		}
		else {
			int totalPages = (int)Math.ceil(totalLines / 20D);
			int currentPage = page > totalPages ? totalPages - 1 : page - 1;

			logLines = FormatUtil.splitToPage(logLines, currentPage, 20);

			List<Pair<String, String>> splitLogLines = FormatUtil.splitLogLines(logLines);

			ctx.getSource().sendSuccess(new TranslatableComponent("Sending log \"%1$s\" (%2$s total lines): %3$s", name, totalLines, ComponentUtils.formatList(splitLogLines, l -> new TextComponent("\n" + l.getLeft()).withStyle(ChatFormatting.DARK_GRAY).append(new TextComponent(l.getRight()).withStyle(ChatFormatting.GREEN)))), false);

			if (logLines.size() > 0 && totalPages > 1)
				ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, splitLogLines.size()), false);
		}

		return logLines.size();
	}

	private static int countLogFiles(CommandContext<CommandSourceStack> ctx) {
		File[] logFiles = FMLPaths.GAMEDIR.get().resolve(Paths.get("logs")).toFile().listFiles();
		int totalCount = logFiles == null ? 0 : logFiles.length;

		if (totalCount == 0) {
			ctx.getSource().sendFailure(new TextComponent("No log files found"));
			return 0;
		}

		int debugFileCount = (int)Arrays.stream(logFiles).filter(f -> f.getName().contains("debug")).count();

		ctx.getSource().sendSuccess(new TranslatableComponent("Found %1$s log files, %2$s of which are debug log files", totalCount, debugFileCount), false);
		return totalCount;
	}
}
