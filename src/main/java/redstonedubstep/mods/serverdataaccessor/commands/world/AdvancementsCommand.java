package redstonedubstep.mods.serverdataaccessor.commands.world;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.event.HoverEvent.Action;
import net.minecraftforge.common.util.FakePlayer;

public class AdvancementsCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_ADVANCEMENTS = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().hasProgress() && adv.getKey().getRewards().recipes.length == 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);
	private static final SuggestionProvider<CommandSource> SUGGEST_RECIPES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggestResource(getPlayerAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player")).advancements.entrySet().stream().filter(adv -> adv.getValue().hasProgress() && adv.getKey().getRewards().recipes.length > 0).map(adv -> adv.getKey().getId()), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("advancements")
				.then(Commands.argument("player", GameProfileArgument.gameProfile())
						.then(Commands.literal("count").executes(ctx -> countAdvancements(ctx, GameProfileArgument.getGameProfiles(ctx, "player"))))
						.then(Commands.literal("advancement").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, null))
								.then(Commands.argument("advancement", ResourceLocationArgument.id()).suggests(SUGGEST_ADVANCEMENTS).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), false, ResourceLocationArgument.getAdvancement(ctx, "advancement")))))
						.then(Commands.literal("recipe").executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, null))
								.then(Commands.argument("recipe", ResourceLocationArgument.id()).suggests(SUGGEST_RECIPES).executes(ctx -> getAdvancementsFrom(ctx, GameProfileArgument.getGameProfiles(ctx, "player"), true, ResourceLocationArgument.getAdvancement(ctx, "recipe"))))));
	}

	private static int getAdvancementsFrom(CommandContext<CommandSource> ctx, Collection<GameProfile> profiles, boolean recipe, Advancement advancement) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		FakePlayer fakePlayer = new FakePlayer(ctx.getSource().getServer().overworld(), profile);
		PlayerAdvancements playerAdvancements = ctx.getSource().getServer().getPlayerList().getPlayerAdvancements(fakePlayer);
		String advancementReference = recipe ? "recipe advancement" : "advancement";

		if (advancement == null) {
			Stream<Pair<Advancement, Integer>> allAdvancements = playerAdvancements.advancements.entrySet().stream().map(e -> Pair.of(e.getKey(), (int)(getAdvancementPercent(e.getValue()) * 100))).filter(p -> p.getRight() > 0);
			List<Pair<Advancement, Integer>> filteredAdvancements = allAdvancements.filter(p -> recipe ? p.getKey().getRewards().recipes.length > 0 : p.getKey().getRewards().recipes.length == 0).collect(Collectors.toList());
			IFormattableTextComponent advancementList = TextComponentUtils.formatList(filteredAdvancements, p -> p.getKey().getChatComponent().copy().append(new TranslationTextComponent(" (%s%%)", p.getRight()).withStyle(TextFormatting.GRAY)));

			if (filteredAdvancements.isEmpty()) {
				ctx.getSource().sendFailure(new TranslationTextComponent("No completed %1$ss of player %2$s were found", advancementReference, profile.getName()));
				return 0;
			}

			ctx.getSource().sendSuccess(new TranslationTextComponent("Sending all completed %1$ss of player %2$s (%3$s): %4$s", advancementReference, profile.getName(), filteredAdvancements.size(), advancementList), false);
			return filteredAdvancements.size();
		}

		AdvancementProgress advancementProgress = playerAdvancements.advancements.get(advancement);
		int progress = (int)(getAdvancementPercent(advancementProgress) * 100);

		if (progress == 0) {
			ctx.getSource().sendFailure(new TranslationTextComponent("No progress on %1$s %2$s of player %3$s found", advancementReference, advancement.getChatComponent(), profile.getName()));
			return 0;
		}

		Map<String, CriterionProgress> criteria = new HashMap<>();

		advancementProgress.getRemainingCriteria().forEach(s -> criteria.put(s, advancementProgress.getCriterion(s)));
		advancementProgress.getCompletedCriteria().forEach(s -> criteria.put(s, advancementProgress.getCriterion(s)));
		ctx.getSource().sendSuccess(new TranslationTextComponent("Sending %1$s %2$s of player %3$s: %4$s%% complete, criteria: %5$s", advancementReference, advancement.getChatComponent(), profile.getName(), progress, TextComponentUtils.formatList(criteria.keySet(), n -> new StringTextComponent(n).withStyle(s -> s.applyFormat(criteria.get(n).isDone() ? TextFormatting.GREEN : TextFormatting.DARK_RED).withHoverEvent(new HoverEvent(Action.SHOW_TEXT, new StringTextComponent("Obtained: " + (criteria.get(n).isDone() ? criteria.get(n).getObtained() : "Never"))))))), false);
		return progress;
	}

	private static int countAdvancements(CommandContext<CommandSource> ctx, Collection<GameProfile> profiles) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		FakePlayer fakePlayer = new FakePlayer(ctx.getSource().getServer().overworld(), profile);
		PlayerAdvancements playerAdvancements = ctx.getSource().getServer().getPlayerList().getPlayerAdvancements(fakePlayer);
		int recipeAdvancements = 0;
		int totalAdvancements = 0;

		for (Entry<Advancement, AdvancementProgress> advancement : playerAdvancements.advancements.entrySet()) {
			if (getAdvancementPercent(advancement.getValue()) > 0.0F) {
				if (advancement.getKey().getRewards().recipes.length > 0)
					recipeAdvancements++;

				totalAdvancements++;
			}
		}

		ctx.getSource().sendSuccess(new TranslationTextComponent("Player %1$s has %2$s completed advancements, %3$s of which are recipe advancements", profile.getName(), totalAdvancements, recipeAdvancements), false);
		return totalAdvancements;
	}

	private static GameProfile ensureOneTarget(Collection<GameProfile> profiles) throws CommandSyntaxException {
		if (profiles.size() > 1)
			throw new SimpleCommandExceptionType(new StringTextComponent("Targeting multiple players is not supported!")).create();

		return profiles.iterator().next();
	}

	private static PlayerAdvancements getPlayerAdvancements(CommandContext<CommandSource> ctx, Collection<GameProfile> profiles) throws CommandSyntaxException {
		GameProfile profile = ensureOneTarget(profiles);
		MinecraftServer server = ctx.getSource().getServer();
		FakePlayer fakePlayer = new FakePlayer(server.overworld(), profile);

		return server.getPlayerList().getPlayerAdvancements(fakePlayer);
	}

	//These two methods are copied from AdvancementProgress because they have a @OnlyIn(Dist.CLIENT) annotation there
	public static float getAdvancementPercent(AdvancementProgress progress) {
		if (progress.requirements.length == 0)
			return 0F;
		else {
			float total = (float)progress.requirements.length;
			float completed = (float)countCompletedRequirements(progress);

			return completed / total;
		}
	}

	public static int countCompletedRequirements(AdvancementProgress progress) {
		int count = 0;

		for(String[] requirement : progress.requirements) {
			for(String criterion : requirement) {
				CriterionProgress criterionProgress = progress.getCriterion(criterion);

				if (criterionProgress != null && criterionProgress.isDone()) {
					count++;
					break;
				}
			}
		}

		return count;
	}
}
