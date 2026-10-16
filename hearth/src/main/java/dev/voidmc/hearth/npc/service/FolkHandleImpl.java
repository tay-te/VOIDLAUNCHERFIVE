package dev.voidmc.hearth.npc.service;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One embodied folk as other modules drive it ({@link NpcService.FolkHandle}). Made once per brain and valid while the
 * entity lives. Movement asks for a path at most once per repath cooldown for the same target, so a behaviour may call
 * {@link #moveTo} every tick.
 */
public final class FolkHandleImpl implements NpcService.FolkHandle {
	/** Blocks within which players hear a folk speak. */
	private static final double SPEECH_RANGE = 16.0;

	private final FolkBrain brain;
	private @Nullable BlockPos target;
	private int nextRepath;
	private boolean pathFound;
	/** Where the folk stood still since {@link #stillSince} while walking to {@link #target}. */
	private @Nullable Vec3 stillAt;
	private int stillSince;

	public FolkHandleImpl(FolkBrain brain) {
		this.brain = brain;
	}

	@Override
	public Resident resident() {
		return this.brain.resident;
	}

	@Override
	public Settlement settlement() {
		return this.brain.settlement;
	}

	@Override
	public PathfinderMob entity() {
		return this.brain.folk;
	}

	public Folk folk() {
		return this.brain.folk;
	}

	@Override
	public boolean awake() {
		Folk f = this.brain.folk;
		return f.isAlive() && !f.isRemoved() && !f.isFolded() && !f.isSleeping();
	}

	@Override
	public void wake() {
		this.brain.wake(20 * 120);
	}

	@Override
	public boolean canSee(Entity target) {
		Folk f = this.brain.folk;
		if (!awake() || target.level() != f.level() || target == f || target.isRemoved()) {
			return false;
		}
		NpcConfig.Perception p = NpcModule.config().perception();
		Vec3 eye = f.getEyePosition();
		Vec3 at = target.getEyePosition();
		if (!Perception.inSight(f.getYHeadRot(), f.getXRot(), at.x - eye.x, at.y - eye.y, at.z - eye.z, p.sightRange(), p.fieldOfView(), p.nearSense())) {
			return false;
		}
		// Vanilla sensing caches line of sight per tick, so several modules asking about one target cost one ray.
		return f.getSensing().hasLineOfSight(target);
	}

	@Override
	public boolean moveTo(BlockPos target, double speed) {
		Folk f = this.brain.folk;
		int now = f.tickCount;
		if (target.equals(this.target)) {
			// Stuck on the way for ten seconds (a partial path's end, a stair the pathfinder will not take): if nobody
			// sees the folk or where it is going, it is there, as the routine's own walks do.
			if (this.stillAt == null || f.position().distanceToSqr(this.stillAt) > 1.0) {
				this.stillAt = f.position();
				this.stillSince = now;
			} else if (now - this.stillSince > 200 && !arrived() && f.level() instanceof ServerLevel level && this.brain.unseen(level, target)) {
				BlockPos spot = dev.voidmc.hearth.npc.entity.FolkSpots.standableNear(level, target, 3);
				if (spot != null) {
					f.getNavigation().stop();
					f.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					this.stillAt = f.position();
					this.stillSince = now;
					return true;
				}
			}
			if (now < this.nextRepath || !f.getNavigation().isDone()) {
				// Same goal, still walking or asked again too soon: do not hammer the pathfinder.
				return this.pathFound;
			}
		} else {
			this.stillAt = f.position();
			this.stillSince = now;
		}
		this.target = target.immutable();
		this.nextRepath = now + FolkBrain.config().repathCooldown();
		java.util.List<BlockPos> spots = f.level() instanceof net.minecraft.world.level.Level level ? dev.voidmc.hearth.npc.entity.FolkSpots.approaches(level, target)
			: java.util.List.of();
		// the search budget follows the distance, as the routine's own walks do (FolkBrain.walk)
		double far = Math.sqrt(target.distSqr(f.blockPosition())) * 1.4 + 16.0 + (Math.abs(target.getY() - f.getBlockY()) >= 3 ? 48.0 : 0.0);
		f.getNavigation().setRequiredPathLength((float) Math.min(far, 128.0));
		Path path = spots.isEmpty() ? f.getNavigation().createPath(target, 1) : f.getNavigation().createPath(new java.util.HashSet<>(spots), 0);
		this.pathFound = path != null && f.getNavigation().moveTo(path, speed);
		return this.pathFound || arrived();
	}

	@Override
	public boolean arrived() {
		BlockPos t = this.target;
		if (t == null) {
			return true;
		}
		Vec3 pos = this.brain.folk.position();
		double dx = t.getX() + 0.5 - pos.x;
		double dz = t.getZ() + 0.5 - pos.z;
		double near = this.brain.folk.getNavigation().isDone() ? 2.5 : 1.5;
		return dx * dx + dz * dz <= near * near && Math.abs(t.getY() - pos.y) < 2.0;
	}

	@Override
	public void stop() {
		this.brain.folk.getNavigation().stop();
		this.target = null;
	}

	@Override
	public void lookAt(Entity target) {
		this.brain.folk.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	@Override
	public void lookAt(BlockPos target) {
		this.brain.faceTowards(target);
	}

	@Override
	public void say(Component line, @Nullable ServerPlayer to) {
		Folk f = this.brain.folk;
		Component msg = Component.translatable("hearth.npc.say", Component.literal(this.brain.resident.fullName()).withStyle(ChatFormatting.GOLD), line);
		if (to != null) {
			to.sendSystemMessage(msg);
		} else if (f.level() instanceof ServerLevel level) {
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(f) <= SPEECH_RANGE * SPEECH_RANGE) {
					p.sendSystemMessage(msg);
				}
			}
		}
		this.brain.pose(FolkPose.TALK, 40);
	}

	@Override
	public void hold(ItemStack stack) {
		Folk f = this.brain.folk;
		if (!ItemStack.isSameItemSameComponents(f.getItemBySlot(EquipmentSlot.MAINHAND), stack)) {
			f.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
		}
	}

	@Override
	public void swing() {
		this.brain.swing();
	}

	@Override
	public long gameTime() {
		return this.brain.folk.level().getGameTime();
	}
}
