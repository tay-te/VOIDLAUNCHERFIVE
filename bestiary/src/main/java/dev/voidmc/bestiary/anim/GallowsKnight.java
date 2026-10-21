package dev.voidmc.bestiary.anim;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.fray.motion.MotionServer;
import java.util.EnumSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Gallows Knight (BESTIARY.md §8.3), stage 1A: the first custom rig (real elbows, knees, spine, a chain with a
 * hook), drawn by Fray's {@code RigModel}, fighting with Fray timelines. Its AI here is a placeholder so the rig can be
 * seen walking and swinging (approach, then the telegraphed chain sweep when in reach); the Bestiary AI lane replaces it
 * with the boss framework and both phases.
 */
public class GallowsKnight extends Monster {
	public static final Identifier CHAIN_SWEEP = Bestiary.id("gallows_knight/chain_sweep");
	public static final Identifier STAGGER = Bestiary.id("gallows_knight/stagger");

	public GallowsKnight(EntityType<? extends GallowsKnight> type, Level level) {
		super(type, level);
		this.xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 900)
			.add(Attributes.ARMOR, 12)
			.add(Attributes.ATTACK_DAMAGE, 10)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.FOLLOW_RANGE, 24)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new ChainSweepGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Busy while a Fray action runs: no turning, no walking (the swing keeps the facing it started with). */
	public boolean acting() {
		return MotionServer.running(this) != null;
	}

	/** Placeholder melee: walk to the target, then the chain sweep when it's within reach, every 3 seconds at most. */
	static final class ChainSweepGoal extends Goal {
		private final GallowsKnight knight;
		private int cooldown;

		ChainSweepGoal(GallowsKnight knight) {
			this.knight = knight;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = this.knight.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.knight.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity t = this.knight.getTarget();
			if (t == null) {
				return;
			}
			if (this.cooldown > 0) {
				this.cooldown--;
			}
			if (this.knight.acting()) {
				this.knight.getNavigation().stop();
				return;
			}
			this.knight.getLookControl().setLookAt(t, 30, 30);
			double d = this.knight.distanceToSqr(t);
			if (d < 2.9 * 2.9 && this.cooldown <= 0 && this.knight.hasLineOfSight(t)) {
				this.knight.getNavigation().stop();
				// Face the target before the tell starts: the sweep keeps this facing to the end.
				this.knight.setYRot((float) Math.toDegrees(Math.atan2(t.getZ() - this.knight.getZ(), t.getX() - this.knight.getX())) - 90);
				this.knight.yBodyRot = this.knight.getYRot();
				this.knight.yHeadRot = this.knight.getYRot();
				MotionServer.play(this.knight, CHAIN_SWEEP, 1);
				this.cooldown = 60;
			} else if (d >= 2.2 * 2.2) {
				this.knight.getNavigation().moveTo(t, 1.0);
			} else {
				this.knight.getNavigation().stop();
			}
		}
	}
}
