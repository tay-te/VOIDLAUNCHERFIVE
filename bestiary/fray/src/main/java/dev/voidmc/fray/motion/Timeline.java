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
import org.jspecify.annotations.Nullable;

/**
 * A server timeline (ANIMATION.md §5.2): integer ticks, phases, named windows, events, hit and hurt volumes, baked from a
 * clip by {@link dev.voidmc.fray.motion.bake.TimelineBaker}. The server reads only this, never bones.
 *
 * <p>A clip may strike more than once ({@code double_claw}): each strike is an ACTIVE window {@code [start, end)}.
 * Hit volumes are capsules in entity-local blocks (Y up from the feet, facing +Z, +X the entity's left), one per tick
 * from the tick before the first strike (where the first sweep starts) to the end of the last strike. Hurt volumes
 * (weak points) have one capsule per tick of their window.
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

	/** Capsules for ticks {@code from .. from + caps.length - 1}. */
	public record Track(int from, Capsule[] caps) {
		public @Nullable Capsule at(int tick) {
			int i = tick - this.from;
			return i >= 0 && i < this.caps.length ? this.caps[i] : null;
		}
	}

	public final Identifier clip;
	public final String clipHash;
	public final Identifier rig;
	/** The custom rig's file hash ("" for the humanoid): hit volumes depend on the rig as much as on the clip. */
	public final String rigHash;
	public final int ticks;
	public final float modelScale;
	/** ACTIVE windows, {@code [start, endExclusive]} each, in order. */
	public final int[][] strikes;
	/** The first strike's window (-1 when the clip has none: an idle, a dodge). */
	public final int activeStart;
	public final int activeEnd;
	public final List<Event> events;
	public final Map<String, Track> volumes;
	public final Map<String, Track> hurtVolumes;
	public final Map<String, int[]> windows;
	public final float damage;
	public final int tellFrom;
	public final String tellColour;

	public Timeline(Identifier clip, String clipHash, Identifier rig, String rigHash, int ticks, float modelScale, int[][] strikes, List<Event> events,
		Map<String, Track> volumes, Map<String, Track> hurtVolumes, Map<String, int[]> windows, float damage, int tellFrom, String tellColour) {
		this.clip = clip;
		this.clipHash = clipHash;
		this.rig = rig;
		this.rigHash = rigHash;
		this.ticks = ticks;
		this.modelScale = modelScale;
		this.strikes = strikes;
		this.activeStart = strikes.length == 0 ? -1 : strikes[0][0];
		this.activeEnd = strikes.length == 0 ? -1 : strikes[0][1];
		this.events = events;
		this.volumes = volumes;
		this.hurtVolumes = hurtVolumes;
		this.windows = windows;
		this.damage = damage;
		this.tellFrom = tellFrom;
		this.tellColour = tellColour;
	}

	public boolean hasPhases() {
		return this.strikes.length > 0;
	}

	public int[][] strikes() {
		return this.strikes;
	}

	/** The strike whose ACTIVE window holds this tick, or -1. */
	public int strikeAt(int tick) {
		for (int i = 0; i < this.strikes.length; i++) {
			if (tick >= this.strikes[i][0] && tick < this.strikes[i][1]) {
				return i;
			}
		}
		return -1;
	}

	public boolean isActive(int tick) {
		return this.strikeAt(tick) >= 0;
	}

	public String phase(int tick) {
		if (this.strikes.length == 0) {
			return "none";
		}
		if (this.isActive(tick)) {
			return "active";
		}
		return tick < this.strikes[this.strikes.length - 1][1] ? "windup" : "recovery";
	}

	/** A named window {@code [from, to]} (inclusive ticks), or null. */
	public int @Nullable [] window(String name) {
		return this.windows.get(name);
	}

	public boolean inWindow(String name, int tick) {
		int[] w = this.windows.get(name);
		return w != null && tick >= w[0] && tick <= w[1];
	}

	// ------------------------------------------------------------------ json

	public String write() {
		StringBuilder s = new StringBuilder(1024);
		s.append("{\n  \"format\": ").append(ClipJson.FORMAT).append(",\n");
		s.append("  \"clip\": \"").append(this.clip).append("\",\n");
		s.append("  \"clip_hash\": \"").append(this.clipHash).append("\",\n");
		s.append("  \"rig\": \"").append(this.rig).append("\",\n");
		if (!this.rigHash.isEmpty()) {
			s.append("  \"rig_hash\": \"").append(this.rigHash).append("\",\n");
		}
		s.append("  \"ticks\": ").append(this.ticks).append(",\n");
		s.append("  \"model_scale\": ").append(ClipJson.num(this.modelScale)).append(",\n");
		s.append("  \"strikes\": [");
		for (int i = 0; i < this.strikes.length; i++) {
			s.append(i == 0 ? "" : ", ").append('[').append(this.strikes[i][0]).append(", ").append(this.strikes[i][1] - 1).append(']');
		}
		s.append("],\n");
		if (this.hasPhases()) {
			s.append("  \"phases\": { \"windup\": [0, ").append(this.activeStart - 1).append("], \"active\": [").append(this.activeStart).append(", ")
				.append(this.strikes[this.strikes.length - 1][1] - 1).append("], \"recovery\": [").append(this.strikes[this.strikes.length - 1][1])
				.append(", ").append(this.ticks - 1).append("] },\n");
		}
		s.append("  \"windows\": {");
		boolean first = true;
		for (Map.Entry<String, int[]> e : this.windows.entrySet()) {
			s.append(first ? " " : ", ").append('"').append(e.getKey()).append("\": [").append(e.getValue()[0]).append(", ").append(e.getValue()[1]).append(']');
			first = false;
		}
		s.append(first ? "},\n" : " },\n");
		if (this.tellFrom >= 0) {
			s.append("  \"tell\": { \"colour\": \"").append(this.tellColour).append("\", \"from\": ").append(this.tellFrom).append(" },\n");
		}
		s.append("  \"damage\": ").append(ClipJson.num(this.damage)).append(",\n");
		s.append("  \"events\": [");
		for (int i = 0; i < this.events.size(); i++) {
			Event e = this.events.get(i);
			s.append(i == 0 ? "" : ", ").append('[').append(e.tick()).append(", \"").append(e.name()).append("\", \"").append(e.arg()).append("\"]");
		}
		s.append("],\n");
		tracks(s, "hit_volumes", this.volumes);
		s.append(",\n");
		tracks(s, "hurt_volumes", this.hurtVolumes);
		s.append("\n}\n");
		return s.toString();
	}

	private static void tracks(StringBuilder s, String key, Map<String, Track> map) {
		s.append("  \"").append(key).append("\": {");
		boolean first = true;
		for (Map.Entry<String, Track> e : map.entrySet()) {
			s.append(first ? "\n" : ",\n").append("    \"").append(e.getKey()).append("\": { \"from\": ").append(e.getValue().from()).append(", \"caps\": [");
			Capsule[] caps = e.getValue().caps();
			for (int i = 0; i < caps.length; i++) {
				s.append(i == 0 ? "\n      " : ",\n      ");
				capsule(s, caps[i]);
			}
			s.append("] }");
			first = false;
		}
		s.append(first ? "}" : "\n  }");
	}

	private static void capsule(StringBuilder s, Capsule c) {
		s.append("[[").append(ClipJson.num(c.ax)).append(", ").append(ClipJson.num(c.ay)).append(", ").append(ClipJson.num(c.az)).append("], [")
			.append(ClipJson.num(c.bx)).append(", ").append(ClipJson.num(c.by)).append(", ").append(ClipJson.num(c.bz)).append("], ").append(ClipJson.num(c.r))
			.append(']');
	}

	public static Timeline parse(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		Identifier clip = Identifier.parse(o.get("clip").getAsString());
		List<int[]> strikes = new ArrayList<>();
		if (o.has("strikes")) {
			for (JsonElement e : o.getAsJsonArray("strikes")) {
				JsonArray a = e.getAsJsonArray();
				strikes.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt() + 1});
			}
		}
		List<Event> events = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("events")) {
			JsonArray a = e.getAsJsonArray();
			events.add(new Event(a.get(0).getAsInt(), a.get(1).getAsString(), a.size() > 2 ? a.get(2).getAsString() : ""));
		}
		Map<String, int[]> windows = new LinkedHashMap<>();
		if (o.has("windows")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("windows").entrySet()) {
				JsonArray a = e.getValue().getAsJsonArray();
				windows.put(e.getKey(), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
			}
		}
		int tellFrom = -1;
		String colour = "";
		if (o.has("tell")) {
			tellFrom = o.getAsJsonObject("tell").get("from").getAsInt();
			colour = o.getAsJsonObject("tell").get("colour").getAsString();
		}
		return new Timeline(clip, o.get("clip_hash").getAsString(), Identifier.parse(o.has("rig") ? o.get("rig").getAsString() : HumanoidRig.ID.toString()),
			o.has("rig_hash") ? o.get("rig_hash").getAsString() : "", o.get("ticks").getAsInt(), o.get("model_scale").getAsFloat(),
			strikes.toArray(new int[0][]), List.copyOf(events), tracks(o, "hit_volumes"), tracks(o, "hurt_volumes"), windows,
			o.has("damage") ? o.get("damage").getAsFloat() : 0, tellFrom, colour);
	}

	private static Map<String, Track> tracks(JsonObject o, String key) {
		Map<String, Track> out = new LinkedHashMap<>();
		if (!o.has(key)) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
			JsonObject t = e.getValue().getAsJsonObject();
			JsonArray arr = t.getAsJsonArray("caps");
			Capsule[] caps = new Capsule[arr.size()];
			for (int i = 0; i < caps.length; i++) {
				JsonArray a = arr.get(i).getAsJsonArray();
				JsonArray p = a.get(0).getAsJsonArray();
				JsonArray q = a.get(1).getAsJsonArray();
				caps[i] = new Capsule(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat(), q.get(0).getAsFloat(), q.get(1).getAsFloat(),
					q.get(2).getAsFloat(), a.get(2).getAsFloat());
			}
			out.put(e.getKey(), new Track(t.get("from").getAsInt(), caps));
		}
		return out;
	}
}
