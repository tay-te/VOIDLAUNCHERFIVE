package dev.voidmc.hearth.economy.bank;

import com.mojang.serialization.Codec;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Coin players keep on deposit. One ledger for the whole world (kept with the overworld's Hearth data), so a deposit made
 * at one settlement's bank is honoured at every other's — banks are a network, the way the roads are.
 *
 * <p>Until the sim module provides world data (or if it never does, e.g. in a test world), the ledger lives in a Fabric
 * attachment on the overworld; when world data appears, it is moved there on first use.
 */
public final class Deposits {
	public static final Codec<Deposits> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).xmap(Deposits::new, d -> Map.copyOf(d.balances));

	public static final HearthDataKey<Deposits> KEY = HearthDataKey.register(Hearth.id("economy/deposits"), HearthDataKey.Scope.WORLD, CODEC,
		Deposits::new);
	private static final AttachmentType<Deposits> FALLBACK = AttachmentRegistry.createPersistent(Hearth.id("economy/deposits"), CODEC);

	private final Map<UUID, Long> balances;

	public Deposits() {
		this(Map.of());
	}

	private Deposits(Map<UUID, Long> balances) {
		this.balances = new HashMap<>(balances);
	}

	public static void init() {
		// class load registers the key and the attachment
	}

	public static long balance(MinecraftServer server, UUID player) {
		return ledger(server).balances.getOrDefault(player, 0L);
	}

	/** Adds (or with a negative delta takes) bits; returns false and changes nothing if that would go below zero. */
	public static boolean add(MinecraftServer server, UUID player, long delta) {
		Deposits d = ledger(server);
		long now = d.balances.getOrDefault(player, 0L);
		long next = now + delta;
		if (next < 0) {
			return false;
		}
		if (next == 0) {
			d.balances.remove(player);
		} else {
			d.balances.put(player, next);
		}
		save(server, d);
		return true;
	}

	/** Every account, for admin commands. */
	public static Map<UUID, Long> all(MinecraftServer server) {
		return Map.copyOf(ledger(server).balances);
	}

	private static Deposits ledger(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		HearthWorld world = HearthWorld.of(overworld);
		Deposits fallback = overworld.getAttached(FALLBACK);
		if (world == null) {
			return fallback != null ? fallback : new Deposits();
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
			// A fresh copy each time, so the attachment is marked changed and saved with the level.
			overworld.setAttached(FALLBACK, new Deposits(d.balances));
		}
	}
}
