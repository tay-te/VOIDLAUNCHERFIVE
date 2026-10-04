package dev.voidmc.hearth.law.lock;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.item.KeyData;
import dev.voidmc.hearth.law.item.LawComponents;
import dev.voidmc.hearth.law.own.OwnerRules;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.state.LawState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Locks in settlements (docs/LIVING_WORLD.md §4). Rather than a block state, a lock is worked out from where the block
 * is: whose plot, what kind of building, the hour. Homes lock their doors at night, shops and workshops outside opening
 * hours, the jail always; chests in homes are often locked and in shops, banks, keeps and the jail always. How good a
 * lock is follows the building (a hovel's latch, a bank's strongbox). Picked locks stay open until their owners lock
 * up again; forced ones until they are mended. The only saved state is those exceptions
 * ({@link LawState#locks()}), so locks cost nothing in towns nobody visits.
 */
public final class Locks {
	/** Ticks a picked door stays open before its owners notice and lock it again. */
	static final long PICKED_DOOR_TICKS = 12_000;
	static final long PICKED_CHEST_TICKS = 6_000;

	private Locks() {
	}

	/**
	 * A lock as it stands.
	 *
	 * @param pos    the lock's block (the lower half of a door)
	 * @param tier   1 (very easy) to 5 (very hard)
	 * @param locked locked now
	 * @param door   a door (otherwise a container)
	 */
	public record Lock(BlockPos pos, Settlement settlement, PlotView plot, Owner owner, int tier, boolean locked, boolean door) {
	}

	/** The block a lock is kept at: the lower half of a door, one half (the same either way) of a double chest. */
	public static BlockPos canonical(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.immutable();
		}
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			return other.asLong() < pos.asLong() ? other : pos.immutable();
		}
		return pos.immutable();
	}

	/** Whether this block can carry a lock at all. */
	public static boolean lockable(BlockState state) {
		return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock;
	}

	/** The lock on a block, or null if it has none (not lockable, public, a player's own, or lock-free by the rules). */
	public static @Nullable Lock at(ServerLevel level, BlockPos pos, BlockState state) {
		if (!lockable(state)) {
			return null;
		}
		boolean door = state.getBlock() instanceof DoorBlock;
		BlockPos lockPos = canonical(pos, state);
		Ownership.Place place = Ownership.place(level, lockPos);
		if (place == null || place.plot() == null || place.owner() == null || place.owner().kind() == Owner.Kind.PLAYER) {
			return null;
		}
		Settlement s = place.settlement();
		PlotView plot = place.plot();
		ArchetypeCategory category = OwnerRules.category(plot.spec().archetype());
		String path = plot.spec().archetype().getPath();
		int tier = door ? doorTier(category, path, plot.spec().tier()) : chestTier(category, path, plot.spec().tier(), lockPos, s, level);
		if (tier <= 0) {
			return null;
		}
		boolean locked = lockedNow(level, s, lockPos, door, category, path);
		return new Lock(lockPos, s, plot, place.owner(), tier, locked, door);
	}

	/** How good a building's door locks are (0: never locked). */
	static int doorTier(ArchetypeCategory category, String path, int finish) {
		if (isOpenHouse(path)) {
			return 0;
		}
		int bonus = finish >= 3 ? 1 : 0;
		return switch (category) {
			case HOUSING -> 1 + bonus;
			case NOBLE -> 3 + bonus;
			case LAW -> 4;
			case COMMERCE -> path.endsWith("bank") || path.contains("counting_house") ? 4 : 2 + bonus;
			case CIVIC -> path.contains("town_hall") || path.contains("courthouse") || isGuardhouse(path) ? 3 : 2;
			case WORKPLACE, STORAGE, DEFENSE, LANDMARK, FARM -> 2;
			case EXTRACTION, UNDERGROUND -> 1;
			case INFRASTRUCTURE, LANDSCAPE -> 0;
		};
	}

	/** How good a container's lock is (0: unlocked), deterministic per chest. */
	static int chestTier(ArchetypeCategory category, String path, int finish, BlockPos pos, Settlement s, ServerLevel level) {
		for (Poi p : s.pois(PoiType.STRONGBOX)) {
			if (p.pos().equals(pos)) {
				return 5;
			}
		}
		return chestTier(category, path, finish, mix(pos.asLong() ^ s.id() * 0x9E3779B97F4A7C15L));
	}

	/** {@link #chestTier} from a per-chest hash (pure, for tests). */
	static int chestTier(ArchetypeCategory category, String path, int finish, long hash) {
		double roll = (hash >>> 11) * 0x1.0p-53;
		int bonus = finish >= 3 ? 1 : 0;
		return switch (category) {
			case HOUSING -> roll < 0.45 ? 0 : roll < 0.8 ? 1 + bonus : 2 + bonus;
			case NOBLE -> 4;
			case LAW -> 4;
			case COMMERCE -> path.endsWith("bank") || path.contains("counting_house") ? 5 : isOpenHouse(path) ? (roll < 0.5 ? 0 : 2) : 3;
			case CIVIC -> path.contains("town_hall") || path.contains("courthouse") ? 4 : roll < 0.5 ? 0 : 2;
			case WORKPLACE, FARM -> roll < 0.5 ? 0 : 2;
			case STORAGE -> roll < 0.3 ? 0 : 2;
			case DEFENSE, LANDMARK -> 3;
			case EXTRACTION, UNDERGROUND -> roll < 0.6 ? 0 : 1;
			case INFRASTRUCTURE, LANDSCAPE -> 0;
		};
	}

	/** The guardhouse and watch house, which hold cells. */
	static boolean isGuardhouse(String path) {
		return path.endsWith("guardhouse") || path.endsWith("watch_house");
	}

	/** Buildings anyone may walk into at any hour: inns, taverns, hostels, chapels and temples. */
	static boolean isOpenHouse(String path) {
		return path.endsWith("inn") || path.endsWith("tavern") || path.endsWith("hostel") || path.endsWith("caravanserai") || path.endsWith("chapel")
			|| path.endsWith("temple") || path.endsWith("cathedral") || path.endsWith("shrine") || path.endsWith("bathhouse");
	}

	private static boolean lockedNow(ServerLevel level, Settlement s, BlockPos pos, boolean door, ArchetypeCategory category, String path) {
		LawConfig.Stealth cfg = Law.config().stealth();
		LawState.LockOverride override = Law.state(s).locks().get(pos.asLong());
		if (override != null && (override.broken() || override.until() > level.getGameTime())) {
			return false;
		}
		if (!door) {
			return cfg.lockContainers();
		}
		if (!cfg.lockDoors()) {
			return false;
		}
		int minute = Law.minuteOfDay(level);
		if (isGuardhouse(path)) {
			return true; // where the cells are: always locked (the guards have keys and folk pass doors anyway)
		}
		return switch (category) {
			case LAW -> true;
			case HOUSING, NOBLE -> Law.within(minute, cfg.nightStart(), cfg.nightEnd());
			case UNDERGROUND -> path.contains("dwelling") ? Law.within(minute, cfg.nightStart(), cfg.nightEnd())
				: Law.within(minute, cfg.shopClose(), cfg.shopOpen());
			default -> Law.within(minute, cfg.shopClose(), cfg.shopOpen());
		};
	}

	/** Whether the player carries a key to the lock's plot (anywhere in their inventory). */
	public static boolean hasKey(ServerPlayer player, Lock lock) {
		return keyFor(player.getInventory(), lock.settlement().id(), lock.plot().id()) != null;
	}

	public static @Nullable ItemStack keyFor(Inventory inventory, long settlementId, int plotId) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			KeyData key = stack.get(LawComponents.KEY);
			if (key != null && key.opens(settlementId, plotId)) {
				return stack;
			}
		}
		return null;
	}

	/** A picked lock: open until the owners lock up again. */
	public static void markPicked(Settlement s, Lock lock, long now) {
		Law.state(s).locks().put(lock.pos().asLong(), new LawState.LockOverride(lock.pos().asLong(),
			now + (lock.door() ? PICKED_DOOR_TICKS : PICKED_CHEST_TICKS), false));
		s.markDirty();
	}

	/** A forced lock (a smashed door): open until the day it is mended. */
	public static void markBroken(Settlement s, BlockPos pos) {
		Law.state(s).locks().put(pos.asLong(), new LawState.LockOverride(pos.asLong(), 0, true));
		s.markDirty();
	}

	/** Who keeps the key to a plot: the head of the household (home) or the workshop (work), or null. */
	public static @Nullable Resident keyHolder(Settlement s, int plotId) {
		Resident head = OwnerRules.head(s, plotId, r -> r.home);
		return head != null ? head : OwnerRules.head(s, plotId, r -> r.work);
	}

	/** The plot whose key a resident keeps (their home if they head it, else their workplace), or {@link PlotView#NONE}. */
	public static int keyPlot(Settlement s, Resident r) {
		if (r.home != Resident.NO_PLOT && keyHolder(s, r.home) == r) {
			return r.home;
		}
		if (r.work != Resident.NO_PLOT) {
			Resident head = OwnerRules.head(s, r.work, x -> x.work);
			if (head == r && OwnerRules.head(s, r.work, x -> x.home) == null) {
				return r.work;
			}
		}
		return PlotView.NONE;
	}

	/** The key a resident carries for a plot. */
	public static KeyData keyData(Settlement s, int plotId, Resident holder) {
		PlotView plot = s.plot(plotId);
		String building = plot == null ? "house" : plot.spec().archetype().getPath();
		building = building.substring(building.indexOf('/') + 1).replace('_', ' ');
		return new KeyData(s.id(), plotId, holder.fullName() + "'s " + building, s.name());
	}

	private static long mix(long z) {
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}
}
