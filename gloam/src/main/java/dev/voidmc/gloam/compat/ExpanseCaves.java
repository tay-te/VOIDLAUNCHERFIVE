package dev.voidmc.gloam.compat;

import dev.voidmc.gloam.Gloam;
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
 * been dug, and Expanse's classes are only touched when it is loaded.
 *
 * <p>The data is the server's: in singleplayer Gloam asks the integrated server's overworld. Elsewhere there is none
 * ({@link #sampler} is null) and {@code Caves} reads the blocks instead. The U tile of the pack bridge (M5) should ask
 * through this class too, so the sound and the fog share one cache of the sampler.
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
