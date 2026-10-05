package dev.voidmc.atlas.core.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.atlas.core.rules.Restrictions.Say;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The map restrictions a server asks for, read from the conventions other map mods already use, so a server that
 * restricts Xaero's Minimap or VoxelMap restricts Atlas too without doing anything for it. Sources and the exact codes
 * are in {@code docs/SERVER_RULES.md}.
 *
 * <p>Three ways in:
 * <ul>
 * <li>{@link #accept(String)}: text from the server (the system/login message, the MOTD, the tab list header and
 * footer), with its {@code §} codes as received. Never feed it player chat: anyone can type there.</li>
 * <li>{@link #acceptVoxelMapSettings(byte[])}: the {@value #VOXELMAP_SETTINGS} plugin message.</li>
 * <li>{@link #acceptJourneyMapPermissions(byte[])}: JourneyMap's {@value #JOURNEYMAP_PERMISSIONS} reply.</li>
 * </ul>
 * Each source is kept apart and the result is the most restrictive of them ({@link Restrictions#merge}). Codes stick
 * until a reset code; a settings packet replaces what the same source said before. {@link #clear()} on disconnect.
 *
 * <p>Atlas's defaults for what servers leave unsaid are in {@link Restrictions#allowed}: in multiplayer the radar is
 * off until a server opts in ({@link ServerCode#ATLAS_RADAR}, or Rei's keyword), the cave view is on until a server
 * says no.
 *
 * <p>Thread-safe.
 */
public final class ServerRules {
	/** VoxelMap's settings channel (its own source: a byte 0, then a JSON object as a length-prefixed UTF-8 string). */
	public static final String VOXELMAP_SETTINGS = "voxelmap:settings";
	/** JourneyMap's permission channel, 1.18 and later (payload shape from a third-party server; see the doc). */
	public static final String JOURNEYMAP_PERMISSIONS = "journeymap:perm_req";
	/** JourneyMap's older shared channel; the permission reply there starts with a type byte 2. */
	public static final String JOURNEYMAP_COMMON = "journeymap:common";

	/** Rei's Minimap keyword: §0§0, then any of §1..§7 (what to enable), then §e§f. */
	private static final Pattern REI = Pattern.compile("§0§0((?:§[1-7])+)§e§f");
	/** JourneyMap's marker byte in its permission reply. */
	private static final int JOURNEYMAP_MARKER = 42;

	/**
	 * A code found in a text.
	 *
	 * @param start  where it starts in the text
	 * @param end    just after it
	 * @param effect what it says (for most codes, {@link ServerCode#effect()})
	 */
	public record Found(ServerCode code, int start, int end, Restrictions effect) {
	}

	/** The codes in one text, in the order they appear. */
	public record Scan(List<Found> found) {
		public Scan {
			found = List.copyOf(found);
		}

		/** Whether a reset code is among them. */
		public boolean reset() {
			return this.found.stream().anyMatch(f -> f.code().reset());
		}

		/** {@code before}, after these codes in order: a reset starts over, the rest merge in. */
		public Restrictions applyTo(Restrictions before) {
			Restrictions r = before;
			for (Found f : this.found) {
				r = f.code().reset() ? Restrictions.NONE : r.merge(f.effect());
			}
			return r;
		}

		/** What this text alone says. */
		public Restrictions result() {
			return this.applyTo(Restrictions.NONE);
		}

		public boolean isEmpty() {
			return this.found.isEmpty();
		}
	}

	private final boolean multiplayer;
	private Restrictions codes = Restrictions.NONE;
	private final Map<String, Boolean> voxelMap = new LinkedHashMap<>();
	private Restrictions journeyMap = Restrictions.NONE;

	/** @param multiplayer false on the integrated (single-player) server, where nothing is restricted */
	public ServerRules(boolean multiplayer) {
		this.multiplayer = multiplayer;
	}

	// ---------------------------------------------------------------- text codes

	/**
	 * Finds the known codes in {@code text} (as received, with its {@code §} codes). Matching is exact, as the mods
	 * that defined the codes do it, except that code letters may be upper or lower case (the game treats them the
	 * same). Where two codes overlap, the longer one counts: Xaero's Nether codes end in the letters of the fair-play
	 * code, and two of them back to back must not read as fair-play.
	 */
	public static Scan scan(String text) {
		if (text == null || text.indexOf('§') < 0) {
			return new Scan(List.of());
		}
		String lower = lowerSameLength(text);
		List<Found> all = new ArrayList<>();
		for (ServerCode code : ServerCode.values()) {
			if (code.text() == null) {
				continue;
			}
			for (int at = lower.indexOf(code.text()); at >= 0; at = lower.indexOf(code.text(), at + 1)) {
				all.add(new Found(code, at, at + code.text().length(), code.effect()));
			}
		}
		Matcher rei = REI.matcher(lower);
		while (rei.find()) {
			all.add(new Found(ServerCode.REI_KEYWORD, rei.start(), rei.end(), reiEffect(rei.group(1))));
		}
		all.sort(Comparator.comparingInt((Found f) -> f.end() - f.start()).reversed().thenComparingInt(Found::start));
		List<Found> kept = new ArrayList<>();
		for (Found f : all) {
			boolean overlaps = false;
			for (Found k : kept) {
				if (f.start() < k.end() && k.start() < f.end()) {
					overlaps = true;
					break;
				}
			}
			if (!overlaps) {
				kept.add(f);
			}
		}
		kept.sort(Comparator.comparingInt(Found::start));
		return new Scan(kept);
	}

	/** Rei's digits: 1 cave mapping, 2 player radar, 4 mob radar (3 animals, 5 slimes, 6 squid, 7 other are not separate in Atlas). */
	private static Restrictions reiEffect(String digits) {
		Restrictions r = Restrictions.NONE;
		if (digits.contains("1")) {
			r = r.withCaveMap(Say.ALLOW);
		}
		if (digits.contains("2")) {
			r = r.withRadarPlayers(Say.ALLOW);
		}
		if (digits.contains("4")) {
			r = r.withRadarMobs(Say.ALLOW);
		}
		return r;
	}

	/** Lower-cases one char at a time, so indexes still line up with the original text. */
	private static String lowerSameLength(String text) {
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			chars[i] = Character.toLowerCase(chars[i]);
		}
		return new String(chars);
	}

	/** Reads a server text's codes into the rules; returns what it found. */
	public synchronized Scan accept(String text) {
		Scan scan = scan(text);
		this.codes = scan.applyTo(this.codes);
		return scan;
	}

	// ---------------------------------------------------------------- channels

	/**
	 * Reads VoxelMap's {@value #VOXELMAP_SETTINGS} payload: a byte (0), then a JSON object as a VarInt-length-prefixed
	 * UTF-8 string, as VoxelMap's own {@code VoxelMapSettingsPayload} reads it. Each key replaces what an earlier packet
	 * said for it. Throws {@link IllegalArgumentException} if the payload is not that shape.
	 */
	public synchronized void acceptVoxelMapSettings(byte[] payload) {
		if (payload.length < 1) {
			throw new IllegalArgumentException("empty VoxelMap settings payload");
		}
		int[] pos = {1};
		String json = readUtf(payload, pos);
		this.voxelMap.putAll(voxelMapKeys(json));
	}

	/** The restrictions a VoxelMap settings JSON object says, on its own. */
	public static Restrictions parseVoxelMapSettings(String json) {
		return voxelMapRestrictions(voxelMapKeys(json));
	}

	private static Map<String, Boolean> voxelMapKeys(String json) {
		JsonObject o;
		try {
			o = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("VoxelMap settings are not a JSON object: " + e.getMessage());
		}
		Map<String, Boolean> out = new LinkedHashMap<>();
		for (String key : List.of("radarAllowed", "radarPlayersAllowed", "radarMobsAllowed", "cavesAllowed", "minimapAllowed", "worldmapAllowed", "waypointsAllowed")) {
			JsonElement e = o.get(key);
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				out.put(key, e.getAsBoolean());
			}
		}
		return out;
	}

	/**
	 * VoxelMap's switches as restrictions. Only "false" restricts: "true" is VoxelMap's default and does not opt into
	 * Atlas's radar.
	 */
	private static Restrictions voxelMapRestrictions(Map<String, Boolean> keys) {
		Restrictions r = Restrictions.NONE;
		if (Boolean.FALSE.equals(keys.get("radarAllowed"))) {
			r = r.withRadarPlayers(Say.DENY).withRadarMobs(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("radarPlayersAllowed"))) {
			r = r.withRadarPlayers(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("radarMobsAllowed"))) {
			r = r.withRadarMobs(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("cavesAllowed"))) {
			r = r.withCaveMap(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("minimapAllowed"))) {
			r = r.withMinimap(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("worldmapAllowed"))) {
			r = r.withWorldMap(Say.DENY);
		}
		if (Boolean.FALSE.equals(keys.get("waypointsAllowed"))) {
			r = r.withWaypoints(Say.DENY);
		}
		return r;
	}

	/**
	 * Reads JourneyMap's permission reply ({@value #JOURNEYMAP_PERMISSIONS}, or {@value #JOURNEYMAP_COMMON} with a
	 * leading type byte): the marker byte 42, whether the player is an operator, then the effective server config as
	 * a length-prefixed UTF-8 JSON string. It replaces what an earlier reply said. Throws
	 * {@link IllegalArgumentException} if the payload is not that shape. Unverified against JourneyMap itself: see the doc.
	 */
	public synchronized void acceptJourneyMapPermissions(byte[] payload) {
		this.journeyMap = decodeJourneyMapPermissions(payload);
	}

	/** The restrictions in a JourneyMap permission reply. */
	public static Restrictions decodeJourneyMapPermissions(byte[] payload) {
		int start;
		if (payload.length > 0 && payload[0] == JOURNEYMAP_MARKER) {
			start = 0;
		} else if (payload.length > 1 && payload[1] == JOURNEYMAP_MARKER) {
			start = 1;
		} else {
			throw new IllegalArgumentException("not a JourneyMap permission reply");
		}
		if (payload.length < start + 3) {
			throw new IllegalArgumentException("JourneyMap permission reply cut short");
		}
		boolean op = payload[start + 1] != 0;
		int[] pos = {start + 2};
		return parseJourneyMapConfig(readUtf(payload, pos), op);
	}

	/**
	 * JourneyMap's server config as restrictions, for a player who is ({@code op}) or is not an operator. Levels are
	 * {@code ALL}, {@code OPS} or {@code NONE}; switches are {@code "true"} or {@code "false"}. Only a "no" restricts.
	 */
	public static Restrictions parseJourneyMapConfig(String json, boolean op) {
		JsonObject o;
		try {
			o = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("JourneyMap config is not a JSON object: " + e.getMessage());
		}
		Restrictions r = Restrictions.NONE;
		if (no(o, "journeymapEnabled", op)) {
			r = r.withMinimap(Say.DENY).withWorldMap(Say.DENY);
		}
		if (no(o, "radarEnabled", op)) {
			r = r.withRadarPlayers(Say.DENY).withRadarMobs(Say.DENY);
		}
		if (no(o, "playerRadarEnabled", op)) {
			r = r.withRadarPlayers(Say.DENY);
		}
		if (no(o, "mobRadarEnabled", op) || no(o, "animalRadarEnabled", op) || no(o, "villagerRadarEnabled", op)) {
			r = r.withRadarMobs(Say.DENY);
		}
		if (no(o, "caveMapping", op)) {
			r = r.withCaveMap(Say.DENY);
		}
		if (no(o, "allowWaypoints", op)) {
			r = r.withWaypoints(Say.DENY);
		}
		return r;
	}

	/** Whether a JourneyMap setting says no to this player; anything unrecognised is not a no. */
	private static boolean no(JsonObject o, String key, boolean op) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return false;
		}
		String v = e.getAsString().trim();
		return v.equalsIgnoreCase("false") || v.equalsIgnoreCase("none") || (v.equalsIgnoreCase("ops") && !op);
	}

	/** Minecraft's string: a VarInt byte count, then that many bytes of UTF-8. Advances {@code pos[0]}. */
	static String readUtf(byte[] data, int[] pos) {
		int length = 0;
		int shift = 0;
		while (true) {
			if (pos[0] >= data.length) {
				throw new IllegalArgumentException("cut short in a string length");
			}
			byte b = data[pos[0]++];
			length |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				break;
			}
			shift += 7;
			if (shift >= 35) {
				throw new IllegalArgumentException("string length too long");
			}
		}
		if (length < 0 || length > data.length - pos[0]) {
			throw new IllegalArgumentException("string of " + length + " bytes but " + (data.length - pos[0]) + " left");
		}
		String s = new String(data, pos[0], length, StandardCharsets.UTF_8);
		pos[0] += length;
		return s;
	}

	// ---------------------------------------------------------------- state

	/** Forgets everything: call on disconnect, and when joining another server. */
	public synchronized void clear() {
		this.codes = Restrictions.NONE;
		this.voxelMap.clear();
		this.journeyMap = Restrictions.NONE;
	}

	/** What every source together says (most restrictive wins). */
	public synchronized Restrictions restrictions() {
		return this.codes.merge(voxelMapRestrictions(this.voxelMap)).merge(this.journeyMap);
	}

	/** What Atlas lets the player use now. */
	public synchronized Restrictions.Allowed allowed() {
		return this.restrictions().allowed(this.multiplayer);
	}

	public boolean multiplayer() {
		return this.multiplayer;
	}
}
