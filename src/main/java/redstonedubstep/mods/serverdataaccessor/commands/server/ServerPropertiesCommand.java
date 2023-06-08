package redstonedubstep.mods.serverdataaccessor.commands.server;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.tuple.Pair;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.dedicated.DedicatedServer;

public class ServerPropertiesCommand {
	private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROPERTIES = (ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(((DedicatedServer)ctx.getSource().getServer()).getProperties().properties.stringPropertyNames(), suggestionsBuilder);

	public static ArgumentBuilder<CommandSourceStack, ?> register() {
		return Commands.literal("properties")
				.then(Commands.literal("all").executes(ctx -> sendProperties(ctx, "")))
				.then(Commands.literal("get")
						.then(Commands.argument("name", StringArgumentType.word()).suggests(SUGGEST_PROPERTIES).executes(ctx -> sendProperties(ctx, StringArgumentType.getString(ctx, "name")))));
	}

	private static int sendProperties(CommandContext<CommandSourceStack> ctx, String name) {
		Properties serverProperties = ((DedicatedServer)ctx.getSource().getServer()).getProperties().properties;

		if (name.isEmpty()) {
			List<Pair<String, String>> sortedProperties = serverProperties.entrySet().stream().map(e -> Pair.of(e.getKey().toString(), e.getValue().toString())).sorted(Comparator.comparing(Pair::getLeft)).toList();
			Component propertiesComponent = ComponentUtils.formatList(sortedProperties, p -> Component.literal(p.getKey()).withStyle(ChatFormatting.AQUA).append("=").append(Component.literal(p.getValue()).withStyle(ChatFormatting.GREEN)));

			ctx.getSource().sendSuccess(() -> Component.translatable("The following %s properties were found: \n", serverProperties.entrySet().size()).append(propertiesComponent), false);
			return sortedProperties.size();
		}
		else if (!serverProperties.containsKey(name)) {
			ctx.getSource().sendFailure(Component.translatable("Could not find property with name \"%s\"", name));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("Property \"%1$s\" has the following value: %2$s", Component.literal(name).withStyle(ChatFormatting.AQUA), Component.literal(serverProperties.get(name).toString()).withStyle(ChatFormatting.GREEN)), false);
		return 1;
	}
}
