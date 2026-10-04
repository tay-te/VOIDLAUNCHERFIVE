package dev.voidmc.hearth.npc.routine;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Turns an activity into the next concrete task: where to sleep, eat, spend the evening, pass free time. Destinations
 * come from the resident's own places (bed, table, fire, seat, door) and the town's public ones (squares, inns,
 * benches, altars, lecterns, viewpoints), with sensible fallbacks when a town has none of a kind.
 */
public final class Plans {
	private Plans() {
	}

	static @Nullable Task plan(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		return switch (brain.activity()) {
			case SLEEP -> new Tasks.Sleep(p.bed, p.home);
			case WAKE -> new Tasks.Sequence("getting up", Tasks.Linger.at(p.bed != null ? p.home : null, 120 + brain.random.nextInt(200), "getting up"),
				doorstep(brain, level, p, 200));
			case EAT -> eat(brain, level, p);
			case WORK -> {
				Task t = brain.work().next(brain, level);
				yield t != null ? t : Tasks.Linger.at(p.work, 200, "idling at work");
			}
			case LEISURE -> leisure(brain, level, p);
			case HOME -> evening(brain, level, p);
			case SHELTER -> new Tasks.Sequence("sheltering", new Tasks.GoTo(p.bed != null ? p.bed : p.home, 2.0, 0.75, 20 * 60),
				Tasks.Linger.at(null, 400, "sheltering"));
			case FLEE -> new Tasks.Flee();
			// One folk rings the bell; the rest get indoors.
			case ALARM -> new Tasks.Alarm(brain.town.claimBell(level.getGameTime()) ? nearestBell(brain, level) : null);
		};
	}

	private static Task eat(FolkBrain brain, ServerLevel level, ResidentPlaces p) {
		ItemStack food = Tasks.meal(brain);
		Schedule.Block b = brain.block();
		Schedule.Where where = b != null && b.where() != null ? b.where() : Schedule.Where.HOME;
		int ticks = 600 + brain.random.nextInt(400);
		if (where == Schedule.Where.INN) {
			List<Poi> inn = brain.town.places.inn(brain.settlement);
			Poi seat = pick(brain, inn, PoiType.SEAT);
			if (seat != null) {
				return new Tasks.Sit(seat, ticks, food, Tasks.eating(food));
			}
		}
		if (where == Schedule.Where.WORK) {
			return new Tasks.Linger(p.work, null, FolkPose.NONE, ticks, food, ItemStack.EMPTY, Tasks.eating(food), "lunch at work");
		}
		Poi seat = pick(brain, p.homeSeats);
		if (seat != null) {
			return new Tasks.Sit(seat, ticks, food, Tasks.eating(food));
		}
		Poi table = pick(brain, p.homeTables);
		BlockPos at = table != null ? table.pos() : !p.homeHearths.isEmpty() ? p.homeHearths.get(0).pos() : p.home;
		return new Tasks.Linger(at, null, FolkPose.NONE, ticks, food, ItemStack.EMPTY, Tasks.eating(food), "eating at home");
	}

	private static Task evening(FolkBrain brain, ServerLevel level, ResidentPlaces p) {
		// The evening's centre is the fire.
		Poi seat = pick(brain, p.homeSeats);
		if (seat != null && brain.random.nextInt(3) != 0) {
			ItemStack held = brain.traits.contains(dev.voidmc.hearth.npc.person.Trait.DREAMY) ? new ItemStack(Items.BOOK) : ItemStack.EMPTY;
			return new Tasks.Sit(seat, 900 + brain.random.nextInt(900), held, null);
		}
		BlockPos fire = !p.homeHearths.isEmpty() ? p.homeHearths.get(0).pos() : p.home;
		return new Tasks.Linger(fire, null, FolkPose.NONE, 600 + brain.random.nextInt(600), ItemStack.EMPTY, ItemStack.EMPTY, null, "an evening by the fire");
	}

	static Task doorstep(FolkBrain brain, ServerLevel level, ResidentPlaces p, int ticks) {
		BlockPos door = p.homeDoor != null ? p.homeDoor.pos() : p.home;
		if (brain.random.nextInt(3) == 0) {
			// A little gardening by the door.
			return new Tasks.Linger(door, null, FolkPose.TEND, ticks, ItemStack.EMPTY, ItemStack.EMPTY, null, "tending the garden");
		}
		return new Tasks.Sequence("about the house", Tasks.Linger.at(door, ticks, "on the doorstep"), new Tasks.Wander(door, 5, 2));
	}

