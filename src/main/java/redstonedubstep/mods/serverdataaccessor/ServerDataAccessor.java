package redstonedubstep.mods.serverdataaccessor;

import net.minecraft.commands.Commands.CommandSelection;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import redstonedubstep.mods.serverdataaccessor.commands.CommandRoot;

@Mod(ServerDataAccessor.MODID)
public class ServerDataAccessor {
	public static final String MODID = "serverdataaccessor";

	public ServerDataAccessor() {
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true));
		NeoForge.EVENT_BUS.addListener(this::registerCommands);
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SDMConfig.SERVER_SPEC);
	}

	public void registerCommands(RegisterCommandsEvent event){
		if (event.getCommandSelection() == CommandSelection.DEDICATED)
			CommandRoot.registerServerDataCommand(event.getDispatcher());

		CommandRoot.registerWorldDataCommand(event.getDispatcher());
	}
}
