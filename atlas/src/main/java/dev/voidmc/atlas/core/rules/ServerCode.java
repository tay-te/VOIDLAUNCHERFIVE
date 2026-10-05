package dev.voidmc.atlas.core.rules;

import dev.voidmc.atlas.core.rules.Restrictions.Say;

/**
 * The fixed text codes servers put in messages to restrict (or allow) map mods. Each is a run of formatting codes
 * ({@code §} and a character), which the game does not draw, so players never see them. Sources for each are in
 * {@code docs/SERVER_RULES.md}.
 *
 * <p>Rei's Minimap's keyword ({@code §0§0}, then digits, then {@code §e§f}) is not a fixed string, so it is matched
 * separately by {@link ServerRules}; it is listed here as {@link #REI_KEYWORD} with no text.
 */
public enum ServerCode {
	/** Xaero's Minimap and World Map "fair-play": no cave mode (on either map) and no entity radar. */
	XAERO_FAIR_PLAY("§f§a§i§r§x§a§e§r§o", "Xaero's Minimap", false,
		Restrictions.NONE.withCaveMap(Say.DENY).withRadarPlayers(Say.DENY).withRadarMobs(Say.DENY)),
	/** Xaero's Minimap: cave mode works in the Nether despite fair-play. */
	XAERO_MINIMAP_NETHER_CAVES("§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r", "Xaero's Minimap", false,
		Restrictions.NONE.withNetherCaveMinimap(Say.ALLOW)),
	/** Xaero's World Map: cave mode works in the Nether despite fair-play. */
	XAERO_WORLD_MAP_NETHER_CAVES("§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r", "Xaero's World Map", false,
		Restrictions.NONE.withNetherCaveWorldMap(Say.ALLOW)),
	/** Xaero's Minimap: no minimap at all. */
	XAERO_NO_MINIMAP("§n§o§m§i§n§i§m§a§p", "Xaero's Minimap", false,
		Restrictions.NONE.withMinimap(Say.DENY)),
	/** Xaero's Minimap and World Map: forget every flag set by codes so far. */
	XAERO_RESET("§r§e§s§e§t§x§a§e§r§o", "Xaero's Minimap", true, Restrictions.NONE),
	/** VoxelMap (and Zan's Minimap before it): no radar. Spaces are part of the code. */
	VOXELMAP_NO_RADAR("§3 §6 §3 §6 §3 §6 §e", "VoxelMap", false,
		Restrictions.NONE.withRadarPlayers(Say.DENY).withRadarMobs(Say.DENY)),
	/** VoxelMap (and Zan's Minimap before it): no cave mode. Spaces are part of the code. */
	VOXELMAP_NO_CAVES("§3 §6 §3 §6 §3 §6 §d", "VoxelMap", false,
		Restrictions.NONE.withCaveMap(Say.DENY)),
	/** Atlas's own opt-in: the radar may show players and mobs. ("atlasradar"; ends in §r, so text after it is unstyled.) */
	ATLAS_RADAR("§a§t§l§a§s§r§a§d§a§r", "VOID Atlas", false,
		Restrictions.NONE.withRadarPlayers(Say.ALLOW).withRadarMobs(Say.ALLOW)),
	/** Atlas's own opt-in: the radar may show mobs, not players. ("atlasmobradar") */
	ATLAS_MOB_RADAR("§a§t§l§a§s§m§o§b§r§a§d§a§r", "VOID Atlas", false,
		Restrictions.NONE.withRadarMobs(Say.ALLOW)),
	/** Rei's Minimap keyword: {@code §0§0}, digits 1-7 for what to enable, {@code §e§f}. Matched by pattern. */
	REI_KEYWORD(null, "Rei's Minimap", false, Restrictions.NONE);

	private final String text;
	private final String mod;
	private final boolean reset;
	private final Restrictions effect;

	ServerCode(String text, String mod, boolean reset, Restrictions effect) {
		this.text = text;
		this.mod = mod;
		this.reset = reset;
		this.effect = effect;
	}

	/** The exact code, or null for {@link #REI_KEYWORD}. */
	public String text() {
		return this.text;
	}

	/** The map mod whose convention it is. */
	public String mod() {
		return this.mod;
	}

	/** Whether it clears what earlier codes set. */
	public boolean reset() {
		return this.reset;
	}

	/** What it says (for {@link #REI_KEYWORD}, depends on the digits: see {@link ServerRules.Found#effect()}). */
	public Restrictions effect() {
		return this.effect;
	}
}
