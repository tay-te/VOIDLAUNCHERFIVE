package dev.voidmc.hearth.client.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.client.overlay.HearthStateSections;
import dev.voidmc.hearth.client.overlay.HearthViews;
import dev.voidmc.hearth.client.overlay.LivingWorldJson;
import dev.voidmc.hearth.economy.coin.CoinFormat;
import dev.voidmc.hearth.social.events.EventPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The contest line under the quest HUD (docs/LIVING_WORLD_2.md §10 F6; Figma "Contest line under the quest HUD"): the
 * archery contest's arrows as they land and its table, or the melee's rounds, while the player is at the range or the
 * ring; the result held six seconds, then the line folds away. Drawn natively like the quest HUD, in the Hub's look (a
 * flat dark card, a hairline, spaced eyebrows; static text and cells, no animation); the same facts are the hub state's
 * {@code contest} section. Its rect joins the quest panel's in {@link QuestHudHook#rect} so a map keeps clear of both.
 * Everything comes from the server ({@link EventPayloads.ContestLine}). 2026-10-21.
 */
public final class ContestHud implements HudElement {
	/** A result stays this long after the contest is settled. */
	static final long HOLD_MS = 6000;
	static final int WIDTH = 168;
	static final int PAD = 6;
	static final int CARD = 0xE6191A1C;
	static final int HAIRLINE = 0x1AFFFFFF;
	static final int TEXT = 0xFFECECEC;
	static final int MUTED = 0xFF8C8E93;
	static final int GOLD = 0xFFD9B36C;
	static final int ROW = 0x14FFFFFF;
	static final int CELL_EMPTY = 0xFF2A2B2E;
	static final int CELL_HIT = 0xFF5E8F5A;
	static final int CELL_GOOD = 0xFFC9A55C;
	static final int CELL_MISS = 0xFF7A3A33;

	private static volatile @Nullable JsonObject line;
	private static volatile long resultAt = -1;
	private static volatile long revision;

	ContestHud() {
	}

