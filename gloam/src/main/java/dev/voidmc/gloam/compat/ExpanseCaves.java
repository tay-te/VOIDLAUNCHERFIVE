package dev.voidmc.gloam.compat;

import dev.voidmc.gloam.Gloam;
import dev.voidmc.gloam.bridge.UndergroundTile;
import dev.voidmc.gloam.sound.CaveKinds;
import dev.voidmc.gloam.sound.CaveProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.Level;

/**
 * What Expanse knows about the spaces under the land ({@code ExpanseUnderground}): how open a point is, its floor and
 * roof, the cavern water nearest it, its realm, and whether glowworms hang, bats roost, the roof drips or a great mine
 * runs there. Like {@link Expanse}'s countries it comes from the seed, not the blocks, so it is the same whatever has
 * been dug, and Expanse's classes are only touched when it is loaded. Safe from any thread.
 *
 * <p>The data is the server's: in singleplayer Gloam asks the integrated server's overworld. Elsewhere there is none
 * ({@link #sampler} is null) and {@code Caves} and {@code ClientCaves} read the blocks instead.
 *
 * <p>One class for both users (wave-2 cross-check): the soundscape's {@code Caves} asks {@link #sampler} and the
 * bridge's U tile asks {@link #source}, which adapts the same sampler, so the sound and the fog share one cache and
 * the same {@link CaveKinds} ids (the U tile's water nibble and {@code void_underground.zw}). This file supersedes the
 * gloam_sound module's copy, which has only {@link #sampler}; integrate gloam_sound first.
 */
public final class ExpanseCaves {
	/** Asks about one level's underground; safe from any thread. Each call walks to the floor and the roof, so it
	 * costs more in a great cavern than in rock: ask a point a tick, not a ring at once. */
	@FunctionalInterface
	public interface Sampler {
		CaveProbe at(int x, int y, int z);
	}

	private static IntegratedServer server;
	private static Sampler sampler;
	private static Sampler sourceOf;
	private static UndergroundTile.CaveSource source;
	private static boolean broken;

	private ExpanseCaves() {
	}

	/** The sampler for the underground {@code level} shows, or null (see the class comment). Cheap after the first call. */
	public static Sampler sampler(ClientLevel level) {
		if (!Expanse.LOADED || broken || level == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		IntegratedServer s = Minecraft.getInstance().getSingleplayerServer();
		if (s == null) {
			server = null;
			sampler = null;
			return null;
		}
		if (s != server) {
			server = s;
			try {
				sampler = Api.sampler(s.overworld());
			} catch (LinkageError | RuntimeException e) {
				broken = true;
				sampler = null;
				Gloam.LOGGER.warn("VOID Gloam: Expanse's underground API is not what Gloam expects; the caves are read from the blocks", e);
			}
		}
		return sampler;
	}

	/** The same caves in the U tile's terms ({@link UndergroundTile.CaveSource}), or null. Client thread; the source it
	 * returns is safe from any thread. */
	public static UndergroundTile.CaveSource source(ClientLevel level) {
		Sampler s = sampler(level);
		if (s == null) {
			sourceOf = null;
			source = null;
			return null;
		}
		if (s != sourceOf) {
			sourceOf = s;
			source = (x, y, z, out) -> {
				CaveProbe p = s.at(x, y, z);
				out.space = p.space();
				out.floor = p.floor();
				out.ceiling = p.ceiling();
				out.water = p.water();            // CaveKinds ids, which TileCodec's WATER_* now equal
				out.waterDistance = p.waterDistance();
				out.realm = p.realm();            // CaveKinds ids, the same as TileCodec.REALMS
				out.greatRiver = p.greatRiver();
				out.glowworms = p.glowworms();
				out.bats = p.bats();
				out.drip = p.drip();
				return true;
			};
		}
		return source;
	}

	/** Expanse's side, in a class of its own, so the JVM only resolves it once Expanse is known to be there. */
	private static final class Api {
		static Sampler sampler(net.minecraft.server.level.ServerLevel level) {
			dev.voidmc.expanse.api.ExpanseUnderground.Sampler s = level == null ? null : dev.voidmc.expanse.api.ExpanseUnderground.sampler(level);
			if (s == null) {
				return null;
			}
			return (x, y, z) -> {
				dev.voidmc.expanse.api.ExpanseUnderground.Here h = s.at(x, y, z);
				return new CaveProbe(h.underground(), h.space(), h.floor(), h.ceiling(), CaveKinds.water(h.water()), h.waterSurface(),
					h.waterDistance(), CaveKinds.realm(h.realm()), h.greatRiver(), h.glowworms(), h.bats(), h.drip(), h.mine());
			};
		}
	}
}
