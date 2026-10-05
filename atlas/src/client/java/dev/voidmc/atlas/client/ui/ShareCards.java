package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.core.waypoints.SharedWaypoint;
import dev.voidmc.atlas.core.waypoints.WaypointShare;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * A friend's shared waypoint (the design's 355:14021): a card at the bottom left, over the chat, with Accept and Ignore.
 * It folds to a line after two minutes, and nothing is added without Accept. A share arrives as an {@code atlas-wp:}
 * code in a message (VOID friends will hand it over directly later, through {@link #offer}); Accept and Ignore are
 * clicked with the chat open (T).
 */
public final class ShareCards {
	/** A card folds after this long. */
	public static final long FOLD_MILLIS = 120_000;

	private record Card(SharedWaypoint waypoint, String from, String sourceLabel, long at) {
	}

	private static final ArrayDeque<Card> CARDS = new ArrayDeque<>();
	private static float[] accept;
	private static float[] ignore;

	private ShareCards() {
	}

	/** A shared waypoint for the player to accept or ignore (client thread). */
	public static void offer(SharedWaypoint waypoint, String from, String sourceLabel) {
		if (CARDS.size() >= 4) {
			CARDS.pollFirst();
		}
		CARDS.addLast(new Card(waypoint, from == null || from.isBlank() ? "Someone" : from, sourceLabel, System.currentTimeMillis()));
	}

	/** A message on its way to the chat: any share codes in it become cards. */
	public static void scan(Component message) {
		String text = message.getString();
		if (text.indexOf(WaypointShare.PREFIX) < 0) {
			return;
		}
		String from = sender(text);
		for (String code : WaypointShare.findCodes(text)) {
			try {
				List<SharedWaypoint> list = WaypointShare.decode(code);
				for (SharedWaypoint w : list) {
					offer(w, from, "from chat");
				}
			} catch (IllegalArgumentException e) {
				AtlasClient.LOGGER.info("Atlas: a waypoint share could not be read: {}", e.getMessage());
			}
		}
	}

	/** Who a chat line is from: "<Mara> ..." or "Mara: ...", or null. */
	static String sender(String text) {
		String t = text.strip();
		if (t.startsWith("<") && t.indexOf('>') > 1) {
			return t.substring(1, t.indexOf('>'));
		}
		int colon = t.indexOf(':');
		if (colon > 0 && colon < 24 && !t.substring(0, colon).contains(" ")) {
			return t.substring(0, colon);
		}
		return null;
	}

	/** Registers the card on the chat screen (drawn over it, clicked there). */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(graphics, true));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y()));
		});
	}

	/** In the HUD (no chat open): the card, or its folded line. */
	public static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		draw(graphics, false);
	}

	private static void draw(GuiGraphicsExtractor graphics, boolean chatOpen) {
		accept = null;
		ignore = null;
		Card card = CARDS.peekLast();
		if (card == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Ui ui = new Ui(graphics, MinimapHud.hudUnit());
		ui.begin();
		long age = System.currentTimeMillis() - card.at;
		float x = 36;
		SharedWaypoint w = card.waypoint;
		if (age > FOLD_MILLIS && !chatOpen) {
			// folded: one quiet line
			String line = card.from + " shared " + w.name() + "  ·  T to open";
			float lw = ui.width(line, Ui.Font.F11) + 28;
			float y = ui.height() - 120;
			ui.panel(x, y, lw, 26, 13, Theme.ISLAND, Theme.HAIR_8);
			ui.text(line, x + 14, y + 6, Ui.Font.F11, Theme.INK_3);
			ui.end();
			return;
		}
		float cw = 368;
		float ch = 118;
		float y = ui.height() - (chatOpen ? 300 : 120) - ch;
		ui.panel(x, y, cw, ch, 16, Theme.ISLAND, Theme.HAIR_10);
		PinShapes.pin(ui, x + 30, y + 42, 18, w.colour(), AtlasClient.get().settings().shapeMarkers, Waypoint.COLOURS[Math.floorMod(w.colour(), 8)], 1);
		ui.text((card.from + " shared a waypoint").toUpperCase(Locale.ROOT), x + 60, y + 14, Ui.Font.F85, 1, Theme.INK_4);
		ui.text(ui.fit(w.name(), Ui.Font.F13, cw - 80), x + 60, y + 28, Ui.Font.F13, Theme.INK);
		LocalPlayer p = mc.player;
		String where = "X " + w.x() + "  Y " + (w.y() == null ? "~" : w.y()) + "  Z " + w.z();
		if (p != null) {
			double d = Math.hypot(w.x() + 0.5 - p.getX(), w.z() + 0.5 - p.getZ());
			where += "  ·  " + MarkerPainter.distance(d) + " " + MarkerPainter.direction(p.getX(), p.getZ(), w.x() + 0.5, w.z() + 0.5);
		}
		ui.text(where, x + 60, y + 48, Ui.Font.F11, Theme.INK_3);
		float by = y + ch - 44;
		String chip = card.sourceLabel;
		float chipW = ui.width(chip, Ui.Font.F11) + 34;
		ui.panel(x + 14, by + 4, chipW, 26, 13, Theme.WELL, Theme.HAIR_8);
		ui.text(chip, x + 14 + 24, by + 9, Ui.Font.F11, Theme.INK_3);
		float ax = x + cw - 14 - 72 - 8 - 72;
		ui.panel(ax, by, 72, 34, 17, Theme.RAISED, Theme.HAIR_10);
		ui.textCentred("Accept", ax + 36, by + 9, Ui.Font.F12, 0, Theme.INK);
		float ix = ax + 72 + 8;
		ui.panel(ix, by, 72, 34, 17, Theme.WELL, Theme.HAIR_7);
		ui.textCentred("Ignore", ix + 36, by + 9, Ui.Font.F12, 0, Theme.INK_2);
		if (chatOpen) {
			// in GUI pixels, for the clicks
			float k = 1 / ui.design(1);
			accept = new float[]{ax * k, by * k, (ax + 72) * k, (by + 34) * k};
			ignore = new float[]{ix * k, by * k, (ix + 72) * k, (by + 34) * k};
		}
		ui.end();
	}

	/** A click with the chat open; true if it was on the card's buttons. */
	private static boolean click(double x, double y) {
		Card card = CARDS.peekLast();
		if (card == null) {
			return false;
		}
		if (inside(accept, x, y)) {
			var session = AtlasClient.get().session();
			LocalPlayer p = Minecraft.getInstance().player;
			if (session != null) {
				Waypoint w = card.waypoint.toWaypoint(p == null ? 64 : p.getBlockY()).withShared(false);
				session.waypoints.put(w);
				session.saveSoon(false);
			}
			CARDS.remove(card);
			return true;
		}
		if (inside(ignore, x, y)) {
			CARDS.remove(card);
			return true;
		}
		return false;
	}

	private static boolean inside(float[] r, double x, double y) {
		return r != null && x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
	}
}
