package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
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
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraftforge.common.util.FakePlayer;

public class AdvancementsCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_ADVANCEMENTS = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().getPercent() > 0 && adv.getKey().getRewards().getRecipes().length == 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_RECIPES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().getPercent() > 0 && adv.getKey().getRewards().getRecipes().length > 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("advancements")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.then(Commands.literal("count").executes(ctx -> countAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player"))))
						.then(Commands.literal("advancement").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, null))
								.then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, ResourceLocationArgument.getAdvancement(ctx, "advancement")))))
						.then(Commands.literal("recipe").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, null))
								.then(Commands.argument("recipe", ResourceLocationArgument.id()).suggests(SUGGEST_RECIPES).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, ResourceLocationArgument.getAdvancement(ctx, "recipe"))))));
	}

	private static int getAdvancementsFrom(CommandContext<CommandSourceStack> ctx, Collection<GameProfile> profiles, boolean recipe, Advancement advancement) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		FakePlayer fakePlayer = new FakePlayer(ctx.getSource().getServer().overworld(), profile);
		PlayerAdvancements playerAdvancements = ctx.getSource().getServer().getPlayerList().getPlayerAdvancements(fakePlayer);

		if (advancement == null) {
			Stream<Pair<Advancement, Integer>> allAdvancements = playerAdvancements.advancements.entrySet().stream().map(e -> Pair.of(e.getKey(), (int)(e.getValue().getPercent() * 100))).filter(p -> p.getRight() > 0);
			List<Pair<Advancement, Integer>> filteredAdvancements = allAdvancements.filter(p -> recipe ? p.getKey().getRewards().getRecipes().length > 0 : p.getKey().getRewards().getRecipes().length == 0).toList();

			if (filteredAdvancements.isEmpty()) {
				ctx.getSource().sendFailure(new TranslatableComponent("No completed %1$sadvancements of player %2$s were found", recipe ? "recipe " : "", profile.getName()));
				return 0;
			}

			ctx.getSource().sendSuccess(new TranslatableComponent("Sending all completed %1$sadvancements of player %2$s (%3$s): %4$s", recipe ? "recipe " : "", profile.getName(), filteredAdvancements.size(), ComponentUtils.formatList(filteredAdvancements, p -> p.getKey().getChatComponent().copy().append(new TranslatableComponent(" (%s%%)", p.getRight()).withStyle(ChatFormatting.GRAY)))), false);
			return filteredAdvancements.size();
		}

		AdvancementProgress advancementProgress = playerAdvancements.advancements.get(advancement);
		int progress = (int)(advancementProgress.getPercent() * 100);

		if (progress == 0) {
			ctx.getSource().sendFailure(new TranslatableComponent("No progress on %1$sadvancement %2$s of player %3$s found", recipe ? "recipe " : "", advancement.getChatComponent(), profile.getName()));
			return 0;
		}

		Map<String, CriterionProgress> criteria = new HashMap<>();

		advancementProgress.getRemainingCriteria().forEach(s -> criteria.put(s, advancementProgress.getCriterion(s)));
		advancementProgress.getCompletedCriteria().forEach(s -> criteria.put(s, advancementProgress.getCriterion(s)));
		ctx.getSource().sendSuccess(new TranslatableComponent("Sending %1$sadvancement %2$s of player %3$s: %4$s%% complete, criteria: %5$s", recipe ? "recipe " : "", advancement.getChatComponent(), profile.getName(), progress, ComponentUtils.formatList(criteria.keySet(), n -> new TextComponent(n).withStyle(s -> s.applyFormat(criteria.get(n).isDone() ? ChatFormatting.GREEN : ChatFormatting.DARK_RED).withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new TextComponent("Obtained: " + (criteria.get(n).isDone() ? criteria.get(n).getObtained() : "Never"))))))), false);
		return progress;
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
