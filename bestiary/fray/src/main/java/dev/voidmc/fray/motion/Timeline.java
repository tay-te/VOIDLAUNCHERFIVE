package dev.voidmc.fray.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * A server timeline (ANIMATION.md §5.2): integer ticks, phases, events and hit volumes, baked from a clip by
 * {@link dev.voidmc.fray.motion.bake.TimelineBaker}. The server reads only this, never bones.
 *
 * <p>Hit volumes are capsules in entity-local blocks (Y up from the feet, facing +Z, +X the entity's left), one per
 * ACTIVE tick, plus the capsule of the tick before ACTIVE as the start of the first sweep.
 */
public final class Timeline {
	public record Event(int tick, String name, String arg) {
	}

	/** One capsule: a and b in entity-local blocks, radius in blocks. */
	public record Capsule(float ax, float ay, float az, float bx, float by, float bz, float r) {
		public float reach() {
			return (float) Math.max(Math.hypot(this.ax, this.az), Math.hypot(this.bx, this.bz)) + this.r;
		}
	}

	public final Identifier clip;
	public final String clipHash;
	public final int ticks;
	public final float modelScale;
	/** First ACTIVE tick and the first tick after ACTIVE; both -1 when the clip has no phases (an idle). */
	public final int activeStart;
	public final int activeEnd;
	public final List<Event> events;
	/** Per hit volume name: capsules for ticks activeStart..activeEnd-1. */
	public final Map<String, Capsule[]> volumes;
	/** Per hit volume name: the capsule at activeStart-1 (where the first sweep starts). */
	public final Map<String, Capsule> sweepFrom;
	public final float damage;
	public final int tellFrom;
	public final String tellColour;

	public Timeline(Identifier clip, String clipHash, int ticks, float modelScale, int activeStart, int activeEnd, List<Event> events,
		Map<String, Capsule[]> volumes, Map<String, Capsule> sweepFrom, float damage, int tellFrom, String tellColour) {
		this.clip = clip;
		this.clipHash = clipHash;
		this.ticks = ticks;
		this.modelScale = modelScale;
		this.activeStart = activeStart;
		this.activeEnd = activeEnd;
		this.events = events;
		this.volumes = volumes;
		this.sweepFrom = sweepFrom;
		this.damage = damage;
		this.tellFrom = tellFrom;
		this.tellColour = tellColour;
	}

	public boolean hasPhases() {
		return this.activeStart >= 0;
	}

	public boolean isActive(int tick) {
		return this.activeStart >= 0 && tick >= this.activeStart && tick < this.activeEnd;
	}

	public String phase(int tick) {
		if (this.activeStart < 0) {
			return "none";
		}
		return tick < this.activeStart ? "windup" : tick < this.activeEnd ? "active" : "recovery";
	}

	// ------------------------------------------------------------------ json

	public String write() {
		StringBuilder s = new StringBuilder(1024);
		s.append("{\n  \"format\": ").append(ClipJson.FORMAT).append(",\n");
		s.append("  \"clip\": \"").append(this.clip).append("\",\n");
		s.append("  \"clip_hash\": \"").append(this.clipHash).append("\",\n");
		s.append("  \"ticks\": ").append(this.ticks).append(",\n");
		s.append("  \"model_scale\": ").append(ClipJson.num(this.modelScale)).append(",\n");
		if (this.hasPhases()) {
			s.append("  \"phases\": { \"windup\": [0, ").append(this.activeStart - 1).append("], \"active\": [").append(this.activeStart).append(", ")
				.append(this.activeEnd - 1).append("], \"recovery\": [").append(this.activeEnd).append(", ").append(this.ticks - 1).append("] },\n");
		} else {
			s.append("  \"phases\": {},\n");
		}
		if (this.tellFrom >= 0) {
			s.append("  \"tell\": { \"colour\": \"").append(this.tellColour).append("\", \"from\": ").append(this.tellFrom).append(" },\n");
		}
		s.append("  \"damage\": ").append(ClipJson.num(this.damage)).append(",\n");
		s.append("  \"events\": [");
		for (int i = 0; i < this.events.size(); i++) {
			Event e = this.events.get(i);
			s.append(i == 0 ? "" : ", ").append('[').append(e.tick()).append(", \"").append(e.name()).append("\", \"").append(e.arg()).append("\"]");
		}
		s.append("],\n  \"hit_volumes\": {");
		boolean first = true;
		for (Map.Entry<String, Capsule[]> e : this.volumes.entrySet()) {
			s.append(first ? "\n" : ",\n").append("    \"").append(e.getKey()).append("\": [");
			Capsule[] caps = e.getValue();
			for (int i = 0; i < caps.length; i++) {
				s.append(i == 0 ? "" : ",\n      ");
				capsule(s, this.activeStart + i, caps[i]);
			}
			s.append(']');
			first = false;
		}
		s.append(first ? "},\n" : "\n  },\n");
		s.append("  \"sweep_from\": {");
		first = true;
		for (Map.Entry<String, Capsule> e : this.sweepFrom.entrySet()) {
			s.append(first ? " " : ", ").append('"').append(e.getKey()).append("\": ");
			capsule(s, this.activeStart - 1, e.getValue());
			first = false;
		}
		s.append(first ? "}\n}\n" : " }\n}\n");
		return s.toString();
	}

	private static void capsule(StringBuilder s, int tick, Capsule c) {
		s.append('[').append(tick).append(", [").append(ClipJson.num(c.ax)).append(", ").append(ClipJson.num(c.ay)).append(", ").append(ClipJson.num(c.az))
			.append("], [").append(ClipJson.num(c.bx)).append(", ").append(ClipJson.num(c.by)).append(", ").append(ClipJson.num(c.bz)).append("], ")
			.append(ClipJson.num(c.r)).append(']');
	}

	public static Timeline parse(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		Identifier clip = Identifier.parse(o.get("clip").getAsString());
		int start = -1, end = -1;
		JsonObject phases = o.getAsJsonObject("phases");
		if (phases.has("active")) {
			JsonArray a = phases.getAsJsonArray("active");
			start = a.get(0).getAsInt();
			end = a.get(1).getAsInt() + 1;
		}
		List<Event> events = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("events")) {
			JsonArray a = e.getAsJsonArray();
			events.add(new Event(a.get(0).getAsInt(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsString() : ""));
		}
		Map<String, Capsule[]> volumes = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("hit_volumes").entrySet()) {
			JsonArray arr = e.getValue().getAsJsonArray();
			Capsule[] caps = new Capsule[arr.size()];
			for (int i = 0; i < caps.length; i++) {
				caps[i] = capsule(arr.get(i).getAsJsonArray());
			}
			volumes.put(e.getKey(), caps);
		}
		Map<String, Capsule> from = new LinkedHashMap<>();
		if (o.has("sweep_from")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("sweep_from").entrySet()) {
				from.put(e.getKey(), capsule(e.getValue().getAsJsonArray()));
			}
		}
		int tellFrom = -1;
		String colour = "";
		if (o.has("tell")) {
			tellFrom = o.getAsJsonObject("tell").get("from").getAsInt();
			colour = o.getAsJsonObject("tell").get("colour").getAsString();
		}
		return new Timeline(clip, o.get("clip_hash").getAsString(), o.get("ticks").getAsInt(), o.get("model_scale").getAsFloat(), start, end,
			List.copyOf(events), volumes, from, o.has("damage") ? o.get("damage").getAsFloat() : 0, tellFrom, colour);
	}

	private static Capsule capsule(JsonArray a) {
		JsonArray p = a.get(1).getAsJsonArray();
		JsonArray q = a.get(2).getAsJsonArray();
		return new Capsule(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), q.get(0).getAsFloat(), q.get(1).getAsFloat(),
			q.get(2).getAsFloat(), a.get(3).getAsFloat());
	}
}
