package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Herders with the town's flock (docs/LIVING_WORLD.md §7.4): shearing the sheep that have grown their wool back, milking
 * the cows, driving strays back into the pasture, and walking among the herd with a crook and a fistful of wheat. Only
 * animals in and around the herder's own pasture are touched, never one that is leashed, named or tamed (someone's
 * pet). Shearing and milking drop nothing: the wool, milk and meat are the sim's to credit from the herder's working
 * time, as they are while nobody watches.
 */
final class HerderWork implements WorkBehaviour {
	/** How far beyond the pasture a stray is still the herder's to fetch. */
	private static final int STRAY_RANGE = 28;
	private int trips;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		this.trips++;
		AABB pasture = null;
		// a pasture with a far meadow: the flock is driven out at 07:00 and home for 18:00 (docs/LIVING_WORLD_2.md §6.1)
		dev.voidmc.hearth.npc.work.field.RemoteSites.Site site = dev.voidmc.hearth.npc.work.field.FieldSites.siteOf(brain.settlement, brain.resident);
		if (site != null && site.kind() == dev.voidmc.hearth.npc.work.field.RemoteSites.Kind.PASTURE) {
			Task drove = dev.voidmc.hearth.npc.work.field.DroveWork.next(brain, level, site);
			if (drove != null) {
				return drove;
			}
			if (dev.voidmc.hearth.npc.work.field.DroveWork.phase(level, site).phase() == dev.voidmc.hearth.npc.work.field.Commute.Phase.AT) {
				pasture = dev.voidmc.hearth.npc.work.field.DroveWork.meadow(site);
				dev.voidmc.hearth.npc.work.field.WorkBarks.maybe(brain, level, "herder", 8, null);
			}
		}
		if (pasture == null) {
			pasture = pasture(brain, level);
		}
		BlockPos centre = BlockPos.containing(pasture.getCenter().x, pasture.minY + 1, pasture.getCenter().z);
		boolean handle = NpcModule.config().gathering().shearAndMilk();
		List<Animal> herd = level.getEntitiesOfClass(Animal.class, pasture.inflate(STRAY_RANGE, 4, STRAY_RANGE), HerderWork::herdAnimal);
		Animal stray = null;
		Sheep woolly = null;
		Cow cow = null;
		for (Animal a : herd) {
			boolean inside = pasture.contains(a.position());
			if (!inside) {
				if (stray == null || a.distanceToSqr(Vec3.atCenterOf(centre)) < stray.distanceToSqr(Vec3.atCenterOf(centre))) {
					stray = a;
				}
				continue;
			}
			if (a instanceof Sheep s && s.readyForShearing() && (woolly == null || brain.random.nextBoolean())) {
				woolly = s;
			} else if (a instanceof Cow c && !c.isBaby() && (cow == null || brain.random.nextBoolean())) {
				cow = c;
			}
		}
		if (stray != null && this.trips % 2 == 0) {
			return new Drive(stray, centre, pasture);
		}
		if (handle && woolly != null) {
			return new Tend(woolly, Tend.Kind.SHEAR);
		}
		if (handle && cow != null && this.trips % 3 == 0) {
			return new Tend(cow, Tend.Kind.MILK);
		}
		if (this.trips % 3 == 1) {
			return new Tasks.Linger(centre, null, FolkPose.TEND, 200, new ItemStack(Items.WHEAT), ItemStack.EMPTY, null, "feeding the herd");
		}
		return new FieldWork.WanderHolding(centre, Math.max(4, (int) (pasture.getXsize() / 2)), new ItemStack(Items.STICK), "minding the herd");
	}

	/** Sheep, cattle, goats, pigs and hens; never a pet (tamed, named, leashed) nor a caravan's beast. */
	static boolean herdAnimal(Animal a) {
		if (!a.isAlive() || a.isLeashed() || a.hasCustomName() || a instanceof TamableAnimal || a.entityTags().contains(dev.voidmc.hearth.npc.caravan.Caravans.TAG)) {
			return false;
		}
		return a instanceof Sheep || a instanceof Cow || a instanceof Goat || a instanceof Pig || a instanceof Chicken;
	}

	/** The herder's pasture: their work plot's footprint, or the land round their workplace. */
	static AABB pasture(FolkBrain brain, ServerLevel level) {
		PlotView plot = brain.resident.work != Resident.NO_PLOT ? brain.settlement.plot(brain.resident.work) : null;
		if (plot != null) {
			PlotSpec s = plot.spec();
			BlockPos o = s.origin();
			return new AABB(o.getX(), o.getY() - 3, o.getZ(), o.getX() + s.sizeX(), o.getY() + 6, o.getZ() + s.sizeZ());
		}
		BlockPos w = brain.places(level).work;
		return new AABB(w).inflate(10, 4, 10);
	}

	/** Shearing a sheep or milking a cow: walk up, a moment's work, the sound of it. */
	static final class Tend implements Task {
		enum Kind { SHEAR, MILK }

		private final Animal animal;
		private final Kind kind;
		private int timeout = 20 * 30;
		private int repath;
		private int after = -1;

		Tend(Animal animal, Kind kind) {
			this.animal = animal;
			this.kind = kind;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			if (this.after >= 0) {
				// a moment holding the fleece or the pail
				this.after -= elapsed;
				return this.after <= 0 ? Status.DONE : Status.RUNNING;
			}
			this.timeout -= elapsed;
			if (!this.animal.isAlive() || this.timeout < 0) {
				return Status.FAILED;
			}
			brain.hold(new ItemStack(this.kind == Kind.SHEAR ? Items.SHEARS : Items.BUCKET), ItemStack.EMPTY);
			if (brain.folk.distanceToSqr(this.animal) > 2.6 * 2.6) {
				this.repath -= elapsed;
				if (this.repath <= 0) {
					brain.folk.getNavigation().moveTo(this.animal, 0.6);
					this.repath = 10;
				}
				return Status.RUNNING;
			}
			brain.folk.getNavigation().stop();
			// hold the beast still a moment
			this.animal.getNavigation().stop();
			brain.folk.getLookControl().setLookAt(this.animal, 30.0F, 30.0F);
			brain.swing();
			brain.pose(FolkPose.TEND, 30);
			Vec3 at = this.animal.position().add(0, this.animal.getBbHeight() * 0.6, 0);
			if (this.kind == Kind.SHEAR && this.animal instanceof Sheep sheep && sheep.readyForShearing()) {
				sheep.setSheared(true);
				level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState()), at.x, at.y, at.z, 10, 0.3, 0.2, 0.3, 0.02);
				brain.hold(new ItemStack(Items.SHEARS), new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)));
			} else if (this.kind == Kind.MILK) {
				level.playSound(null, this.animal, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
				brain.hold(new ItemStack(Items.MILK_BUCKET), ItemStack.EMPTY);
			}
			this.after = 40;
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.getNavigation().stop();
			brain.folk.setFolkPose(FolkPose.NONE);
		}

		@Override
		public String describe() {
			return this.kind == Kind.SHEAR ? "shearing a sheep" : "milking a cow";
		}
	}

	/** Fetching a stray: get round behind it and walk it back into the pasture. */
	static final class Drive implements Task {
		private final Animal animal;
		private final BlockPos centre;
		private final AABB pasture;
		private int timeout = 20 * 25;
		private int nudge;

		Drive(Animal animal, BlockPos centre, AABB pasture) {
			this.animal = animal;
			this.centre = centre;
			this.pasture = pasture;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.timeout -= elapsed;
			if (!this.animal.isAlive() || this.timeout < 0 || this.pasture.contains(this.animal.position())) {
				return Status.DONE;
			}
			brain.hold(new ItemStack(Items.STICK), new ItemStack(Items.WHEAT));
			// Stand on the far side of the animal from the pasture, and press it on.
			Vec3 a = this.animal.position();
			Vec3 toPasture = Vec3.atCenterOf(this.centre).subtract(a).multiply(1, 0, 1);
			Vec3 behind = toPasture.lengthSqr() < 1.0E-4 ? a : a.subtract(toPasture.normalize().scale(2.5));
			this.nudge -= elapsed;
			if (this.nudge <= 0) {
				this.nudge = 15;
				brain.folk.getNavigation().moveTo(behind.x, behind.y, behind.z, 0.7);
				if (brain.folk.distanceToSqr(this.animal) < 5 * 5) {
					this.animal.getNavigation().moveTo(this.centre.getX() + 0.5, this.centre.getY(), this.centre.getZ() + 0.5, 1.1);
					if (brain.random.nextInt(3) == 0) {
						brain.swing();
					}
				}
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.getNavigation().stop();
		}

		@Override
		public String describe() {
			return "driving a stray back to the pasture";
		}
	}
}
