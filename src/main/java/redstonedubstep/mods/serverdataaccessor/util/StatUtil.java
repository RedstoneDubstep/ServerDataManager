package redstonedubstep.mods.serverdataaccessor.util;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
public class StatUtil {
	public static StatsCounter mergeStats(Iterable<?> targets, StatType<?> statType, Optional<ResourceLocation> statId, MinecraftServer server) {
		StatsCounter statsCollection = new StatsCounter();

		for (Object statsHolder : targets) {
			ServerStatsCounter profileStats = getStatsFromSource(statsHolder, server);

			if (profileStats != null) {
				for (Object2IntMap.Entry<Stat<?>> statEntry : profileStats.stats.object2IntEntrySet()) {
					if (statType.equals(statEntry.getKey().getType()) && (statId.isEmpty() || statEntry.getKey().getValue().equals(statType.getRegistry().get(statId.get()))))
						statsCollection.increment(null, statEntry.getKey(), statEntry.getIntValue());
				}

			}
		}

		return statsCollection;
	}

	public static ServerStatsCounter getStatsFromSource(Object statSource, MinecraftServer server) {
		if (statSource instanceof File statsFile)
			return new ServerStatsCounter(server, statsFile);
		else if (statSource instanceof GameProfile profile)
			return StatUtil.getPlayerStats(profile.getId(), server);

		throw new IllegalArgumentException("Object " + statSource + " is not a valid stat source!"); //shouldn't happen
	}

	public static ServerStatsCounter getPlayerStats(UUID playerUUID, MinecraftServer server) {
		ServerStatsCounter serverstatscounter = server.getPlayerList().stats.get(playerUUID);

		if (serverstatscounter == null) {
			File statsFolder = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
			File statsFile = new File(statsFolder, playerUUID + ".json");

			if (statsFile.isFile())
				serverstatscounter = new ServerStatsCounter(server, statsFile);
		}

		return serverstatscounter;
	}

	public static Pair<Stat<?>, Integer> getStatFromCollection(StatsCounter statsCollection, StatType<?> statType, ResourceLocation statId) throws CommandSyntaxException {
		Pair<Stat<?>, Integer> stat = null;

		if (statType != null && statId != null) {
			Optional<Entry<Stat<?>>> optionalStat = statsCollection.stats.object2IntEntrySet().stream().filter(e -> e.getKey().getType().equals(statType) && e.getKey().getValue().equals(statType.getRegistry().get(statId))).findFirst();

			if (optionalStat.isPresent())
				stat = Pair.of(optionalStat.get().getKey(), optionalStat.get().getIntValue());
			else
				throw new SimpleCommandExceptionType(Component.translatable("Couldn't find statistic with id \"%1$s\" of type \"%2$s\"", statId.toString(), Component.translatable(StatUtil.getStatTypeTranslation(statType)))).create();
		}

		return stat;
	}

	public static String getStatTranslationKey(Stat<?> stat) {
		if (stat.getValue() instanceof Block block)
			return block.getDescriptionId();
		else if (stat.getValue() instanceof Item item)
			return item.getDescriptionId();
		else if (stat.getValue() instanceof EntityType<?> type)
			return type.getDescriptionId();
		else if (stat.getValue() instanceof ResourceLocation location)
			return getStatTranslationKey(location);

		return "";
	}

	public static String getStatTranslationKey(ResourceLocation customStatLocation) {
		return customStatLocation == null ? "unknown" : "stat." + customStatLocation.toString().replace(':', '.');
	}

	public static String getStatTypeTranslation(StatType<?> statType) {
		Component displayName = statType.getDisplayName();
		String key = displayName.getContents() instanceof TranslatableContents content ? content.getKey() : displayName.getString();

		if (key.contains("killed") || key.contains("custom")) {
			String name = key.substring(key.lastIndexOf(".") + 1).replace(".", "").replace("_", " ");

			return name.substring(0, 1).toUpperCase() + name.substring(1);
		}

		return key;
	}

	public static StatType<?> getStatType(CommandContext<CommandSourceStack> ctx, String argument) throws CommandSyntaxException {
		ResourceKey<?> resourceKey = ctx.getArgument(argument, ResourceKey.class);
		Optional<ResourceKey<StatType<?>>> optional = resourceKey.cast(Registries.STAT_TYPE);
		ResourceKey<StatType<?>> statKey = optional.orElseThrow(() -> new SimpleCommandExceptionType(new LiteralMessage("Unknown statistic")).create());

		return ctx.getSource().getServer().registryAccess().registryOrThrow(Registries.STAT_TYPE).getOptional(statKey).orElseThrow(() -> new SimpleCommandExceptionType(new LiteralMessage("Unknown statistic")).create());
	}
}
