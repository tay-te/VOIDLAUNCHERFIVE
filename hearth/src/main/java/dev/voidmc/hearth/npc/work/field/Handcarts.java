package dev.voidmc.hearth.npc.work.field;

import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.npc.travel.cart.Carts;
import dev.voidmc.hearth.sim.LoadCaps;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;

/**
 * Haulers' handcarts on the town's streets (docs/LIVING_WORLD_2.md §6.1): lane A1's {@code hearth:cart} entity, a
 * handcart drawn by the hauler, its bed showing the load (logs from the logging camp to the sawmill, stone to the mason,
 * sacks, barrels). A view of a haul the sim has already made: never saved (the entity type is {@code noSave}, and it is
 * tagged {@link #TAG}), at most {@code carts.max_per_town} a town and {@code carts.max_per_server} in all, both shrinking
 * as the server slows ({@link LoadCaps}). A cart whose hauler is gone (folded, dead, the haul given up) is taken away by
 * the sweep; the hauler parks it by the door it is fetching from or bringing to, so it never rolls into a building.
 */
public final class Handcarts {
	public static final String TAG = "hearth.handcart";
	private static final int SWEEP_TICKS = 20;

	/** A live handcart: the town it hauls for and the hauler that draws it. */
	private record Live(long settlementId, UUID hauler, ServerLevel level) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static long ticks;

	private Handcarts() {
	}

	/** Handcarts out now, server-wide. */
	public static int count() {
		return LIVE.size();
	}

	/** Handcarts out now for one town. */
	public static int count(long settlementId) {
		int n = 0;
		for (Live l : LIVE.values()) {
			if (l.settlementId() == settlementId) {
				n++;
			}
		}
		return n;
	}

	/** Whether a haul this long gets a cart (short ones are carried in the arms, as before). */
	public static boolean worthACart(double haulLength) {
		FieldWorkConfig.Carts cfg = FieldWork.config().carts();
		return cfg.enabled() && haulLength >= cfg.minHaul();
	}

	/** Whether one more may come out for this town at this tick time. */
	static boolean admits(long settlementId, int townCount, int serverCount, double mspt, double highMspt, FieldWorkConfig.Carts cfg) {
		if (!cfg.enabled() || !LoadCaps.admitting(mspt, highMspt)) {
			return false;
		}
		return townCount < LoadCaps.cap(cfg.maxPerTown(), 0, mspt, highMspt) && serverCount < LoadCaps.cap(cfg.maxPerServer(), 0, mspt, highMspt);
	}

	/** A handcart hitched behind {@code hauler}, empty; null when the caps say no. */
	public static @Nullable CartEntity take(ServerLevel level, long settlementId, Entity hauler) {
		FieldWorkConfig.Carts cfg = FieldWork.config().carts();
		MinecraftServer server = level.getServer();
		if (!admits(settlementId, count(settlementId), count(), LoadCaps.mspt(server), LoadCaps.highMspt(), cfg)) {
			return null;
		}
		CartEntity cart = Carts.CART.create(level, EntitySpawnReason.EVENT);
		if (cart == null) {
			return null;
		}
		cart.setup(CartEntity.Kind.HANDCART, CartEntity.Load.NONE);
		cart.addTag(TAG);
		float yaw = hauler.getYRot() * Mth.DEG_TO_RAD;
		double hitch = CartEntity.Kind.HANDCART.hitch;
		cart.snapTo(hauler.getX() + Math.sin(yaw) * hitch, hauler.getY(), hauler.getZ() - Math.cos(yaw) * hitch, hauler.getYRot(), 0F);
		cart.hitchTo(hauler);
		if (!level.addFreshEntity(cart)) {
			return null;
		}
		LIVE.put(cart.getUUID(), new Live(settlementId, hauler.getUUID(), level));
		return cart;
	}

	/** The haul is over: the cart goes. */
	public static void release(@Nullable CartEntity cart) {
		if (cart == null) {
			return;
		}
		LIVE.remove(cart.getUUID());
		if (!cart.isRemoved()) {
			cart.discard();
		}
	}

	/** From the field work tick: carts whose hauler is gone are taken away; above {@code high_mspt} all of them are. */
	static void tick(MinecraftServer server) {
		if (++ticks % SWEEP_TICKS != 0 || LIVE.isEmpty()) {
			return;
		}
		boolean shed = LoadCaps.shedding(LoadCaps.mspt(server), LoadCaps.highMspt());
		Iterator<Map.Entry<UUID, Live>> it = LIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Live> e = it.next();
			Live l = e.getValue();
			Entity cart = l.level().getEntity(e.getKey());
			Entity hauler = l.level().getEntity(l.hauler());
			boolean orphan = cart == null || !cart.isAlive() || hauler == null || !hauler.isAlive()
				|| cart instanceof CartEntity c && c.puller() == null && cart.distanceToSqr(hauler) > 32 * 32;
			if (orphan || shed) {
				if (cart != null && !cart.isRemoved()) {
					cart.discard();
				}
				it.remove();
			}
		}
	}

	static void clear() {
		LIVE.clear();
	}
}
