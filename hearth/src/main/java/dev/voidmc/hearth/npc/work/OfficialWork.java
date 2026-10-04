package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The town's officials at their work (docs/LIVING_WORLD.md §5, §8.1):
 *
 * <ul>
 *   <li><b>Reeve</b> — the ledgers at the town hall; rounds of the notice board, the market and the building sites; in
 *   the morning, a report to the lord's hall where there is one.</li>
 *   <li><b>Steward</b> — the keep's accounts and strongbox, the granary and stores, and attendance on the lord.</li>
 *   <li><b>Judge</b> — presiding in the courthouse, reading the law, a look at the pillory.</li>
 *   <li><b>Jailer</b> — the round of the cells with the keys, and the jail door.</li>
 * </ul>
 *
 * Their places come from the town's plots ({@code civic/town_hall}, {@code noble/*}, {@code law/*}) and POIs
 * ({@code THRONE}, {@code CELL}, {@code PILLORY}, {@code STRONGBOX}, {@code NOTICE_BOARD}...); where a place is
 * missing, they work at their own job site.
 */
final class OfficialWork implements WorkBehaviour {
	private final JobType job;
	private int bout;

	OfficialWork(JobType job) {
		this.job = job;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		this.bout++;
		return switch (this.job) {
			case REEVE -> reeve(brain, level);
			case STEWARD -> steward(brain, level);
			case JUDGE -> judge(brain, level);
			case JAILER -> jailer(brain, level);
			default -> desk(brain, level, "at work");
		};
	}

	private Task reeve(FolkBrain brain, ServerLevel level) {
		int minute = NpcTime.minuteOfDay(level);
		Poi throne = first(brain, level, PoiType.THRONE);
		if (throne != null && minute < 10 * 60 && this.bout % 4 == 1) {
			return attend(throne, new ItemStack(Items.WRITABLE_BOOK), "reporting to the lord");
		}
		return switch (this.bout % 4) {
			case 1 -> {
				Poi board = first(brain, level, PoiType.NOTICE_BOARD);
				yield board != null ? new Tasks.Linger(board.pos().relative(board.facing()), board.pos(), FolkPose.READ, 200, new ItemStack(Items.PAPER),
					ItemStack.EMPTY, null, "posting notices") : desk(brain, level, "keeping the rolls");
			}
			case 2 -> {
				BlockPos site = buildingSite(brain);
				yield site != null ? new Tasks.Linger(site, site, FolkPose.TALK, 240, new ItemStack(Items.PAPER), ItemStack.EMPTY, null,
					"inspecting the works") : desk(brain, level, "keeping the rolls");
			}
			case 3 -> {
				Poi stall = random(brain, level, PoiType.MARKET_STALL);
				yield stall != null ? new Tasks.Linger(stall.pos().relative(stall.facing(), 2), stall.pos(), FolkPose.TALK, 200, ItemStack.EMPTY,
					ItemStack.EMPTY, null, "collecting the market dues") : desk(brain, level, "keeping the rolls");
			}
			default -> desk(brain, level, "keeping the rolls");
		};
	}

	private Task steward(FolkBrain brain, ServerLevel level) {
		Poi throne = first(brain, level, PoiType.THRONE);
		switch (this.bout % 3) {
			case 0 -> {
				Poi box = first(brain, level, PoiType.STRONGBOX);
				if (box != null) {
					return new Tasks.Linger(box.pos(), box.pos().relative(box.facing()), FolkPose.CRAFT, 300, new ItemStack(Items.GOLD_NUGGET),
						new ItemStack(Items.BOOK), Tasks.every(70, SoundEvents.CHAIN_PLACE, 0.3F, null, false), "counting the treasury");
				}
			}
			case 1 -> {
				if (throne != null) {
					return attend(throne, new ItemStack(Items.BOOK), "attending the lord");
				}
			}
			default -> {
				Poi store = random(brain, level, PoiType.STORAGE);
				if (store != null) {
					return new Tasks.Linger(store.pos(), store.pos().relative(store.facing()), FolkPose.READ, 240, new ItemStack(Items.WRITABLE_BOOK),
						ItemStack.EMPTY, Tasks.every(100, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false), "taking stock");
				}
			}
		}
		return desk(brain, level, "at the accounts");
	}

