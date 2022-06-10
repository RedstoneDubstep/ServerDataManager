package redstonedubstep.mods.serverdataaccessor.commands.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;

public class CrashReportsCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_CRASH_REPORTS = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(FormatUtil.safeArrayStream(FMLPaths.GAMEDIR.get().resolve("crash-reports").toFile().listFiles()).map(f -> f.getName().replace(".txt", "")), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("crash-reports")
				.then(Commands.literal("count").executes(CrashReportsCommand::countCrashReports))
				.then(Commands.literal("latest").executes(ctx -> sendCrashReport(ctx, "", false, 1))
						.then(Commands.argument("sendInChat", BoolArgumentType.bool()).executes(ctx -> sendCrashReport(ctx, "", BoolArgumentType.getBool(ctx, "sendInChat"), 1))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendCrashReport(ctx, "", BoolArgumentType.getBool(ctx, "sendInChat"), IntegerArgumentType.getInteger(ctx, "page"))))))
				.then(Commands.literal("all")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_CRASH_REPORTS).executes(ctx -> sendCrashReport(ctx, StringArgumentType.getString(ctx, "name"), false, 1))
								.then(Commands.argument("sendInChat", BoolArgumentType.bool()).executes(ctx -> sendCrashReport(ctx, StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "sendInChat"), 1))
										.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> sendCrashReport(ctx, StringArgumentType.getString(ctx, "name"), BoolArgumentType.getBool(ctx, "sendInChat"), IntegerArgumentType.getInteger(ctx, "page")))))));
	}

	private static int sendCrashReport(CommandContext<CommandSourceStack> ctx, String name, boolean sendInChat, int page) {
		File[] crashReports = FMLPaths.GAMEDIR.get().resolve(Paths.get("crash-reports")).toFile().listFiles();

		if (crashReports != null) {
			String crashReportName = name.isEmpty() ? Arrays.stream(crashReports).filter(File::isFile).min(Comparator.comparing(File::getName)).map(f -> f.getName().replace(".txt", "")).orElse("latest") : name;
			File foundCrashReport = FMLPaths.GAMEDIR.get().resolve(Paths.get("crash-reports", crashReportName + ".txt")).toFile();

			if (!foundCrashReport.isFile()) {
				ctx.getSource().sendFailure(Component.translatable(name.isEmpty() ? "Could not find latest crash report" : "Could not find crash report with name \"%s\"", crashReportName));
				return 0;
			}

			InputStream crashReport;

			try {
				crashReport = Files.newInputStream(foundCrashReport.toPath());
			} catch(IOException e) {
				ctx.getSource().sendFailure(Component.translatable(name.isEmpty() ? "Could not read latest crash report" : "Could not read crash report with name \"%s\"", crashReportName));
				return 0;
			}

			List<String> crashReportLines = new BufferedReader(new InputStreamReader(crashReport)).lines().toList();
			int totalLines = crashReportLines.size();

			if (!sendInChat) {
				int listContentSize = crashReportLines.stream().mapToInt(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
				int totalPages = (int)Math.ceil(listContentSize / 260000.0D);
				int currentPage = page > totalPages ? totalPages - 1 : page - 1;
				HoverEvent infoText = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy crash report content"));

				crashReportLines = FormatUtil.splitStringsToPage(crashReportLines, currentPage, 260000);

				ClickEvent copyToClipboard = new ClickEvent(Action.COPY_TO_CLIPBOARD, ComponentUtils.formatList(crashReportLines, Component.literal("\n"), Component::literal).getString());

				ctx.getSource().sendSuccess(Component.translatable("Sending crash report \"%1$s\" (%2$s total lines): %3$s", crashReportName, totalLines, Component.literal("Crash report content").withStyle(s -> s.applyFormat(ChatFormatting.UNDERLINE).withClickEvent(copyToClipboard).withHoverEvent(infoText))), false);

				if (crashReportLines.size() > 0 && totalPages > 1)
					ctx.getSource().sendSuccess(Component.translatable("Sent page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, crashReportLines.size()), false);
			}
			else {
				int totalPages = (int)Math.ceil(totalLines / 20D);
				int currentPage = page > totalPages ? totalPages - 1 : page - 1;

				crashReportLines = FormatUtil.splitToPage(crashReportLines, currentPage, 20).stream().map(s -> s.replace("\t", "    ")).toList();
				ctx.getSource().sendSuccess(Component.translatable("Sending crash report \"%1$s\" (%2$s total lines): %3$s", crashReportName, totalLines, ComponentUtils.formatList(crashReportLines, s -> Component.literal("\n" + s).withStyle(ChatFormatting.GREEN))), false);

				if (crashReportLines.size() > 0 && totalPages > 1)
					ctx.getSource().sendSuccess(Component.translatable("Displaying page %1$s out of %2$s with %3$s lines", currentPage + 1, totalPages, crashReportLines.size()), false);
			}

			return totalLines;
		}

		return 0;
	}

	private static int countCrashReports(CommandContext<CommandSourceStack> ctx) {
		File[] crashReports = FMLPaths.GAMEDIR.get().resolve(Paths.get("crash-reports")).toFile().listFiles();
		int count = crashReports == null ? 0 : crashReports.length;

		if (count == 0)
			ctx.getSource().sendFailure(Component.literal("No crash reports found"));
		else
			ctx.getSource().sendSuccess(Component.translatable("Found %s crash reports", count), false);

		return count;
	}
}
