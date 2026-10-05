package dev.voidmc.atlas.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.atlas.core.rules.Restrictions.Allowed;
import dev.voidmc.atlas.core.rules.Restrictions.Say;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Server map restrictions: each known code, codes inside other text, resets, false positives, and the channels. */
class ServerRulesTest {
	static final String FAIR = "§f§a§i§r§x§a§e§r§o";
	static final String NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
	static final String RESET = "§r§e§s§e§t§x§a§e§r§o";
	static final String MM_NETHER = "§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r";
	static final String WM_NETHER = "§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";
	static final String VM_NO_RADAR = "§3 §6 §3 §6 §3 §6 §e";
	static final String VM_NO_CAVES = "§3 §6 §3 §6 §3 §6 §d";
	static final String ATLAS_RADAR = "§a§t§l§a§s§r§a§d§a§r";
	static final String ATLAS_MOB_RADAR = "§a§t§l§a§s§m§o§b§r§a§d§a§r";

	private static List<ServerCode> codes(String text) {
		return ServerRules.scan(text).found().stream().map(ServerRules.Found::code).toList();
	}

	private static Allowed allowed(String... texts) {
		ServerRules rules = new ServerRules(true);
		for (String t : texts) {
			rules.accept(t);
		}
		return rules.allowed();
	}

	// ---------------------------------------------------------------- each code

	@Test
	void codesMatchTheirEnumText() {
		assertEquals(FAIR, ServerCode.XAERO_FAIR_PLAY.text());
		assertEquals(NO_MINIMAP, ServerCode.XAERO_NO_MINIMAP.text());
		assertEquals(RESET, ServerCode.XAERO_RESET.text());
		assertEquals(MM_NETHER, ServerCode.XAERO_MINIMAP_NETHER_CAVES.text());
		assertEquals(WM_NETHER, ServerCode.XAERO_WORLD_MAP_NETHER_CAVES.text());
		assertEquals(VM_NO_RADAR, ServerCode.VOXELMAP_NO_RADAR.text());
		assertEquals(VM_NO_CAVES, ServerCode.VOXELMAP_NO_CAVES.text());
		assertEquals(ATLAS_RADAR, ServerCode.ATLAS_RADAR.text());
		assertEquals(ATLAS_MOB_RADAR, ServerCode.ATLAS_MOB_RADAR.text());
	}

	@Test
	void xaeroFairPlay() {
		assertEquals(List.of(ServerCode.XAERO_FAIR_PLAY), codes(FAIR));
		Restrictions r = ServerRules.scan(FAIR).result();
		assertEquals(Say.DENY, r.caveMap());
		assertEquals(Say.DENY, r.radarPlayers());
		assertEquals(Say.DENY, r.radarMobs());
		assertEquals(Say.UNSET, r.minimap());
		Allowed a = allowed(FAIR);
		assertFalse(a.caveView());
		assertFalse(a.caveViewIn(true, false));
		assertFalse(a.caveViewIn(true, true));
		assertTrue(a.minimap());
		assertTrue(a.worldMap());
	}

	@Test
	void xaeroNoMinimap() {
		Allowed a = allowed(NO_MINIMAP);
		assertFalse(a.minimap());
		assertTrue(a.worldMap(), "the code is for the minimap only");
		assertTrue(a.caveView());
	}

	@Test
	void xaeroNetherCodesOnlyLiftTheirOwnMap() {
		Allowed minimapOnly = allowed(FAIR + MM_NETHER);
		assertFalse(minimapOnly.caveView());
		assertTrue(minimapOnly.caveViewIn(true, false));
		assertFalse(minimapOnly.caveViewIn(true, true));
		assertFalse(minimapOnly.caveViewIn(false, false));

		Allowed worldMapOnly = allowed(FAIR, WM_NETHER);
		assertFalse(worldMapOnly.caveViewIn(true, false));
		assertTrue(worldMapOnly.caveViewIn(true, true));

		Allowed both = allowed(FAIR + MM_NETHER + WM_NETHER);
		assertTrue(both.caveViewIn(true, false));
		assertTrue(both.caveViewIn(true, true));
		assertFalse(both.caveViewIn(false, true));
	}

