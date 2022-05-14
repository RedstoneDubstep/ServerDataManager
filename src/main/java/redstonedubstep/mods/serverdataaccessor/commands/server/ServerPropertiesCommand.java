package redstonedubstep.mods.serverdataaccessor.commands.server;

import java.util.Properties;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

public class ServerPropertiesCommand {
	private static final SuggestionProvider<CommandSource> SUGGEST_PROPERTIES = (ctx, suggestionsBuilder) -> ISuggestionProvider.suggest(((DedicatedServer)ctx.getSource().getServer()).getProperties().properties.stringPropertyNames(), suggestionsBuilder);

	public static ArgumentBuilder<CommandSource, ?> register() {
		return Commands.literal("properties")
				.then(Commands.literal("all").executes(ctx -> sendProperties(ctx, "")))
				.then(Commands.literal("get")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_PROPERTIES).executes(ctx -> sendProperties(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static int sendProperties(CommandContext<CommandSource> ctx, String name) {
		Properties serverProperties = ((DedicatedServer)ctx.getSource().getServer()).getProperties().properties;

		if (name.isEmpty()) {
			ITextComponent propertiesText = TextComponentUtils.formatList(serverProperties.entrySet(), e -> new StringTextComponent(e.getKey().toString()).withStyle(TextFormatting.AQUA).append("=").append(new StringTextComponent(e.getValue().toString()).withStyle(TextFormatting.GREEN)));

			ctx.getSource().sendSuccess(new TranslationTextComponent("The following %s properties were found: \n", serverProperties.entrySet().size()).append(propertiesText), false);
			return serverProperties.entrySet().size();
		}
		else {
			if (serverProperties.containsKey(name)) {
				ctx.getSource().sendSuccess(new TranslationTextComponent("Property \"%1$s\" has the following value: %2$s", new StringTextComponent(name).withStyle(TextFormatting.AQUA), new StringTextComponent(serverProperties.get(name).toString()).withStyle(TextFormatting.GREEN)), false);
				return 1;
			}
			else
				ctx.getSource().sendFailure(new TranslationTextComponent("Could not find property with name \"%s\"", name));
		}

		return 0;
	}
}
