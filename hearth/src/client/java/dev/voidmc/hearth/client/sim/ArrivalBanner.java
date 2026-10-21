package dev.voidmc.hearth.client.sim;

import dev.voidmc.hearth.sim.net.ArrivalPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The banner that greets a player entering a settlement — "Ashbrook", and under it "a timber-framed village of 34
 * souls" — fading in, holding, and fading out, like a place name on an old map; and under it, since 2026-10-21, the
 * day's events in the town (F7, "A wedding at the chapel · from midday"; at most two, a dot for whether you can join in,
 * trouble for someone, or something to witness), fading in 300 ms after the name and leaving with it. Everything it shows
 * came from the server ({@link ArrivalPayload}, {@code EventPayloads.ArrivalEvents}); the client decides nothing. The
 * events are the hub state's {@code arrival} section too.
 */
public final class ArrivalBanner {
	private static final long FADE_IN = 600;
	private static final long HOLD = 4200;
	private static final long FADE_OUT = 1600;

	private static @Nullable ArrivalPayload current;
	private static @Nullable ArrivalPayload shown;
	private static long shownAt;
	private static long lastShownId = Long.MIN_VALUE;
	private static long lastShownTime;
	/** Today's events in the town the player is in (F7): the town's id, the server's JSON list, and when they came. */
	private static long eventsFor = Long.MIN_VALUE;
	private static @Nullable String eventsJson;
	private static java.util.List<Line> events = java.util.List.of();
	private static long eventsAt;
	private static long revision;

	/** One event line as drawn. */
	record Line(String text, String when, String tone) {
	}

	/** The event line's dots: join in (gold), trouble (red), witness (grey). */
	static int dot(String tone) {
		return switch (tone) {
			case "join" -> 0xD9B36C;
			case "trouble" -> 0xC8554A;
			default -> 0x9A9890;
		};
	}

	/** Today's events for a town (they may come a moment after the banner). */
	static void events(long settlementId, String json) {
		eventsFor = settlementId;
		eventsJson = json;
		java.util.List<Line> list = new java.util.ArrayList<>();
		try {
			for (com.google.gson.JsonElement e : com.google.gson.JsonParser.parseString(json).getAsJsonArray()) {
				com.google.gson.JsonObject o = e.getAsJsonObject();
				list.add(new Line(o.has("text") ? o.get("text").getAsString() : "", o.has("when") ? o.get("when").getAsString() : "",
					o.has("tone") ? o.get("tone").getAsString() : "witness"));
			}
		} catch (RuntimeException ex) {
			list.clear();
		}
		events = list;
		long now = Util.getMillis();
		eventsAt = now;
		ArrivalPayload c = current;
		if (c != null && c.id() == settlementId && (shown == null || shown.id() != settlementId)) {
			// the banner was not up for this town (stepped back in within the minute): bring it up for the day's events
			shown = c;
			shownAt = now;
			lastShownId = c.id();
			lastShownTime = now;
		}
		revision++;
		dev.voidmc.hearth.client.overlay.HearthViews.bump();
	}

	/** The hub state's {@code arrival} section: today's events where the player is (null outside a town or with none sent). */
	static com.google.gson.JsonElement state() {
		ArrivalPayload c = current;
		if (c == null || c.id() != eventsFor) {
			return com.google.gson.JsonNull.INSTANCE;
		}
		ArrivalPayload s = shown;
		return dev.voidmc.hearth.client.overlay.LivingWorldJson.arrival(c.id(), c.name(), eventsJson, s != null && s.id() == c.id());
	}

	static long revision() {
		return revision * 31 + System.identityHashCode(current) * 7L + System.identityHashCode(shown);
	}

	private ArrivalBanner() {
	}

	/** The settlement the player stands in, as the server last said (for the F3 line). */
	public static @Nullable ArrivalPayload current() {
		return current;
	}

	static void receive(ArrivalPayload payload) {
		current = payload.left() ? null : payload;
		revision++;
		if (payload.left()) {
			return;
		}
		long now = Util.getMillis();
		// stepping back and forth over the edge of town should not greet you every time
		if (payload.id() == lastShownId && now - lastShownTime < 60_000) {
			return;
		}
		shown = payload;
		shownAt = now;
		lastShownId = payload.id();
		lastShownTime = now;
	}

	static void reset() {
		current = null;
		shown = null;
		lastShownId = Long.MIN_VALUE;
		eventsFor = Long.MIN_VALUE;
		eventsJson = null;
		events = java.util.List.of();
		revision++;
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		ArrivalPayload p = shown;
		Minecraft mc = Minecraft.getInstance();
		if (p == null) {
			return;
		}
		long age = Util.getMillis() - shownAt;
		if (age > FADE_IN + HOLD + FADE_OUT) {
			shown = null;
			revision++;
			return;
		}
		float alpha = age < FADE_IN ? age / (float) FADE_IN : age < FADE_IN + HOLD ? 1 : 1 - (age - FADE_IN - HOLD) / (float) FADE_OUT;
		int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 4;
		Component title = Component.literal(p.name());
		Component subtitle = Component.translatable(p.population() == 1 ? "hearth.sim.banner.subtitle.one" : "hearth.sim.banner.subtitle",
			Component.translatable("hearth.sim.banner.flavour." + p.culture()), Component.translatable("hearth.sim.banner.tier." + p.tier()), p.population());
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(2.5F, 2.5F);
		g.text(font, title, -font.width(title) / 2, -font.lineHeight, ARGB.color(a, 0xF2, 0xE6, 0xC8), true);
		g.pose().popMatrix();
		g.pose().pushMatrix();
		g.pose().translate(cx, y + 6);
		g.text(font, subtitle, -font.width(subtitle) / 2, 0, ARGB.color(a, 0xC8, 0xBC, 0xA0), true);
		g.pose().popMatrix();
		if (eventsFor == p.id() && !events.isEmpty()) {
			lines(g, font, cx, y + 6 + font.lineHeight + 6, age);
		}
	}

	/** The day's events under the subtitle: a short rule, then a line each, fading in 300 ms after the name, out with it. */
	private static void lines(GuiGraphicsExtractor g, Font font, int cx, int top, long age) {
		long since = Math.min(age - 300, Util.getMillis() - eventsAt);
		float in = since <= 0 ? 0 : Math.min(1, since / (float) FADE_IN);
		float out = age < FADE_IN + HOLD ? 1 : 1 - (age - FADE_IN - HOLD) / (float) FADE_OUT;
		int a = (int) (Math.max(0, Math.min(in, out)) * 255);
		if (a < 5) {
			return;
		}
		g.fill(cx - 20, top, cx + 20, top + 1, ARGB.color(a / 3, 0xD9, 0xB3, 0x6C));
		int y = top + 7;
		for (Line l : events) {
			String when = l.when();
			int tw = font.width(l.text());
			int ww = when.isEmpty() ? 0 : Math.round(font.width(when) * 0.8F) + 5;
			int x = cx - (8 + tw + ww) / 2;
			int dot = dot(l.tone());
			g.fill(x + 1, y + 2, x + 4, y + 5, ARGB.color(a, (dot >> 16) & 0xFF, (dot >> 8) & 0xFF, dot & 0xFF));
			g.text(font, l.text(), x + 8, y, ARGB.color(a, 0xF2, 0xE6, 0xC8), true);
			if (!when.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(x + 8 + tw + 5, y + 1);
				g.pose().scale(0.8F, 0.8F);
				g.text(font, when, 0, 0, ARGB.color(a, 0xB4, 0xA8, 0x8C), true);
				g.pose().popMatrix();
			}
			y += font.lineHeight + 5;
		}
	}
}
