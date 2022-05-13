package redstonedubstep.mods.serverdataaccessor;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public class SDMConfig {
	public static final ForgeConfigSpec SERVER_SPEC;
	public static final Config CONFIG;

	static {
		final Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);

		SERVER_SPEC = specPair.getRight();
		CONFIG = specPair.getLeft();
	}

	public static class Config {
		public IntValue serverdataCommandPermissionLevel;
		public IntValue worlddataCommandPermissionLevel;

		Config(ForgeConfigSpec.Builder builder) {
			serverdataCommandPermissionLevel = builder
					.comment("What op permission level should be the requirement for being able to execute /serverdata?")
					.defineInRange("serverdataCommandPermissionLevel", 2, 0, 4);
			worlddataCommandPermissionLevel = builder
					.comment("What op permission level should be the requirement for being able to execute /worlddata")
					.defineInRange("worlddataCommandPermissionLevel", 2, 0, 4);
		}
	}
}