	@Test
	void netherCodeAloneChangesNothingVisible() {
		Allowed a = allowed(MM_NETHER);
		assertTrue(a.caveView());
		assertTrue(a.caveViewIn(true, false));
	}

	@Test
	void backToBackNetherCodesAreNotFairPlay() {
		// the minimap code ends "...§f§a§i§r" and the world map code starts "§x§a§e§r§o": fair-play spelled across them
		String text = MM_NETHER + WM_NETHER;
		assertTrue(text.contains(FAIR));
		assertEquals(List.of(ServerCode.XAERO_MINIMAP_NETHER_CAVES, ServerCode.XAERO_WORLD_MAP_NETHER_CAVES), codes(text));
		assertTrue(allowed(text).caveView());
	}

	@Test
	void fairPlayRightAfterANetherCodeIsStillFound() {
		assertEquals(List.of(ServerCode.XAERO_MINIMAP_NETHER_CAVES, ServerCode.XAERO_FAIR_PLAY), codes(MM_NETHER + FAIR));
	}

	@Test
	void voxelMapCodes() {
		Allowed noRadar = new ServerRules(true).allowed();
		assertFalse(noRadar.radarPlayers(), "multiplayer radar is off by default anyway");
		Restrictions r = ServerRules.scan(VM_NO_RADAR).result();
		assertEquals(Say.DENY, r.radarPlayers());
		assertEquals(Say.DENY, r.radarMobs());
		assertEquals(Say.UNSET, r.caveMap());
		assertFalse(allowed(VM_NO_CAVES).caveView());
		assertTrue(allowed(VM_NO_RADAR).caveView());
		// the radar code and the cave code on separate lines of one MOTD
		assertEquals(List.of(ServerCode.VOXELMAP_NO_RADAR, ServerCode.VOXELMAP_NO_CAVES), codes(VM_NO_RADAR + "\n" + VM_NO_CAVES));
	}

	@Test
	void voxelMapCodesNeedTheirSpaces() {
		assertTrue(codes("§3§6§3§6§3§6§e").isEmpty());
		assertTrue(codes("§3 §6 §3 §6 §3 §e").isEmpty());
	}

	@Test
	void atlasRadarOptIn() {
		Allowed a = allowed(ATLAS_RADAR);
		assertTrue(a.radarPlayers());
		assertTrue(a.radarMobs());
		Allowed mobs = allowed(ATLAS_MOB_RADAR);
		assertFalse(mobs.radarPlayers());
		assertTrue(mobs.radarMobs());
		assertEquals(List.of(ServerCode.ATLAS_MOB_RADAR), codes(ATLAS_MOB_RADAR), "the mob code does not also read as the full one");
	}

	@Test
	void reiKeyword() {
		Restrictions all = ServerRules.scan("§0§0§1§2§3§4§5§6§7§e§f").result();
		assertEquals(Say.ALLOW, all.caveMap());
		assertEquals(Say.ALLOW, all.radarPlayers());
		assertEquals(Say.ALLOW, all.radarMobs());
		Allowed a = allowed("§0§0§1§2§3§4§5§6§7§e§f");
		assertTrue(a.radarPlayers());
		assertTrue(a.radarMobs());

		Allowed caveOnly = allowed("§0§0§1§e§f");
		assertTrue(caveOnly.caveView());
		assertFalse(caveOnly.radarPlayers());
		Allowed animals = allowed("§0§0§3§5§e§f");
		assertFalse(animals.radarMobs(), "animals and slimes alone are not Atlas's mob radar");
		assertEquals(List.of(ServerCode.REI_KEYWORD), codes("Welcome! §0§0§2§e§f"));
	}

	@Test
	void reiKeywordMustBeWellFormed() {
		assertTrue(codes("§0§0§e§f").isEmpty());
		assertTrue(codes("§0§0§8§e§f").isEmpty());
		assertTrue(codes("§0§0§1§2§e").isEmpty());
		assertTrue(codes("§0§1§2§e§f").isEmpty());
	}