	private Task judge(FolkBrain brain, ServerLevel level) {
		PlotView court = plot(brain, "law/courthouse");
		if (this.bout % 5 == 0) {
			Poi pillory = first(brain, level, PoiType.PILLORY);
			if (pillory != null) {
				return new Tasks.Linger(pillory.pos().relative(pillory.facing(), 3), pillory.pos(), FolkPose.NONE, 160, ItemStack.EMPTY, ItemStack.EMPTY,
					null, "seeing justice done");
			}
		}
		if (court != null) {
			List<Poi> pois = brain.town.places.plot(level, brain.settlement, court);
			Poi seat = pick(brain, pois, PoiType.THRONE);
			if (seat == null) {
				seat = pick(brain, pois, PoiType.SEAT);
			}
			if (seat != null && this.bout % 2 == 0) {
				return new Tasks.Sit(seat, 600 + brain.random.nextInt(400), new ItemStack(Items.BOOK), Tasks.every(160, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false));
			}
			Poi lectern = pick(brain, pois, PoiType.LECTERN);
			if (lectern != null) {
				return new Tasks.Linger(lectern.pos(), lectern.pos().relative(lectern.facing()), FolkPose.READ, 400, new ItemStack(Items.BOOK),
					ItemStack.EMPTY, Tasks.every(140, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false), "reading the law");
			}
		}
		return desk(brain, level, "reading the law");
	}

	private Task jailer(FolkBrain brain, ServerLevel level) {
		List<Poi> cells = brain.town.places.all(level, brain.settlement, PoiType.CELL);
		if (!cells.isEmpty() && this.bout % 3 != 0) {
			Poi cell = cells.get(this.bout % cells.size());
			return new Tasks.Linger(cell.pos().relative(cell.facing().getOpposite(), 2), cell.pos(), FolkPose.NONE, 200, new ItemStack(Items.TRIPWIRE_HOOK),
				ItemStack.EMPTY, Tasks.every(120, SoundEvents.CHAIN_STEP, 0.4F, null, false), "looking in on the cells");
		}
		PlotView jail = plot(brain, "law/jail");
		if (jail != null) {
			for (Poi p : brain.town.places.plot(level, brain.settlement, jail)) {
				if (p.type() == PoiType.DOOR) {
					return new Tasks.Linger(p.pos(), null, FolkPose.NONE, 400, new ItemStack(Items.TRIPWIRE_HOOK), ItemStack.EMPTY, null, "at the jail door");
				}
			}
		}
		return desk(brain, level, "keeping the keys");
	}

	/** Standing before the high seat, attending. */
	static Task attend(Poi throne, ItemStack held, String what) {
		BlockPos before = throne.pos().relative(throne.facing(), 3);
		return new Tasks.Linger(before, throne.pos(), FolkPose.TALK, 300, held, ItemStack.EMPTY, null, what);
	}

	/** Their own desk: the job site of their workplace, with the ledger. */
	static Task desk(FolkBrain brain, ServerLevel level, String what) {
		ResidentPlaces p = brain.places(level);
		Poi site = p.workSite;
		BlockPos at = site != null ? site.pos() : p.work;
		BlockPos look = site != null ? site.pos().relative(site.facing()) : null;
		return new Tasks.Linger(at, look, FolkPose.READ, 400 + brain.random.nextInt(300), new ItemStack(Items.WRITABLE_BOOK), ItemStack.EMPTY,
			Tasks.every(150, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false), what);
	}

	/** A plot under construction: where an official goes to see the works. */
	static @Nullable BlockPos buildingSite(FolkBrain brain) {
		List<PlotView> building = new ArrayList<>();
		for (PlotView p : brain.settlement.plots()) {
			if (p.state() == PlotState.BUILDING) {
				building.add(p);
			}
		}
		if (building.isEmpty()) {
			return null;
		}
		PlotView p = building.get(brain.random.nextInt(building.size()));
		// out in the street before it, looking on
		return ResidentPlaces.centre(p.spec()).relative(p.spec().facing(), p.spec().depth() / 2 + 2);
	}

	static @Nullable PlotView plot(FolkBrain brain, String path) {
		for (PlotView p : brain.settlement.plots()) {
			if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals(path)) {
				return p;
			}
		}
		return null;
	}

	static @Nullable Poi first(FolkBrain brain, ServerLevel level, PoiType type) {
		List<Poi> all = brain.town.places.all(level, brain.settlement, type);
		if (all.isEmpty()) {
			return null;
		}
		BlockPos home = brain.places(level).work;
		Poi best = all.get(0);
		for (Poi p : all) {
			if (p.pos().distSqr(home) < best.pos().distSqr(home)) {
				best = p;
			}
		}
		return best;
	}

	static @Nullable Poi random(FolkBrain brain, ServerLevel level, PoiType type) {
		List<Poi> all = brain.town.places.all(level, brain.settlement, type);
		return all.isEmpty() ? null : all.get(brain.random.nextInt(all.size()));
	}

	static @Nullable Poi pick(FolkBrain brain, List<Poi> pois, PoiType type) {
		List<Poi> of = new ArrayList<>();
		for (Poi p : pois) {
			if (p.type() == type) {
				of.add(p);
			}
		}
		return of.isEmpty() ? null : of.get(brain.random.nextInt(of.size()));
	}
}
