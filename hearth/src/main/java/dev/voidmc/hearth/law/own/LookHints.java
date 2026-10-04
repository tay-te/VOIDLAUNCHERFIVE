package dev.voidmc.hearth.law.own;

import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.law.crime.Presence;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.net.LawPayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * KCD's red hand: when a player in a settlement looks at something that belongs to someone (a chest, a door, a bed, a
 * crop, a penned animal), the HUD says whose it is, and for locks how good and whether they hold the key. A ray a few
 * times a second for players in towns, sent only when it changes.
 */
public final class LookHints {
	private static final double REACH = 5.0;
	private static final Map<UUID, LawPayloads.Hint> SENT = new HashMap<>();

	private LookHints() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LawPayloads.Hint hint = Presence.of(player) == null ? LawPayloads.Hint.NONE : compute(player);
			LawPayloads.Hint before = SENT.get(player.getUUID());
			if (!hint.equals(before) && (before != null || hint != LawPayloads.Hint.NONE)) {
				SENT.put(player.getUUID(), hint);
				if (ServerPlayNetworking.canSend(player, LawPayloads.Hint.TYPE)) {
					ServerPlayNetworking.send(player, hint);
				}
			}
		}
	}

	static LawPayloads.Hint compute(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(REACH));
		HitResult block = player.pick(REACH, 1.0F, false);
		double blockDist = block.getType() == HitResult.Type.MISS ? REACH * REACH : block.getLocation().distanceToSqr(eye);
		AABB area = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, area, e -> e instanceof Animal && e.isAlive(), blockDist);
		if (entity != null) {
			Owner owner = Ownership.owner(entity.getEntity());
			if (owner == null) {
				return LawPayloads.Hint.NONE;
			}
			return new LawPayloads.Hint(Ownership.name(owner, level.getServer()), true, 0, false, false, Ownership.isOwner(owner, player.getUUID()));
		}
		if (!(block instanceof BlockHitResult hit) || block.getType() != HitResult.Type.BLOCK) {
			return LawPayloads.Hint.NONE;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!interesting(level, pos, state)) {
			return LawPayloads.Hint.NONE;
		}
		Locks.Lock lock = Locks.at(level, pos, state);
		if (lock != null) {
			boolean key = Locks.hasKey(player, lock);
			return new LawPayloads.Hint(Ownership.name(lock.owner(), level.getServer()), false, lock.tier(), lock.locked(), key, false);
		}
		Owner owner = Ownership.owner(level, pos);
		if (owner == null) {
			return LawPayloads.Hint.NONE;
		}
		Component name = Ownership.name(owner, level.getServer());
		return new LawPayloads.Hint(name, false, 0, false, false, Ownership.isOwner(owner, player.getUUID()));
	}

	/** Things worth naming the owner of: what a thief would take or open. */
	static boolean interesting(ServerLevel level, BlockPos pos, BlockState state) {
		if (Locks.lockable(state) || state.getBlock() instanceof BedBlock || state.getBlock() instanceof CropBlock
			|| state.getBlock() instanceof StemBlock || state.getBlock() instanceof SweetBerryBushBlock || state.getBlock() instanceof CocoaBlock
			|| state.getBlock() instanceof NetherWartBlock) {
			return true;
		}
		return state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container;
	}

	public static void forget(UUID player) {
		SENT.remove(player);
	}

	public static void clear() {
		SENT.clear();
	}
}
