package dev.voidmc.fray.client.combat.hud;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Line of sight for nameplates, cached per entity for a few ticks (a raycast per creature per frame costs more than
 * the whole HUD).
 */
final class Sight {
	/** entity id → (game tick << 1) | visible. */
	private static final Int2LongOpenHashMap CACHE = new Int2LongOpenHashMap();

	private Sight() {
	}

	static boolean visible(Minecraft mc, Entity e) {
		long now = mc.level.getGameTime();
		long v = CACHE.getOrDefault(e.getId(), Long.MIN_VALUE);
		if (v != Long.MIN_VALUE && now - (v >> 1) < 4) {
			return (v & 1) != 0;
		}
		boolean seen = mc.player.hasLineOfSight(e);
		CACHE.put(e.getId(), now << 1 | (seen ? 1 : 0));
		if (CACHE.size() > 512) {
			CACHE.clear();
		}
		return seen;
	}
}