	private static Task leisure(FolkBrain brain, ServerLevel level, ResidentPlaces p) {
		Schedule.Block b = brain.block();
		Leisure what = b != null && b.leisure() != null ? b.leisure() : Leisure.PLAZA;
		BlockPos plaza = plaza(brain, level);
		return switch (what) {
			case PLAZA -> new Tasks.Sequence("at the square", Tasks.Linger.at(near(brain, plaza, 4), 400 + brain.random.nextInt(600), "at the square"),
				new Tasks.Wander(plaza, 8, 2));
			case INN -> {
				Poi seat = pick(brain, brain.town.places.inn(brain.settlement), PoiType.SEAT);
				yield seat != null ? new Tasks.Sit(seat, 900 + brain.random.nextInt(900), ItemStack.EMPTY, null)
					: Tasks.Linger.at(near(brain, plaza, 5), 600, "at the square (no inn)");
			}
			case VIEWPOINT -> {
				Poi view = pick(brain, brain.town.places.all(level, brain.settlement, PoiType.VIEWPOINT));
				yield view != null ? new Tasks.Linger(view.pos(), view.pos().relative(view.facing(), 8), FolkPose.NONE, 600 + brain.random.nextInt(600),
					ItemStack.EMPTY, ItemStack.EMPTY, null, "taking in the view") : new Tasks.Wander(plaza, 24, 4);
			}
			case VISIT -> visit(brain, level, p, plaza);
			case PLAY -> brain.resident.age == AgeStage.CHILD ? new Play(plaza) : new Tasks.Wander(plaza, 10, 3);
			case PRAY -> {
				Poi altar = pick(brain, brain.town.places.all(level, brain.settlement, PoiType.ALTAR));
				yield altar != null ? new Tasks.Linger(altar.pos(), altar.pos().relative(altar.facing()), FolkPose.PRAY, 500 + brain.random.nextInt(400),
					ItemStack.EMPTY, ItemStack.EMPTY, null, "praying") : Tasks.Linger.at(near(brain, plaza, 4), 400, "at the square");
			}
			case BENCH -> {
				Poi seat = pick(brain, brain.town.places.all(level, brain.settlement, PoiType.SEAT));
				if (seat == null) {
					seat = pick(brain, p.homeSeats);
				}
				yield seat != null ? new Tasks.Sit(seat, 1200 + brain.random.nextInt(1200), ItemStack.EMPTY, null) : doorstep(brain, level, p, 600);
			}
			case WANDER -> new Tasks.Wander(plaza, 28, 5);
			case READ -> {
				Poi lectern = pick(brain, brain.town.places.all(level, brain.settlement, PoiType.LECTERN));
				ItemStack book = new ItemStack(Items.BOOK);
				if (lectern != null) {
					yield new Tasks.Linger(lectern.pos(), lectern.pos().relative(lectern.facing()), FolkPose.READ, 600, book, ItemStack.EMPTY,
						Tasks.every(160, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false), "reading");
				}
				Poi seat = pick(brain, p.homeSeats);
				yield seat != null ? new Tasks.Sit(seat, 900, book, Tasks.every(160, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false))
					: new Tasks.Linger(p.home, null, FolkPose.READ, 600, book, ItemStack.EMPTY, null, "reading at home");
			}
			case DOORSTEP -> doorstep(brain, level, p, 500 + brain.random.nextInt(500));
		};
	}

	/** Calls on family or a friend: walks to their door and lingers. */
	private static Task visit(FolkBrain brain, ServerLevel level, ResidentPlaces mine, BlockPos plaza) {
		NpcData d = brain.data;
		List<UUID> people = new ArrayList<>(d.friends());
		people.addAll(d.parents());
		people.addAll(d.children());
		d.spouse().ifPresent(people::remove);
		for (int tries = 0; tries < 4 && !people.isEmpty(); tries++) {
			Resident other = brain.settlement.resident(people.get(brain.random.nextInt(people.size())));
			if (other == null || other.home == brain.resident.home && other.home != Resident.NO_PLOT) {
				continue;
			}
			ResidentPlaces theirs = ResidentPlaces.resolve(level, brain.settlement, other, brain.town.places);
			BlockPos door = theirs.homeDoor != null ? theirs.homeDoor.pos() : theirs.home;
			return new Tasks.Linger(door, door, FolkPose.NONE, 500 + brain.random.nextInt(500), ItemStack.EMPTY, ItemStack.EMPTY, null,
				"visiting " + other.givenName);
		}
		return Tasks.Linger.at(near(brain, plaza, 4), 500, "at the square");
	}

