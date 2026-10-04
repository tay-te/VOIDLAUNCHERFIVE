package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Plans;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trades that serve people rather than make things (docs/LIVING_WORLD.md §5–6):
 *
 * <ul>
 *   <li><b>Entertainer</b> — plays and sings on a {@code STAGE}, in the inn, or in the square: notes in the air, a cheer
 *   now and then.</li>
 *   <li><b>Bath keeper</b> — fills and tends the baths ({@code BATH}), towels and buckets of hot water.</li>
 *   <li><b>Stablemaster</b> — grooms and feeds the horses at the {@code HITCHING_POST}s and in the stables.</li>
 *   <li><b>Healer</b> — goes round the {@code SICKBED}s with herbs and potions, and grinds remedies at the bench.</li>
 * </ul>
 */
final class ServiceWork implements WorkBehaviour {
	private final JobType job;
	private int bout;

	ServiceWork(JobType job) {
		this.job = job;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		this.bout++;
		return switch (this.job) {
			case ENTERTAINER -> perform(brain, level);
			case BATH_KEEPER -> baths(brain, level);
			case STABLEMASTER -> stables(brain, level);
			case HEALER -> heal(brain, level);
			default -> OfficialWork.desk(brain, level, "at work");
		};
	}

	private Task perform(FolkBrain brain, ServerLevel level) {
		Poi stage = OfficialWork.random(brain, level, PoiType.STAGE);
		BlockPos at;
		BlockPos look = null;
		if (stage != null) {
			at = stage.pos();
			look = stage.pos().relative(stage.facing(), 4);
		} else {
			List<Poi> inn = brain.town.places.inn(brain.settlement);
			Poi hearth = null;
			for (Poi p : inn) {
				if (p.type() == PoiType.HEARTH || p.type() == PoiType.TABLE) {
					hearth = p;
				}
			}
			at = hearth != null && this.bout % 2 == 0 ? hearth.pos() : Plans.plaza(brain, level);
		}
		Tasks.Effect music = Tasks.every(14, SoundEvents.NOTE_BLOCK_HARP.value(), 0.7F, (b, lvl, e, x) -> {
			Vec3 c = b.folk.getEyePosition();
			lvl.sendParticles(ParticleTypes.NOTE, c.x, c.y + 0.6, c.z, 1, 0.3, 0.1, 0.3, b.random.nextDouble());
			if (b.random.nextInt(12) == 0) {
				b.pose(FolkPose.CHEER, 30);
			}
		}, false);
		return new Tasks.Linger(at, look, FolkPose.TALK, 600 + brain.random.nextInt(400), new ItemStack(Items.GOAT_HORN), ItemStack.EMPTY, music,
			"playing for the crowd");
	}

	private Task baths(FolkBrain brain, ServerLevel level) {
		Poi bath = OfficialWork.random(brain, level, PoiType.BATH);
		ResidentPlaces p = brain.places(level);
		if (bath == null) {
			return CounterWorkHelper.counter(brain, level, p, new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)), "keeping the bathhouse");
		}
		Tasks.Effect steam = Tasks.every(40, SoundEvents.BUCKET_EMPTY, 0.4F, (b, lvl, e, at) -> {
			Vec3 c = Vec3.atCenterOf(at != null ? at : b.folk.blockPosition());
			lvl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.6, c.z, 2, 0.4, 0.1, 0.4, 0.01);
		}, true);
		return new Tasks.Linger(bath.pos().relative(bath.facing().getOpposite()), bath.pos(), FolkPose.TEND, 300, new ItemStack(Items.WATER_BUCKET),
			new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)), steam, "tending the baths");
	}

	private Task stables(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		List<AbstractHorse> horses = level.getEntitiesOfClass(AbstractHorse.class, new AABB(p.work).inflate(16, 4, 16),
			h -> h.isAlive() && !h.entityTags().contains(dev.voidmc.hearth.npc.caravan.Caravans.TAG));
		if (!horses.isEmpty() && this.bout % 2 == 0) {
			AbstractHorse h = horses.get(brain.random.nextInt(horses.size()));
			BlockPos at = h.blockPosition();
			return new Tasks.Linger(at, at, FolkPose.TEND, 240, new ItemStack(Items.WHEAT), ItemStack.EMPTY,
				Tasks.every(50, SoundEvents.HORSE_EAT, 0.5F, null, true), "grooming a horse");
		}
		Poi post = OfficialWork.random(brain, level, PoiType.HITCHING_POST);
		BlockPos at = post != null ? post.pos() : p.work;
		return new Tasks.Linger(at, null, FolkPose.TEND, 300, new ItemStack(this.bout % 3 == 0 ? Items.SADDLE : Items.HAY_BLOCK), ItemStack.EMPTY,
			Tasks.every(70, SoundEvents.GRASS_STEP, 0.5F, null, true), "mucking out the stalls");
	}

	private Task heal(FolkBrain brain, ServerLevel level) {
		List<Poi> beds = brain.town.places.all(level, brain.settlement, PoiType.SICKBED);
		if (!beds.isEmpty() && this.bout % 2 == 0) {
			Poi bed = beds.get(brain.random.nextInt(beds.size()));
			Tasks.Effect care = Tasks.every(80, SoundEvents.BREWING_STAND_BREW, 0.3F, (b, lvl, e, at) -> {
				Vec3 c = Vec3.atCenterOf(bed.pos());
				lvl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.6, c.z, 3, 0.3, 0.2, 0.3, 0.0);
			}, true);
			return new Tasks.Linger(bed.pos().relative(bed.facing()), bed.pos(), FolkPose.TEND, 300, new ItemStack(Items.POTION), ItemStack.EMPTY, care,
				"tending the sick");
		}
		ResidentPlaces p = brain.places(level);
		Poi site = p.workSite;
		BlockPos at = site != null ? site.pos() : p.work;
		return new Tasks.Linger(at, site != null ? site.pos().relative(site.facing()) : null, FolkPose.CRAFT, 400, new ItemStack(Items.BOWL),
			new ItemStack(Items.FERN), Tasks.every(60, SoundEvents.COMPOSTER_FILL, 0.4F, null, true), "grinding herbs");
	}

	/** Behind a counter without a special place: shared with the counter trades. */
	static final class CounterWorkHelper {
		private CounterWorkHelper() {
		}

		static Task counter(FolkBrain brain, ServerLevel level, ResidentPlaces p, ItemStack held, String what) {
			Poi c = p.workSite;
			if (c == null) {
				for (Poi poi : p.workSpots) {
					if (poi.type() == PoiType.COUNTER) {
						c = poi;
					}
				}
			}
			if (c == null) {
				return Tasks.Linger.at(p.work, 400, what);
			}
			return new Tasks.Linger(c.pos(), c.pos().relative(c.facing(), 3), FolkPose.NONE, 500, held, ItemStack.EMPTY, null, what);
		}
	}
}
