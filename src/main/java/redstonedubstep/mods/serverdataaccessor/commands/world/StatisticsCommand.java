package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatisticsManager;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.event.HoverEvent.Action;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.registries.ForgeRegistries;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;
import redstonedubstep.mods.serverdataaccessor.util.StatUtil;

public class StatisticsCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_STAT_TYPES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggestResource(ForgeRegistries.STAT_TYPES.getKeys(), suggestionsBuilder);
	private static final SuggestionProvider<CommandSource> SUGGEST_STATS = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggestResource(StatUtil.getStatType(ctx, "type").getRegistry().keySet(), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("statistics")
				.then(Commands.literal("players")
						.then(Commands.argument("players", GameProfileArgument.gameProfile())
								.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getStatFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), 1, null))
										.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getStatFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), IntegerArgumentType.getInteger(ctx, "page"), null))
												.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getStatFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), IntegerArgumentType.getInteger(ctx, "page"), ResourceLocationArgument.getId(ctx, "id"))))))))
				.then(Commands.literal("global")
						.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getStatFrom(ctx, null, StatUtil.getStatType(ctx, "type"), 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getStatFrom(ctx, null, StatUtil.getStatType(ctx, "type"), IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getStatFrom(ctx, null, StatUtil.getStatType(ctx, "type"), IntegerArgumentType.getInteger(ctx, "page"), ResourceLocationArgument.getId(ctx, "id")))))))
				.then(Commands.literal("compare")
						.then(Commands.literal("max")
								.then(Commands.literal("players")
										.then(Commands.argument("players", GameProfileArgument.gameProfile())
												.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getSpecificStat(ctx, true, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), null))
														.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getSpecificStat(ctx, true, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), ResourceLocationArgument.getId(ctx, "id")))))))
								.then(Commands.literal("global")
										.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getSpecificStat(ctx, true, null, StatUtil.getStatType(ctx, "type"), null))
												.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getSpecificStat(ctx, true, null, StatUtil.getStatType(ctx, "type"), ResourceLocationArgument.getId(ctx, "id")))))))
						.then(Commands.literal("min")
								.then(Commands.literal("players")
										.then(Commands.argument("players", GameProfileArgument.gameProfile())
												.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getSpecificStat(ctx, false, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), null))
														.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getSpecificStat(ctx, false, GameProfileArgument.getGameProfiles(ctx, "players"), StatUtil.getStatType(ctx, "type"), ResourceLocationArgument.getId(ctx, "id")))))))
								.then(Commands.literal("global")
										.then(Commands.argument("type", ResourceLocationArgument.id()).suggests(SUGGEST_STAT_TYPES).executes(ctx -> getSpecificStat(ctx, false, null, StatUtil.getStatType(ctx, "type"), null))
												.then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUGGEST_STATS).executes(ctx -> getSpecificStat(ctx, false, null, StatUtil.getStatType(ctx, "type"), ResourceLocationArgument.getId(ctx, "id"))))))));
	}

	private static int getStatFrom(CommandContext<CommandSource> ctx, Collection<GameProfile> profiles, StatType<?> statType, int page, ResourceLocation statId) throws CommandSyntaxException {
		File statsFolder = ctx.getSource().getServer().getWorldPath(FolderName.PLAYER_STATS_DIR).toFile();
		Collection<?> statSource = profiles != null ? profiles : FormatUtil.safeArrayStream(statsFolder.listFiles()).collect(Collectors.toList());
		StatisticsManager statsCollection = StatUtil.mergeStats(statSource, statType, Optional.ofNullable(statId), ctx.getSource().getServer());
		Pair<Stat<?>, Integer> stat = StatUtil.getStatFromCollection(statsCollection, statType, statId);
		Map<Stat<?>, Integer> statMap = stat != null ? new HashMap<>() : statsCollection.stats;
		TranslationTextComponent playerReference = new TranslationTextComponent(profiles == null ? "all %2$s players" : (profiles.size() == 1 ? "player %1$s" : "%2$s players"), Optional.ofNullable(profiles).map(p -> p.iterator().next().getName()).orElse(""), statSource.size());
		IFormattableTextComponent statTypeComponent = new TranslationTextComponent(StatUtil.getStatTypeTranslation(statType));
		
		if (stat != null)
			statMap.put(stat.getLeft(), stat.getRight());
		
		if (statMap.isEmpty()) {
			ctx.getSource().sendFailure(new TranslationTextComponent("No statistics of %1$s of type \"%2$s\" were found", playerReference, statTypeComponent));
			return 0;
		}

		if (statMap.size() == 1 && stat != null) {
			ITextComponent statComponent = new TranslationTextComponent(StatUtil.getStatTranslationKey(stat.getKey())).withStyle(TextFormatting.GRAY).withStyle(s -> s.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new StringTextComponent(stat.getKey().getName())))).append(": ").append(new StringTextComponent(StatUtil.format(stat.getKey(), stat.getValue())).withStyle(TextFormatting.AQUA));

			ctx.getSource().sendSuccess(new TranslationTextComponent("Sending statistic %1$s of %2$s of type \"%3$s\": %4$s", StatUtil.getStatTranslationKey(stat.getKey()), playerReference, statTypeComponent, statComponent), false);
			return stat.getValue();
		}

		int totalPages = (int)Math.ceil(statMap.size() / 50D);
		int currentPage = page > totalPages ? totalPages - 1 : page - 1;

		Map<Stat<?>, Integer> statsOnPage = new LinkedHashMap<>();
		FormatUtil.splitToPage(statMap.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).collect(Collectors.toList()), currentPage, 50).stream().sorted(Comparator.comparing(p -> new TranslationTextComponent(StatUtil.getStatTranslationKey(p.getLeft())).getString())).forEach(p -> statsOnPage.put(p.getKey(), p.getValue()));

		ITextComponent statList = TextComponentUtils.formatList(statsOnPage.entrySet(), p -> new TranslationTextComponent(StatUtil.getStatTranslationKey(p.getKey())).withStyle(TextFormatting.GRAY).withStyle(s -> s.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new StringTextComponent(p.getKey().getName())))).append(": ").append(new StringTextComponent(StatUtil.format(p.getKey(), p.getValue())).withStyle(TextFormatting.AQUA)));

		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending statistics of %1$s of type \"%2$s\" with %3$s entries: %4$s", playerReference, statTypeComponent, statsCollection.stats.size(), statList), false);

		if (statsOnPage.size() > 0 && totalPages > 1)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, statsOnPage.size()), false);

		return statsCollection.stats.size();
	}

	private static int getSpecificStat(CommandContext<CommandSource> ctx, boolean max, Collection<GameProfile> profiles, StatType<?> statType, ResourceLocation statId) throws CommandSyntaxException {
		if (profiles != null && profiles.size() == 1)
			throw new SimpleCommandExceptionType(new StringTextComponent("Multiple players must be targeted for comparing!")).create();

		MinecraftServer server = ctx.getSource().getServer();
		File statsFolder = server.getWorldPath(FolderName.PLAYER_STATS_DIR).toFile();
		Collection<?> statSource = profiles != null ? profiles : FormatUtil.safeArrayStream(statsFolder.listFiles()).collect(Collectors.toList());;
		Stat<?> stat = null;
		Pair<String, Integer> best = null;
		TranslationTextComponent playerReference = new TranslationTextComponent(profiles == null ? "all %1$s players" : "%1$s players", statSource.size());
		IFormattableTextComponent statTypeComponent = new TranslationTextComponent(StatUtil.getStatTypeTranslation(statType));

		//Compares each statsHolder's stats, picks the best (either the highest or lowest) value and stores it and the corresponding player's name to the variable
		for (Object statsHolder : statSource) {
			ServerStatisticsManager profileStats = StatUtil.getStatsFromSource(statsHolder, server);
			String playerIdentifier = statsHolder instanceof File ? Optional.ofNullable(server.getProfileCache().get(UUID.fromString(((File)statsHolder).getName().replace(".json", "")))).map(GameProfile::getName).orElse(((File)statsHolder).getName().replace(".json", "")) : ((GameProfile)statsHolder).getName();

			if (profileStats != null) {
				Map<Stat<?>, Integer> statsOfType = profileStats.stats.object2IntEntrySet().stream().filter(e -> e.getKey().getType().equals(statType)).collect(Collectors.toMap(Map.Entry::getKey, Entry::getIntValue));
				int size = statsOfType.size();

				if (statId == null && (best == null || (max ? size > best.getRight() : size < best.getRight())))
					best = Pair.of(playerIdentifier, size);
				else if (statId != null) {
					for (Map.Entry<Stat<?>, Integer> statEntry : statsOfType.entrySet()) {
						int statValue = statEntry.getValue();

						if (statEntry.getKey().getValue().equals(statType.getRegistry().get(statId)) && (best == null || (max ? statValue > best.getRight() : statValue < best.getRight()))) {
							best = Pair.of(playerIdentifier, statValue);
							stat = statEntry.getKey();
						}
					}
				}
			}
		}

		if (best == null) {
			if (statId == null)
				ctx.getSource().sendFailure(new TranslationTextComponent("No statistics of %1$s of type \"%2$s\" were found", playerReference, statTypeComponent));
			else
				ctx.getSource().sendFailure(new TranslationTextComponent("Couldn't find statistic of %1$s with id \"%2$s\" of type \"%3$s\"", playerReference, statId, statTypeComponent));

			return 0;
		}

		String statName = stat == null ? "" : stat.getName();

		if (statId == null)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Player with the " + (max ? "highest" : "lowest") + " number of statistic entries (%1$s) of type \"%2$s\" out of %3$s is %4$s", best.getRight(), statTypeComponent, playerReference, best.getLeft()), false);
		else if (stat != null)
			ctx.getSource().sendSuccess(new TranslationTextComponent("Player with the " + (max ? "highest" : "lowest") + " value (%1$s) of statistic \"%2$s\" of type \"%3$s\" out of %4$s is %5$s", StatUtil.format(stat, best.getRight()), new TranslationTextComponent(StatUtil.getStatTranslationKey(stat)).withStyle(TextFormatting.GRAY).withStyle(s -> s.withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new StringTextComponent(statName)))), statTypeComponent, playerReference, best.getLeft()), false);

		return best.getRight();
	}
}
