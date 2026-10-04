package dev.voidmc.hearth.repair.command;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.RepairModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A stand-in for the NPC module, for the self-test only: a husk with its own goals removed plays a resident, a
 * {@link FolkHandle} over vanilla navigation drives it, and a scheduler asks the repair behaviours, by priority, the way
 * {@link dev.voidmc.hearth.api.NpcService} describes (wants about once a second, the highest that wants runs until it is
 * done or a higher one wants the folk). It lets the behaviours be watched walking, climbing and placing with no NPC
 * module in the worktree; the NPC module's folk are what run in a real game.
 */
final class DevFolk implements FolkHandle {
	private final Resident resident;
	private final Settlement settlement;
	private final PathfinderMob mob;
	private @Nullable FolkBehaviour running;
	final List<String> said = new ArrayList<>();
	int ticks;

	private DevFolk(Resident resident, Settlement settlement, PathfinderMob mob) {
		this.resident = resident;
		this.settlement = settlement;
		this.mob = mob;
	}

	/** A stand-in folk of a trade at a spot (a resident record of its own, never added to the settlement). */
	static @Nullable DevFolk spawn(ServerLevel level, Settlement s, JobType job, BlockPos at, String name) {
		Husk husk = EntityTypes.HUSK.spawn(level, at, EntitySpawnReason.COMMAND);
		if (husk == null) {
			return null;
		}
		husk.removeAllGoals(g -> true);
		husk.setPersistenceRequired();
		husk.setPermanentlyInvulnerable(true);
		husk.setSilent(true);
		husk.setCustomName(Component.literal(name));
		Resident r = new Resident(UUID.nameUUIDFromBytes(("repair-test-" + name).getBytes()), name, "Test", false, AgeStage.ADULT, -100, job, Resident.NO_PLOT,
			Resident.NO_PLOT, 0, new CompoundTag());
		return new DevFolk(r, s, husk);
	}

	/** One tick of the NPC module's scheduling over the given behaviours. */
	void tick(List<FolkBehaviour> behaviours) {
		this.ticks++;
		if (this.ticks % 20 == 0) {
			FolkBehaviour best = null;
			for (FolkBehaviour b : behaviours.stream().sorted(Comparator.comparingInt(FolkBehaviour::priority).reversed()).toList()) {
				if (this.running != null && b.priority() <= this.running.priority()) {
					break;
				}
				if (b.wants(this)) {
					best = b;
					break;
				}
			}
			if (best != null && best != this.running) {
				if (this.running != null) {
					this.running.stop(this);
				}
				this.running = best;
				best.start(this);
			}
		}
		if (this.running != null && !this.running.tick(this)) {
			this.running.stop(this);
			this.running = null;
		}
	}

	@Nullable FolkBehaviour running() {
		return this.running;
	}

	void discard() {
		if (this.running != null) {
			this.running.stop(this);
			this.running = null;
		}
		this.mob.discard();
	}

	@Override
	public Resident resident() {
		return this.resident;
	}

	@Override
	public Settlement settlement() {
		return this.settlement;
	}

	@Override
	public PathfinderMob entity() {
		return this.mob;
	}

	@Override
	public boolean awake() {
		return true;
	}

	@Override
	public boolean canSee(Entity target) {
		return this.mob.distanceToSqr(target) < 32 * 32 && this.mob.hasLineOfSight(target);
	}

	@Override
	public boolean moveTo(BlockPos target, double speed) {
		return this.mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
	}

	@Override
	public boolean arrived() {
		return this.mob.getNavigation().isDone();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}

	@Override
	public void lookAt(Entity target) {
		this.mob.getLookControl().setLookAt(target);
	}

	@Override
	public void lookAt(BlockPos target) {
		this.mob.getLookControl().setLookAt(Vec3.atCenterOf(target));
	}

	@Override
	public void say(Component line, @Nullable ServerPlayer to) {
		String text = line.getString();
		this.said.add(text);
		RepairModule.LOG.info("[hearth repair] {} says: {}", this.resident.givenName, text);
	}

	@Override
	public void hold(ItemStack stack) {
		this.mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
	}

	@Override
	public void swing() {
		this.mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	@Override
	public long gameTime() {
		return this.mob.level().getGameTime();
	}
}
