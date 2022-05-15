package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.HoverEvent.Action;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraftforge.common.util.FakePlayer;
import redstonedubstep.mods.serverdataaccessor.util.FormatUtil;

public class AdvancementsCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_ADVANCEMENTS = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().hasProgress() && adv.getKey().getRewards().getRecipes().length == 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_RECIPES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().hasProgress() && adv.getKey().getRewards().getRecipes().length > 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("advancements")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.then(Commands.literal("count").executes(ctx -> countAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player"))))
						.then(Commands.literal("advancement").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, IntegerArgumentType.getInteger(ctx, "page"), ResourceLocationArgument.getAdvancement(ctx, "advancement"))))))
						.then(Commands.literal("recipe").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, 1, null))
								.then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, IntegerArgumentType.getInteger(ctx, "page"), null))
										.then(Commands.argument("recipe", ResourceLocationArgument.id()).suggests(SUGGEST_RECIPES).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, IntegerArgumentType.getInteger(ctx, "page"), ResourceLocationArgument.getAdvancement(ctx, "recipe")))))));
	}

	private static int getAdvancementsFrom(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles, boolean recipe, int page, Advancement advancement) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		FakePlayer fakePlayer = new FakePlayer(ctx.getSource().getServer().overworld(), profile);
		PlayerAdvancements playerAdvancements = ctx.getSource().getServer().getPlayerList().getPlayerAdvancements(fakePlayer);
		String advancementReference = recipe ? "recipe advancement" : "advancement";
		Function<Advancement, MutableComponent> advancementFormatter = adv -> new TextComponent("").withStyle(ChatFormatting.AQUA).append(adv.getChatComponent().copy());

		if (advancement == null) {
			Stream<Pair<Advancement, AdvancementProgress>> allAdvancements = playerAdvancements.advancements.entrySet().stream().map(Pair::of).filter(p -> p.getRight().hasProgress()).sorted(Comparator.comparing(p -> p.getRight().getFirstProgressDate() == null ? new Date() : p.getRight().getFirstProgressDate()));
			List<Pair<Advancement, AdvancementProgress>> filteredAdvancements = allAdvancements.filter(p -> recipe ? p.getKey().getRewards().getRecipes().length > 0 : p.getKey().getRewards().getRecipes().length == 0).toList();
			int totalEntries = filteredAdvancements.size();
			int totalPages = (int)Math.ceil(totalEntries / 20D);
			int currentPage = page > totalPages ? totalPages - 1 : page - 1;

			if (filteredAdvancements.isEmpty()) {
				ctx.getSource().sendFailure(new TranslatableComponent("No %1$ss of player %2$s were found", advancementReference, profile.getName()));
				return 0;
			}

			filteredAdvancements = FormatUtil.splitToPage(filteredAdvancements, currentPage, 20);

			ctx.getSource().sendSuccess(new TranslatableComponent("Sending all %1$ss of player %2$s (%3$s): %4$s", advancementReference, profile.getName(), totalEntries, ComponentUtils.formatList(filteredAdvancements, p -> advancementFormatter.apply(p.getLeft()).append(new TranslatableComponent(" (%s%%)", p.getRight().getPercent() * 100).withStyle(ChatFormatting.GRAY)))), false);

			if (filteredAdvancements.size() > 0 && totalPages > 1)
				ctx.getSource().sendSuccess(new TranslatableComponent("Displaying page %1$s out of %2$s with %3$s entries", currentPage + 1, totalPages, filteredAdvancements.size()), false);

			return filteredAdvancements.size();
		}

		AdvancementProgress advancementProgress = playerAdvancements.advancements.get(advancement);
		float progress = advancementProgress.getPercent() * 100;

		if (progress == 0) {
			ctx.getSource().sendFailure(new TranslatableComponent("No progress on %1$s %2$s of player %3$s found", advancementReference, advancementFormatter.apply(advancement), profile.getName()));
			return 0;
		}

		List<Pair<String, CriterionProgress>> criteria = new ArrayList<>();

		advancementProgress.getCompletedCriteria().forEach(s -> criteria.add(Pair.of(s, advancementProgress.getCriterion(s))));
		advancementProgress.getRemainingCriteria().forEach(s -> criteria.add(Pair.of(s, advancementProgress.getCriterion(s))));
		List<Pair<String, CriterionProgress>> sortedCriteria = criteria.stream().sorted(Comparator.comparing(p -> p.getRight().getObtained() == null ? new Date() : p.getRight().getObtained())).toList();

		int completedCriteria = (int)sortedCriteria.stream().filter(p -> p.getValue().isDone()).count();

		ctx.getSource().sendSuccess(new TranslatableComponent("Sending %1$s %2$s of player %3$s: %4$s complete, %5$s out of %6$s criteria completed: %7$s", advancementReference, advancementFormatter.apply(advancement), profile.getName(), new TranslatableComponent("%s%%", progress).withStyle(ChatFormatting.GRAY), completedCriteria, sortedCriteria.size(), ComponentUtils.formatList(sortedCriteria, p -> new TextComponent(p.getLeft()).withStyle(s -> s.applyFormat(p.getRight().isDone() ? ChatFormatting.GREEN : ChatFormatting.DARK_RED).withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new TextComponent("Obtained: " + (p.getRight().isDone() ? p.getRight().getObtained() : "Never"))))))), false);
		return (int)progress;
	}

	private static int countAdvancements(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		FakePlayer fakePlayer = new FakePlayer(ctx.getSource().getServer().overworld(), profile);
		PlayerAdvancements playerAdvancements = ctx.getSource().getServer().getPlayerList().getPlayerAdvancements(fakePlayer);
		int recipeAdvancements = 0;
		int totalAdvancements = 0;

		for (Entry<Advancement, AdvancementProgress> advancement : playerAdvancements.advancements.entrySet()) {
			if (advancement.getValue().getPercent() > 0.0F) {
				if (advancement.getKey().getRewards().getRecipes().length > 0)
					recipeAdvancements++;

				totalAdvancements++;
			}
		}

		ctx.getSource().sendSuccess(new TranslatableComponent("Player %1$s has %2$s completed advancements, %3$s of which are recipe advancements", profile.getName(), totalAdvancements, recipeAdvancements), false);
		return totalAdvancements;
	}

	private static GameProfile ensureOneTarget(Collection<GameProfile> profiles) throws CommandSyntaxException {
		if (profiles.size() > 1)
			throw new SimpleCommandExceptionType(new TextComponent("Targeting multiple players is not supported!")).create();

		return profiles.iterator().next();
	}

	private static PlayerAdvancements getPlayerAdvancements(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		MinecraftServer server = ctx.getSource().getServer();
		FakePlayer fakePlayer = new FakePlayer(server.overworld(), profile);

		return server.getPlayerList().getPlayerAdvancements(fakePlayer);
	}
}