	// ---------------------------------------------------------------- defaults

	@Test
	void multiplayerDefaults() {
		Allowed a = new ServerRules(true).allowed();
		assertTrue(a.minimap());
		assertTrue(a.worldMap());
		assertTrue(a.caveView());
		assertTrue(a.caveViewIn(true, true));
		assertTrue(a.waypoints());
		assertFalse(a.radarPlayers());
		assertFalse(a.radarMobs());
	}

	@Test
	void singlePlayerAllowsEverything() {
		ServerRules rules = new ServerRules(false);
		rules.accept(FAIR + NO_MINIMAP);
		Allowed a = rules.allowed();
		assertTrue(a.minimap() && a.caveView() && a.radarPlayers() && a.radarMobs() && a.waypoints());
		assertFalse(rules.multiplayer());
	}

	// ---------------------------------------------------------------- merging and resets

	@Test
	void mostRestrictiveWins() {
		assertFalse(allowed(ATLAS_RADAR + VM_NO_RADAR).radarPlayers());
		assertFalse(allowed(VM_NO_RADAR + ATLAS_RADAR).radarPlayers(), "order does not matter");
		assertFalse(allowed(VM_NO_RADAR, ATLAS_RADAR).radarMobs(), "nor across messages");
		assertFalse(allowed("§0§0§1§e§f", VM_NO_CAVES).caveView());
	}

	@Test
	void resetClearsWhatCameBefore() {
		assertTrue(allowed(FAIR + RESET).caveView());
		assertFalse(allowed(RESET + FAIR).caveView(), "a reset first, then fair-play: fair-play stands");
		assertTrue(allowed(FAIR, NO_MINIMAP, RESET).minimap());
		assertTrue(allowed(FAIR, RESET).caveView());
		assertFalse(allowed(ATLAS_RADAR, RESET).radarPlayers(), "a reset clears opt-ins too");
		assertTrue(ServerRules.scan(RESET).reset());
		assertFalse(ServerRules.scan(FAIR).reset());
	}

