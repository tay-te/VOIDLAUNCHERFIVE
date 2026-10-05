package dev.voidmc.atlas.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.waypoints.CoordParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Coordinates in chat ("100 64 -200", "X: 100 Y: 64 Z: -200", the F3 line, Xaero's shared waypoints) become clickable:
 * a click runs Atlas's own client command, which makes a temporary waypoint there. A vanilla click event, handled on
 * the client, so it works on any server and nothing is sent to it.
 */
public final class ChatCoordinates {
	/** The client command a click runs: {@code /atlas waypoint <x> <y> <z> <dimension> <name>}. */
	public static final String COMMAND = "atlas";

	private ChatCoordinates() {
	}

	/** The message with its coordinates made clickable (the same message if it has none, or the setting is off). */
	public static Component decorate(Component message) {
		AtlasClient atlas = AtlasClient.get();
		if (atlas == null || !atlas.settings().chatCoordinates || message == null) {
			return message;
		}
		String text = message.getString();
		if (text.indexOf("/" + COMMAND + " waypoint") >= 0) {
			return message;
		}
		List<CoordParser.Match> matches = CoordParser.find(text);
		if (matches.isEmpty()) {
			return message;
		}
		Minecraft mc = Minecraft.getInstance();
		String here = mc.level == null ? "minecraft:overworld" : mc.level.dimension().identifier().toString();
		int playerY = mc.player == null ? 64 : mc.player.getBlockY();
		// the message's styled pieces, cut where the coordinates begin and end
		List<Style> styles = new ArrayList<>();
		List<String> pieces = new ArrayList<>();
		message.visit((style, piece) -> {
			styles.add(style);
			pieces.add(piece);
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent out = Component.empty();
		int pos = 0;
		int m = 0;
		for (int i = 0; i < pieces.size(); i++) {
			String piece = pieces.get(i);
			Style style = styles.get(i);
			int start = pos;
			int end = pos + piece.length();
			int cursor = start;
			while (cursor < end) {
				while (m < matches.size() && matches.get(m).end() <= cursor) {
					m++;
				}
				CoordParser.Match match = m < matches.size() ? matches.get(m) : null;
				if (match == null || match.start() >= end) {
					out.append(Component.literal(piece.substring(cursor - start)).setStyle(style));
					cursor = end;
				} else if (match.start() > cursor) {
					out.append(Component.literal(piece.substring(cursor - start, match.start() - start)).setStyle(style));
					cursor = match.start();
				} else {
					int to = Math.min(end, match.end());
					out.append(Component.literal(piece.substring(cursor - start, to - start)).setStyle(clickable(style, match, here, playerY)));
					cursor = to;
				}
			}
			pos = end;
		}
		return out;
	}

	private static Style clickable(Style style, CoordParser.Match match, String here, int playerY) {
		String dim = match.dimension() != null ? match.dimension() : here;
		int y = match.y() != null ? match.y() : playerY;
		String name = match.name() != null && !match.name().isBlank() ? match.name() : "From chat";
		String command = String.format(Locale.ROOT, "/%s waypoint %d %d %d %s %s", COMMAND, match.x(), y, match.z(), dim, name);
		return style.withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Atlas: mark " + match.x() + " " + y + " " + match.z() + " (a temporary waypoint)")));
	}

	/** Registers {@code /atlas waypoint <x> <y> <z> [dimension] [name]} (client side only). */
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommands.literal(COMMAND)
			.then(ClientCommands.literal("waypoint")
				.then(ClientCommands.argument("x", IntegerArgumentType.integer(-CoordParser.XZ_LIMIT, CoordParser.XZ_LIMIT))
					.then(ClientCommands.argument("y", IntegerArgumentType.integer(-CoordParser.Y_LIMIT, CoordParser.Y_LIMIT))
						.then(ClientCommands.argument("z", IntegerArgumentType.integer(-CoordParser.XZ_LIMIT, CoordParser.XZ_LIMIT))
							.executes(c -> add(c, null, null))
							.then(ClientCommands.argument("dimension", StringArgumentType.word())
								.executes(c -> add(c, StringArgumentType.getString(c, "dimension"), null))
								.then(ClientCommands.argument("name", StringArgumentType.greedyString())
									.executes(c -> add(c, StringArgumentType.getString(c, "dimension"), StringArgumentType.getString(c, "name")))))))))));
	}

	private static int add(CommandContext<FabricClientCommandSource> c, String dimension, String name) {
		AtlasClient atlas = AtlasClient.get();
		MapSession session = atlas.session();
		if (session == null) {
			c.getSource().sendError(Component.literal("Atlas: no map is open"));
			return 0;
		}
		int x = IntegerArgumentType.getInteger(c, "x");
		int y = IntegerArgumentType.getInteger(c, "y");
		int z = IntegerArgumentType.getInteger(c, "z");
		Minecraft mc = Minecraft.getInstance();
		String dim = dimension != null ? (dimension.contains(":") ? dimension : "minecraft:" + dimension)
			: mc.level == null ? "minecraft:overworld" : mc.level.dimension().identifier().toString();
		int minutes = atlas.settings().temporaryMinutes;
		Waypoint w = Waypoint.temporary(name == null ? "From chat" : name, x, y, z, dim, 2, minutes * 60_000L);
		session.waypoints.put(w);
		session.saveSoon(false);
		c.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT, "Atlas: marked %s at %d %d %d (for %d min; keep it from the map's list)",
			w.name(), x, y, z, minutes)));
		return 1;
	}
}
