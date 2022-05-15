package redstonedubstep.mods.serverdataaccessor.commands.server;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

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
			List<Pair<String, String>> sortedProperties = serverProperties.entrySet().stream().map(e -> Pair.of(e.getKey().toString(), e.getValue().toString())).sorted(Comparator.comparing(Pair::getLeft)).collect(Collectors.toList());
			ITextComponent propertiesComponent = TextComponentUtils.formatList(sortedProperties, p -> new StringTextComponent(p.getKey()).withStyle(TextFormatting.AQUA).append("=").append(new StringTextComponent(p.getValue()).withStyle(TextFormatting.GREEN)));

			ctx.getSource().sendSuccess(new TranslationTextComponent("The following %s properties were found: \n", serverProperties.entrySet().size()).append(propertiesComponent), false);
			return sortedProperties.size();
		}
		else if (!serverProperties.containsKey(name)) {
			ctx.getSource().sendFailure(new TranslationTextComponent("Could not find property with name \"%s\"", name));
			return 0;
		}

		ctx.getSource().sendSuccess(new TranslationTextComponent("Property \"%1$s\" has the following value: %2$s", new StringTextComponent(name).withStyle(TextFormatting.AQUA), new StringTextComponent(serverProperties.get(name).toString()).withStyle(TextFormatting.GREEN)), false);
		return 1;
	}
}