	/** The town's heart: a meeting point (the bell, the well) or its founding centre. */
	public static BlockPos plaza(FolkBrain brain, ServerLevel level) {
		List<Poi> meet = brain.town.places.all(level, brain.settlement, PoiType.MEETING_POINT);
		if (!meet.isEmpty()) {
			// Folk favour the meeting point nearest their home.
			BlockPos home = brain.places(level).home;
			Poi best = meet.get(0);
			for (Poi m : meet) {
				if (m.pos().distSqr(home) < best.pos().distSqr(home)) {
					best = m;
				}
			}
			return best.pos();
		}
		return brain.settlement.center();
	}

	private static @Nullable BlockPos nearestBell(FolkBrain brain, ServerLevel level) {
		List<Poi> meet = brain.town.places.all(level, brain.settlement, PoiType.MEETING_POINT);
		BlockPos here = brain.folk.blockPosition();
		BlockPos best = null;
		for (Poi m : meet) {
			for (BlockPos b : new BlockPos[]{m.pos(), m.pos().above(), m.pos().relative(m.facing())}) {
				if (level.isLoaded(b) && level.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BellBlock
					&& b.distSqr(here) < 48 * 48 && (best == null || b.distSqr(here) < best.distSqr(here))) {
					best = b;
				}
			}
		}
		return best;
	}

	private static BlockPos near(FolkBrain brain, BlockPos centre, int r) {
		return centre.offset(brain.random.nextInt(2 * r + 1) - r, 0, brain.random.nextInt(2 * r + 1) - r);
	}

	static @Nullable Poi pick(FolkBrain brain, List<Poi> pois) {
		return pois.isEmpty() ? null : pois.get(brain.random.nextInt(pois.size()));
	}

	static @Nullable Poi pick(FolkBrain brain, List<Poi> pois, PoiType type) {
		List<Poi> of = new ArrayList<>();
		for (Poi p : pois) {
			if (p.type() == type) {
				of.add(p);
			}
		}
		return pick(brain, of);
	}

	/**
	 * Children's games round the square: tag when other children are about (the one who is "it" chases, and a touch
	 * passes it on), otherwise running about and cheering.
	 */
	static final class Play implements Task {
		private final BlockPos centre;
		private int ticks = 1200;
		private @Nullable Folk chasing;
		private int cooldown;
		private @Nullable BlockPos spot;
		private int nextChase;

		Play(BlockPos centre) {
			this.centre = centre;
		}

		@Override
		public Status tick(FolkBrain brain, ServerLevel level, int elapsed) {
			this.ticks -= elapsed;
			if (this.ticks <= 0) {
				return Status.DONE;
			}
			if (this.cooldown > 0) {
				this.cooldown -= elapsed;
				return Status.RUNNING;
			}
			Folk me = brain.folk;
			if (this.chasing == null && brain.random.nextInt(8) == 0) {
				List<Folk> kids = level.getEntitiesOfClass(Folk.class, me.getBoundingBox().inflate(14, 4, 14),
					f -> f != me && f.isBaby() && f.brain() != null && f.brain().task() instanceof Play);
				if (!kids.isEmpty()) {
					this.chasing = kids.get(brain.random.nextInt(kids.size()));
				}
			}
			if (this.chasing != null) {
				Folk target = this.chasing;
				if (!target.isAlive() || target.distanceToSqr(this.centre.getX(), this.centre.getY(), this.centre.getZ()) > 30 * 30) {
					this.chasing = null;
					return Status.RUNNING;
				}
				if (me.tickCount >= this.nextChase) {
					// Re-aim at the runner twice a second, not every tick.
					me.getNavigation().moveTo(target, 1.15);
					this.nextChase = me.tickCount + 10;
				}
				if (me.distanceToSqr(target) < 1.6) {
					// Tag! It passes on, and the tagger runs off.
					brain.swing();
					this.chasing = null;
					brain.pose(FolkPose.CHEER, 20);
					if (target.brain() != null && target.brain().task() instanceof Play other) {
						other.chasing = me;
						other.cooldown = 30;
					}
					this.spot = null;
				}
				return Status.RUNNING;
			}
			if (this.spot == null || brain.walk(level, this.spot, 1.5, 1.0) || brain.walkFailed()) {
				brain.resetWalk();
				if (this.spot != null && brain.random.nextInt(3) == 0) {
					brain.pose(FolkPose.CHEER, 30);
					level.sendParticles(ParticleTypes.NOTE, me.getX(), me.getY() + 1.6, me.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
					this.cooldown = 30;
				}
				this.spot = near(brain, this.centre, 9);
			}
			return Status.RUNNING;
		}

		@Override
		public void stop(FolkBrain brain, ServerLevel level) {
			brain.folk.getNavigation().stop();
			brain.resetWalk();
		}

		@Override
		public String describe() {
			return this.chasing != null ? "playing tag (it)" : "playing";
		}
	}
}
