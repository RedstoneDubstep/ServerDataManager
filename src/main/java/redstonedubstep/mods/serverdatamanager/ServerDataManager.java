package redstonedubstep.mods.serverdatamanager;

import net.minecraft.commands.Commands.CommandSelection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;
import redstonedubstep.mods.serverdatamanager.commands.CommandRoot;

@Mod("serverdatamanager")
public class ServerDataManager {
	public ServerDataManager() {
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
		MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
	}

	public void registerCommands(RegisterCommandsEvent event){
		if (event.getEnvironment() == CommandSelection.DEDICATED)
			CommandRoot.registerServerDataCommand(event.getDispatcher());

		CommandRoot.registerWorldDataCommand(event.getDispatcher());
	}
}