	/** Called once from the quest module's client init. */
	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(EventPayloads.ContestLine.TYPE, (payload, ctx) -> receive(payload.json(), Util.getMillis()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			line = null;
			resultAt = -1;
			revision++;
		});
		HudElementRegistry.attachElementAfter(Hearth.id("quest/tracker"), Hearth.id("quest/contest"), new ContestHud());
		HearthStateSections.register("contest", new HearthStateSections.Section() {
			@Override
			public JsonElement build(Minecraft mc) {
				return LivingWorldJson.contest(current(Util.getMillis()));
			}

			@Override
			public long fingerprint(Minecraft mc) {
				return revision * 2 + (current(Util.getMillis()) == null ? 0 : 1);
			}
		});
	}

	/** A line from the server: a result is held {@link #HOLD_MS} even when "nothing" follows it at once. */
	static void receive(String json, long now) {
		JsonObject o = null;
		if (!json.isEmpty()) {
			try {
				JsonElement e = JsonParser.parseString(json);
				o = e.isJsonObject() ? e.getAsJsonObject() : null;
			} catch (RuntimeException ex) {
				o = null;
			}
		}
		if (o == null && resultAt >= 0 && now - resultAt < HOLD_MS) {
			return;
		}
		line = o;
		resultAt = o != null && !"open".equals(str(o, "state")) ? now : -1;
		revision++;
		HearthViews.bump();
	}

	/** The line to show now, or null (none, or a result held long enough). */
	static @Nullable JsonObject current(long now) {
		JsonObject l = line;
		if (l != null && resultAt >= 0 && now - resultAt >= HOLD_MS) {
			line = null;
			resultAt = -1;
			revision++;
			return null;
		}
		return l;
	}

	static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	static int num(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
	}

	// ---------------------------------------------------------------- layout

	/** Where the line goes: under the quest panel, or in its place at the top left. */
	static int top(Minecraft mc) {
		QuestHud.Layout q = QuestHud.layout(mc);
		return q == null ? PAD : q.rect().y1() + 4;
	}

	/** The line's height for what it shows now (0: nothing). */
	static int height(Font font, @Nullable JsonObject l) {
		if (l == null) {
			return 0;
		}
		int lh = font.lineHeight + 2;
		boolean result = !"open".equals(str(l, "state"));
		if (result) {
			return 6 + 7 + lh + lh + 4;
		}
		if ("archery".equals(str(l, "kind"))) {
			int rows = l.get("table") instanceof JsonArray t ? Math.min(3, t.size()) : 0;
			return 6 + 7 + lh + 14 + 4 + rows * lh + lh + 4;
		}
		return 6 + 7 + lh + 10 + 4 + lh + 4;
	}

	/** The line's rect, or null when nothing shows (the map's room, through {@link QuestHudHook#rect}). */
	static QuestHudHook.@Nullable Rect rect(Minecraft mc) {
		if (mc.player == null || mc.gui.hud.isHidden()) {
			return null;
		}
		JsonObject l = current(Util.getMillis());
		if (l == null) {
			return null;
		}
		int y = top(mc);
		return new QuestHudHook.Rect(PAD, y, PAD + WIDTH + 6, y + height(mc.font, l));
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		QuestHudHook.Rect r = rect(mc);
		JsonObject l = line;
		if (r == null || l == null) {
			return;
		}
		Font font = mc.font;
		card(g, r.x0(), r.y0(), r.x1(), r.y1());
		int x = r.x0() + 6;
		int right = r.x1() - 6;
		int y = r.y0() + 6;
		String kind = str(l, "kind");
		String state = str(l, "state");
		boolean result = !"open".equals(state);
		boolean archery = "archery".equals(kind);
		String kindName = Component.translatable("hearth.contest." + kind).getString();
		String eyebrow = result ? kindName + " · " + Component.translatable("hearth.contest." + state).getString()
			: kindName + " · " + str(l, "eventName") + (str(l, "town").isEmpty() ? "" : " · " + str(l, "town"));
		eyebrow(g, font, eyebrow, x, y, result && "won".equals(state) ? GOLD : MUTED);
		if (!result && !archery) {
			String round = Component.translatable("hearth.contest.round_of", num(l, "round"), num(l, "rounds")).getString();
			eyebrowRight(g, font, round, right, y, GOLD);
		}
		y += 7 + 2;
		int lh = font.lineHeight + 2;
		if (result) {
			String title = Component.translatable("hearth.contest." + kind + "." + state).getString();
			g.text(font, font.plainSubstrByWidth(title, right - x), x, y, TEXT, false);
			y += lh;
			if ("won".equals(state)) {
				String purse = Component.translatable("hearth.contest.purse_of", CoinFormat.format(num(l, "purse"))).getString();
				g.text(font, font.plainSubstrByWidth(purse, right - x), x, y, MUTED, false);
			} else if (archery) {
				g.text(font, Component.translatable("hearth.contest.score", num(l, "score")).getString(), x, y, MUTED, false);
			} else {
				g.text(font, Component.translatable("hearth.contest.melee.record", num(l, "won"), num(l, "lost")).getString(), x, y, MUTED, false);
			}
			return;
		}
		if (archery) {
			g.text(font, Component.translatable("hearth.contest.score", num(l, "score")).getString(), x, y, TEXT, false);
			String shots = Component.translatable("hearth.contest.shots", num(l, "shots"), num(l, "arrows")).getString();
			g.text(font, shots, right - font.width(shots), y, MUTED, false);
			y += lh;
			JsonArray hits = l.get("hits") instanceof JsonArray a ? a : new JsonArray();
			int arrows = Math.max(1, num(l, "arrows"));
			int cell = 14;
			int gap = Math.max(1, (right - x - arrows * cell) / Math.max(1, arrows - 1));
			gap = Math.min(gap, 3);
			for (int i = 0; i < arrows; i++) {
				int cx = x + i * (cell + gap);
				int colour = CELL_EMPTY;
				String label = "";
				if (i < hits.size()) {
					int s = hits.get(i).getAsInt();
					colour = s <= 0 ? CELL_MISS : s >= 9 ? CELL_GOOD : CELL_HIT;
					label = s <= 0 ? "×" : Integer.toString(s);
				}
				rounded(g, cx, y, cx + cell, y + cell, colour);
				if (!label.isEmpty()) {
					g.pose().pushMatrix();
					g.pose().translate(cx + cell / 2F, y + 4);
					g.pose().scale(0.75F, 0.75F);
					g.centeredText(font, label, 0, 0, s(i, hits) >= 9 ? 0xFF2A1F0C : TEXT);
					g.pose().popMatrix();
				}
			}
			y += cell + 4;
			JsonArray table = l.get("table") instanceof JsonArray t ? t : new JsonArray();
			for (int i = 0; i < table.size() && i < 3; i++) {
				JsonObject row = table.get(i).getAsJsonObject();
				boolean you = row.get("you") != null && row.get("you").getAsBoolean();
				if (you) {
					g.fill(x - 3, y - 1, right + 3, y + lh - 2, ROW);
				}
				g.text(font, Integer.toString(num(row, "place")), x, y, MUTED, false);
				String name = you ? Component.translatable("hearth.contest.you").getString() : str(row, "name");
				g.text(font, font.plainSubstrByWidth(name, right - x - 34), x + 10, y, you ? TEXT : 0xFFB8BABE, false);
				String sc = Integer.toString(num(row, "score"));
				g.text(font, sc, right - font.width(sc), y, you ? TEXT : 0xFFB8BABE, false);
				y += lh;
			}
			String purse = Component.translatable("hearth.contest.purse", CoinFormat.format(num(l, "purse"))).getString();
			small(g, font, font.plainSubstrByWidth(purse, (int) ((right - x) / 0.85F)), x, y, MUTED);
			return;
		}
		// the melee
		String title = Component.translatable(l.get("fighting") != null && l.get("fighting").getAsBoolean() ? "hearth.contest.melee.title"
			: "hearth.contest.melee.waiting").getString();
		g.text(font, font.plainSubstrByWidth(title, right - x), x, y, TEXT, false);
		y += lh;
		int won = num(l, "won");
		int lost = num(l, "lost");
		int rounds = Math.max(1, num(l, "rounds"));
		for (int i = 0; i < rounds; i++) {
			int px = x + i * 30;
			rounded(g, px, y, px + 26, y + 8, i < won ? CELL_HIT : i < won + lost ? 0xFF55575C : CELL_EMPTY);
		}
		String rec = Component.translatable("hearth.contest.melee.record", won, lost).getString();
		small(g, font, rec, x + rounds * 30 + 4, y, 0xFFB8BABE);
		y += 10 + 4;
		small(g, font, font.plainSubstrByWidth(Component.translatable("hearth.contest.melee.yield").getString(), (int) ((right - x) / 0.85F)), x, y,
			MUTED);
	}

	private static int s(int i, JsonArray hits) {
		return i < hits.size() ? hits.get(i).getAsInt() : 0;
	}

	/** A flat card with rounded corners and a hairline: the Hub's panel at HUD size. */
	static void card(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		rounded(g, x0, y0, x1, y1, CARD);
		g.fill(x0 + 2, y0, x1 - 2, y0 + 1, HAIRLINE);
		g.fill(x0 + 2, y1 - 1, x1 - 2, y1, HAIRLINE);
		g.fill(x0, y0 + 2, x0 + 1, y1 - 2, HAIRLINE);
		g.fill(x1 - 1, y0 + 2, x1, y1 - 2, HAIRLINE);
	}

	/** A rectangle with its corners cut by a pixel or two (rounded at HUD scale). */
	static void rounded(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour) {
		g.fill(x0 + 2, y0, x1 - 2, y1, colour);
		g.fill(x0, y0 + 2, x0 + 2, y1 - 2, colour);
		g.fill(x1 - 2, y0 + 2, x1, y1 - 2, colour);
		g.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, colour);
		g.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, colour);
		g.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, colour);
		g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, colour);
	}

	/** An upper-case eyebrow at 0.6 scale. */
	static void eyebrow(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.6F, 0.6F);
		g.text(font, spaced(text), 0, 0, colour, false);
		g.pose().popMatrix();
	}

	static void eyebrowRight(GuiGraphicsExtractor g, Font font, String text, int right, int y, int colour) {
		String s = spaced(text);
		eyebrow(g, font, text, right - Math.round(font.width(s) * 0.6F), y, colour);
	}

	static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.85F, 0.85F);
		g.text(font, text, 0, 0, colour, false);
		g.pose().popMatrix();
	}

	static String spaced(String text) {
		return text.toUpperCase(java.util.Locale.ROOT);
	}
}