	@Test
	void resetDoesNotClearChannelSettings() {
		ServerRules rules = new ServerRules(true);
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"cavesAllowed\":false}"));
		rules.accept(FAIR);
		rules.accept(RESET);
		assertFalse(rules.allowed().caveView());
		rules.clear();
		assertTrue(rules.allowed().caveView());
	}

	@Test
	void acceptReturnsWhatItFound() {
		ServerRules rules = new ServerRules(true);
		ServerRules.Scan scan = rules.accept("§6Welcome§r " + FAIR);
		assertEquals(1, scan.found().size());
		assertEquals(12, scan.found().get(0).start());
		assertEquals(12 + FAIR.length(), scan.found().get(0).end());
		assertTrue(rules.accept("hello").isEmpty());
	}

	// ---------------------------------------------------------------- where codes appear

	@Test
	void codesInsideLongerText() {
		String motd = "§6§lExample Network§r §7- §aSurvival is open!\n§7Have fun." + FAIR + NO_MINIMAP;
		assertEquals(List.of(ServerCode.XAERO_FAIR_PLAY, ServerCode.XAERO_NO_MINIMAP), codes(motd));
		String chat = "[Server] Welcome back, Steve! " + VM_NO_RADAR + " Rules: be nice.";
		assertEquals(List.of(ServerCode.VOXELMAP_NO_RADAR), codes(chat));
	}

	@Test
	void codesTouchingOtherFormatting() {
		assertEquals(List.of(ServerCode.XAERO_FAIR_PLAY), codes("§l§6Hi§r" + FAIR + "§r§7!"));
		assertEquals(List.of(ServerCode.XAERO_FAIR_PLAY), codes("§k§k§k" + FAIR + "§k"));
		assertEquals(List.of(ServerCode.ATLAS_RADAR), codes("§a§a§a" + ATLAS_RADAR));
	}

	@Test
	void upperCaseCodeLetters() {
		assertEquals(List.of(ServerCode.XAERO_FAIR_PLAY), codes("§F§A§I§R§X§A§E§R§O"));
		assertEquals(List.of(ServerCode.VOXELMAP_NO_RADAR), codes("§3 §6 §3 §6 §3 §6 §E"));
	}

	@Test
	void codesSplitByOtherFormattingDoNotCount() {
		// matched exactly, like the mods that defined them: a code with something else inside it is not that code
		assertTrue(codes("§f§a§i§r§7§x§a§e§r§o").isEmpty());
		assertTrue(codes("§f§a§i§r §x§a§e§r§o").isEmpty());
		assertTrue(codes("§n§o§m§i§n§i§r§m§a§p").isEmpty());
		assertTrue(codes("§3 §6 §3 §6 §l§3 §6 §e").isEmpty());
	}

	@Test
	void codesSplitAcrossMessagesDoNotCount() {
		Allowed a = allowed("§f§a§i§r", "§x§a§e§r§o");
		assertTrue(a.caveView());
	}

	@Test
	void codeSplitAcrossComponentsOfOneMessageCounts() {
		// a message built from parts ("§f§a§i§r" + "§x§a§e§r§o") reads as one string
		assertFalse(allowed(String.join("", "§f§a§i§r", "§x§a§e§r§o")).caveView());
	}

	@Test
	void falsePositives() {
		assertTrue(codes("fairxaero nominimap resetxaero atlasradar").isEmpty(), "the letters without § are just words");
		assertTrue(codes("&f&a&i&r&x&a&e&r&o").isEmpty(), "ampersand codes are for plugins to convert, not for us");
		assertTrue(codes("&3 &6 &3 &6 &3 &6 &e").isEmpty());
		assertTrue(codes("&0&0&1&2&e&f").isEmpty());
		assertTrue(codes("§aGreen §6gold §egold §dpink §3cyan").isEmpty());
		assertTrue(codes("§a§t§l§a§s").isEmpty());
		assertTrue(codes("§f§a§i§r§x§a§e§r").isEmpty(), "one short");
		assertTrue(codes("").isEmpty());
		assertTrue(codes(null).isEmpty());
		assertTrue(codes("§").isEmpty());
		assertTrue(codes("Ünïcödé §İ text without codes").isEmpty());
	}

	@Test
	void lowerCasingKeepsPositions() {
		// 'İ' lower-cases to two chars with String.toLowerCase; positions must not shift
		String text = "İİİ" + FAIR;
		ServerRules.Found f = ServerRules.scan(text).found().get(0);
		assertEquals(3, f.start());
		assertEquals(FAIR, text.substring(f.start(), f.end()));
	}

	@Test
	void repeatedCodesAreAllFound() {
		assertEquals(3, codes(FAIR + "x" + FAIR + FAIR).size());
	}

	// ---------------------------------------------------------------- VoxelMap's settings channel

	static byte[] voxelMapPayload(String json) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0);
		writeUtf(out, json);
		return out.toByteArray();
	}

	@Test
	void voxelMapSettingsPacket() {
		ServerRules rules = new ServerRules(true);
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"radarAllowed\":true,\"radarMobsAllowed\":true,\"radarPlayersAllowed\":true,"
			+ "\"cavesAllowed\":false,\"minimapAllowed\":true,\"worldmapAllowed\":false,\"waypointsAllowed\":false,"
			+ "\"deathWaypointAllowed\":true,\"teleportCommand\":\"tppos\",\"serverIdentity\":\"MyNetwork\"}"));
		Allowed a = rules.allowed();
		assertFalse(a.caveView());
		assertTrue(a.minimap());
		assertFalse(a.worldMap());
		assertFalse(a.waypoints());
		assertFalse(a.radarPlayers(), "VoxelMap's radarAllowed:true is its default, not an opt-in for Atlas");
	}

	@Test
	void voxelMapLaterPacketReplacesKeys() {
		ServerRules rules = new ServerRules(true);
		rules.accept(ATLAS_RADAR);
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"radarAllowed\":false}"));
		assertFalse(rules.allowed().radarPlayers());
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"cavesAllowed\":false}"));
		assertFalse(rules.allowed().radarPlayers(), "a packet without the key leaves it as it was");
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"radarAllowed\":true}"));
		assertTrue(rules.allowed().radarPlayers(), "re-allowed by the server; Atlas's own opt-in applies again");
		assertFalse(rules.allowed().caveView());
	}

	@Test
	void voxelMapSplitRadarSwitches() {
		Restrictions r = ServerRules.parseVoxelMapSettings("{\"radarPlayersAllowed\":false,\"radarMobsAllowed\":true}");
		assertEquals(Say.DENY, r.radarPlayers());
		assertEquals(Say.UNSET, r.radarMobs());
	}

	@Test
	void voxelMapIgnoresNonBooleans() {
		Restrictions r = ServerRules.parseVoxelMapSettings("{\"cavesAllowed\":\"false\",\"minimapAllowed\":null}");
		assertEquals(Restrictions.NONE, r);
	}

	@Test
	void voxelMapMalformedPayloads() {
		ServerRules rules = new ServerRules(true);
		assertThrows(IllegalArgumentException.class, () -> rules.acceptVoxelMapSettings(new byte[0]));
		assertThrows(IllegalArgumentException.class, () -> rules.acceptVoxelMapSettings(new byte[] {0, 50, '{'}));
		assertThrows(IllegalArgumentException.class, () -> rules.acceptVoxelMapSettings(voxelMapPayload("[1,2]")));
		assertThrows(IllegalArgumentException.class, () -> rules.acceptVoxelMapSettings(voxelMapPayload("not json {")));
		assertTrue(rules.allowed().caveView(), "a bad packet changes nothing");
	}

	// ---------------------------------------------------------------- JourneyMap's permission reply

	static byte[] journeyMapPayload(Integer typeByte, boolean op, String json) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (typeByte != null) {
			out.write(typeByte);
		}
		out.write(42);
		out.write(op ? 1 : 0);
		writeUtf(out, json);
		out.write(1);
		return out.toByteArray();
	}

	/** JourneyMap's whole default config, long enough that its length takes two VarInt bytes. */
	static String journeyMapConfig(String radar, String caves, String mobs) {
		return "{\"journeymapEnabled\":\"true\",\"useWorldId\":\"true\",\"viewOnlyServerProperties\":\"true\",\"allowMultiplayerSettings\":\"ALL\","
			+ "\"worldPlayerRadar\":\"ALL\",\"worldPlayerRadarUpdateTime\":\"5\",\"seeUndergroundPlayers\":\"ALL\",\"hideOps\":\"false\","
			+ "\"hideSpectators\":\"false\",\"allowDeathPoints\":\"true\",\"showInGameBeacons\":\"true\",\"allowWaypoints\":\"true\","
			+ "\"allowRightClickTeleport\":\"true\",\"radarLateralDistance\":\"512\",\"radarVerticalDistance\":\"320\",\"teleportEnabled\":\"false\","
			+ "\"surfaceMapping\":\"ALL\",\"topoMapping\":\"ALL\",\"biomeMapping\":\"ALL\",\"caveMapping\":\"" + caves + "\",\"radarEnabled\":\"" + radar + "\","
			+ "\"playerRadarEnabled\":\"true\",\"playerRadarNamesEnabled\":\"true\",\"villagerRadarEnabled\":\"true\",\"animalRadarEnabled\":\"true\","
			+ "\"mobRadarEnabled\":\"" + mobs + "\",\"configVersion\":\"6.0.0\"}";
	}

	@Test
	void journeyMapDefaultsRestrictNothing() {
		Restrictions r = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("ALL", "ALL", "true")));
		assertEquals(Restrictions.NONE, r);
	}

	@Test
	void journeyMapNoneAndOps() {
		Restrictions none = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("NONE", "NONE", "true")));
		assertEquals(Say.DENY, none.radarPlayers());
		assertEquals(Say.DENY, none.radarMobs());
		assertEquals(Say.DENY, none.caveMap());

		String opsOnly = journeyMapConfig("OPS", "OPS", "true");
		Restrictions player = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, false, opsOnly));
		assertEquals(Say.DENY, player.caveMap());
		assertEquals(Say.DENY, player.radarPlayers());
		Restrictions operator = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, true, opsOnly));
		assertEquals(Restrictions.NONE, operator);
	}

	@Test
	void journeyMapMobSwitchesAndWaypoints() {
		Restrictions mobs = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("ALL", "ALL", "false")));
		assertEquals(Say.DENY, mobs.radarMobs());
		assertEquals(Say.UNSET, mobs.radarPlayers());
		Restrictions r = ServerRules.parseJourneyMapConfig("{\"allowWaypoints\":false,\"journeymapEnabled\":\"false\",\"animalRadarEnabled\":\"false\"}", false);
		assertEquals(Say.DENY, r.waypoints());
		assertEquals(Say.DENY, r.minimap());
		assertEquals(Say.DENY, r.worldMap());
		assertEquals(Say.DENY, r.radarMobs());
	}

	@Test
	void journeyMapOlderChannelHasATypeByte() {
		Restrictions r = ServerRules.decodeJourneyMapPermissions(journeyMapPayload(2, false, journeyMapConfig("NONE", "ALL", "true")));
		assertEquals(Say.DENY, r.radarPlayers());
		assertEquals(Say.UNSET, r.caveMap());
	}

	@Test
	void journeyMapReplyReplacesTheLastOne() {
		ServerRules rules = new ServerRules(true);
		rules.acceptJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("ALL", "NONE", "true")));
		assertFalse(rules.allowed().caveView());
		rules.acceptJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("ALL", "ALL", "true")));
		assertTrue(rules.allowed().caveView());
	}

	@Test
	void journeyMapMalformed() {
		assertThrows(IllegalArgumentException.class, () -> ServerRules.decodeJourneyMapPermissions(new byte[0]));
		assertThrows(IllegalArgumentException.class, () -> ServerRules.decodeJourneyMapPermissions(new byte[] {7, 7, 7}));
		assertThrows(IllegalArgumentException.class, () -> ServerRules.decodeJourneyMapPermissions(new byte[] {42, 0}));
		assertThrows(IllegalArgumentException.class, () -> ServerRules.decodeJourneyMapPermissions(new byte[] {42, 0, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80}));
		assertThrows(IllegalArgumentException.class, () -> ServerRules.decodeJourneyMapPermissions(journeyMapPayload(null, false, "oops")));
	}

	@Test
	void allSourcesTogether() {
		ServerRules rules = new ServerRules(true);
		rules.accept(ATLAS_RADAR);
		rules.acceptVoxelMapSettings(voxelMapPayload("{\"waypointsAllowed\":false}"));
		rules.acceptJourneyMapPermissions(journeyMapPayload(null, false, journeyMapConfig("ALL", "NONE", "true")));
		Restrictions r = rules.restrictions();
		assertEquals(Say.ALLOW, r.radarPlayers());
		assertEquals(Say.DENY, r.waypoints());
		assertEquals(Say.DENY, r.caveMap());
		rules.clear();
		assertEquals(Restrictions.NONE, rules.restrictions());
	}

	@Test
	void sayIsMostRestrictive() {
		assertEquals(Say.DENY, Say.ALLOW.and(Say.DENY));
		assertEquals(Say.DENY, Say.DENY.and(Say.UNSET));
		assertEquals(Say.ALLOW, Say.UNSET.and(Say.ALLOW));
		assertEquals(Say.UNSET, Say.UNSET.and(Say.UNSET));
	}

	private static void writeUtf(ByteArrayOutputStream out, String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		int v = bytes.length;
		while ((v & ~0x7F) != 0) {
			out.write(v & 0x7F | 0x80);
			v >>>= 7;
		}
		out.write(v);
		out.writeBytes(bytes);
	}
}
