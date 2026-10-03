package dev.voidmc.hearth.economy.bank;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the {@link Deposits} ledger lives: the overworld's Hearth world data ({@code hearth:economy/deposits}, WORLD
 * scope), so every bank in the world shares it.
 *
 * <p>Until the sim module provides world data (or if it never does, e.g. a world without the sim), the ledger lives in
 * a Fabric attachment on the overworld; once world data appears, it is moved there on first use.
 */
public final class DepositStore {
	public static final HearthDataKey<Deposits> KEY = HearthDataKey.register(Hearth.id("economy/deposits"), HearthDataKey.Scope.WORLD,
		Deposits.CODEC, Deposits::new);
	private static final AttachmentType<Deposits> FALLBACK = AttachmentRegistry.createPersistent(Hearth.id("economy/deposits"), Deposits.CODEC);

	private DepositStore() {
	}

	public static void init() {
		// class load registers the key and the attachment
	}

	public static long balance(MinecraftServer server, UUID player) {
		return ledger(server).balance(player);
	}

	/** Adds (or with a negative delta takes) bits; false and no change if that would go below zero. */
	public static boolean add(MinecraftServer server, UUID player, long delta) {
		Deposits d = ledger(server);
		if (!d.add(player, delta)) {
			return false;
		}
		save(server, d);
		return true;
	}

	public static Map<UUID, Long> all(MinecraftServer server) {
		return ledger(server).accounts();
	}

	private static Deposits ledger(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		HearthWorld world = HearthWorld.of(overworld);
		Deposits fallback = overworld.getAttached(FALLBACK);
		if (world == null) {
			return fallback != null ? fallback.copy() : new Deposits();
		}
		Deposits d = world.data(KEY);
		if (fallback != null) {
			fallback.balances.forEach((k, v) -> d.balances.merge(k, v, Long::sum));
			overworld.removeAttached(FALLBACK);
			world.markDirty();
		}
		return d;
	}

	private static void save(MinecraftServer server, Deposits d) {
		ServerLevel overworld = server.overworld();
		HearthWorld world = HearthWorld.of(overworld);
		if (world != null) {
			world.markDirty();
		} else {
			// a fresh value each time, so the attachment is marked changed and saved with the level
			overworld.setAttached(FALLBACK, d.copy());
		}
	}
}
