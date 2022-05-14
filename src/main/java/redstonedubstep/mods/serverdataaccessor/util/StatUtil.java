package redstonedubstep.mods.serverdataaccessor.util;

import java.io.File;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.block.Block;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.IStatFormatter;
import net.minecraft.stats.ServerStatisticsManager;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.StatisticsManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.registries.ForgeRegistries;

public class StatUtil {
	public static StatisticsManager mergeStats(Iterable<?> targets, StatType<?> statType, Optional<ResourceLocation> statId, MinecraftServer server) {
		StatisticsManager statsCollection = new StatisticsManager();

		for (Object statsHolder : targets) {
			ServerStatisticsManager profileStats = getStatsFromSource(statsHolder, server);

			if (profileStats != null) {
				for (Object2IntMap.Entry<Stat<?>> statEntry : profileStats.stats.object2IntEntrySet()) {
					if (statType.equals(statEntry.getKey().getType()) && (!statId.isPresent() || statEntry.getKey().getValue().equals(statType.getRegistry().get(statId.get()))))
						statsCollection.increment(null, statEntry.getKey(), statEntry.getIntValue());
				}

			}
		}

		return statsCollection;
	}

	public static ServerStatisticsManager getStatsFromSource(Object statSource, MinecraftServer server) {
		if (statSource instanceof File)
			return new ServerStatisticsManager(server, ((File)statSource));
		else if (statSource instanceof GameProfile)
			return StatUtil.getPlayerStats(((GameProfile)statSource).getId(), server);

		throw new IllegalArgumentException("Object " + statSource + " is not a valid stat source!"); //shouldn't happen
	}

	public static ServerStatisticsManager getPlayerStats(UUID playerUUID, MinecraftServer server) {
		ServerStatisticsManager serverstatsmanager = server.getPlayerList().stats.get(playerUUID);

		if (serverstatsmanager == null) {
			File statsFolder = server.getWorldPath(FolderName.PLAYER_STATS_DIR).toFile();
			File statsFile = new File(statsFolder, playerUUID + ".json");

			if (statsFile.isFile())
				serverstatsmanager = new ServerStatisticsManager(server, statsFile);
		}

		return serverstatsmanager;
	}

	public static Pair<Stat<?>, Integer> getStatFromCollection(StatisticsManager statsCollection, StatType<?> statType, ResourceLocation statId) throws CommandSyntaxException {
		Pair<Stat<?>, Integer> stat = null;

		if (statType != null && statId != null) {
			Optional<Entry<Stat<?>>> optionalStat = statsCollection.stats.object2IntEntrySet().stream().filter(e -> e.getKey().getType().equals(statType) && e.getKey().getValue().equals(statType.getRegistry().get(statId))).findFirst();

			if (optionalStat.isPresent())
				stat = Pair.of(optionalStat.get().getKey(), optionalStat.get().getIntValue());
			else
				throw new SimpleCommandExceptionType(new TranslationTextComponent("Couldn't find statistic with id \"%1$s\" of type \"%2$s\"", statId, new TranslationTextComponent(StatUtil.getStatTypeTranslation(statType)))).create();
		}

		return stat;
	}

	public static String getStatTranslationKey(Stat<?> stat) {
		if (stat.getValue() instanceof Block)
			return ((Block)stat.getValue()).getDescriptionId();
		else if (stat.getValue() instanceof Item)
			return ((Item)stat.getValue()).getDescriptionId();
		else if (stat.getValue() instanceof EntityType<?>)
			return ((EntityType<?>)stat.getValue()).getDescriptionId();
		else if (stat.getValue() instanceof ResourceLocation)
			return getStatTranslationKey(((ResourceLocation)stat.getValue()));

		return "";
	}

	public static String getStatTranslationKey(ResourceLocation customStatLocation) {
		return customStatLocation == null ? "unknown" : "stat." + customStatLocation.toString().replace(':', '.');
	}

	public static String getStatTypeTranslation(StatType<?> statType) {
		String key = "stat_type." + ForgeRegistries.STAT_TYPES.getKey(statType).toString().replace(':', '.');

		if (key.contains("killed") || key.contains("custom")) {
			String name = key.substring(key.lastIndexOf(".") + 1).replace(".", "").replace("_", " ");

			return name.substring(0, 1).toUpperCase() + name.substring(1);
		}

		return key;
	}

	public static StatType<?> getStatType(CommandContext<CommandSource> ctx, String argument) throws CommandSyntaxException {
		ResourceLocation resourceKey = ctx.getArgument(argument, ResourceLocation.class);
		Optional<StatType<?>> optional = Optional.ofNullable(ForgeRegistries.STAT_TYPES.getValue(resourceKey));

		return optional.orElseThrow(() -> new DynamicCommandExceptionType(s -> new TranslationTextComponent("Unknown stat type %s", s)).create(resourceKey));
	}

	//copied from IStatFormatter because IStatFormatter#format is annotated with @OnlyIn(Dist.CLIENT)
	public static String format(Stat<?> stat, int value) {
		Function<Integer, String> formatter = NumberFormat.getIntegerInstance(Locale.US)::format;

		if (stat.formatter == IStatFormatter.DIVIDE_BY_TEN)
			formatter = decimal -> IStatFormatter.DECIMAL_FORMAT.format((int)decimal * 0.1D);
		else if (stat.formatter == IStatFormatter.DISTANCE) {
			formatter = centimeters -> {
				double meters = (int)centimeters / 100.0D;
				double kilometers = meters / 1000.0D;
				if (kilometers > 0.5D)
					return IStatFormatter.DECIMAL_FORMAT.format(kilometers) + " km";
				else
					return meters > 0.5D ? IStatFormatter.DECIMAL_FORMAT.format(meters) + " m" : centimeters + " cm";
			};
		}
		else if (stat.formatter == IStatFormatter.TIME) {
			formatter = ticks -> {
				double seconds = (int)ticks / 20.0D, minutes = seconds / 60.0D, hours = minutes / 60.0D, days = hours / 24.0D, years = days / 365.0D;

				if (years > 0.5D)
					return IStatFormatter.DECIMAL_FORMAT.format(years) + " y";
				else if (days > 0.5D)
					return IStatFormatter.DECIMAL_FORMAT.format(days) + " d";
				else if (hours > 0.5D)
					return IStatFormatter.DECIMAL_FORMAT.format(hours) + " h";
				else
					return minutes > 0.5D ? IStatFormatter.DECIMAL_FORMAT.format(minutes) + " m" : seconds + " s";
			};
		}

		return formatter.apply(value);
	}
}
