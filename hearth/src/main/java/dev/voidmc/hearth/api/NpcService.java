package dev.voidmc.hearth.api;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The NPC module, as other modules use it: find embodied folk, ask what they can see, and teach them new behaviours
 * without editing the NPC module — the law module's guards and witnesses, the repair module's builders, the social
 * module's festivals and services all add {@link FolkBehaviour}s here. Implemented by the NPC module. Server thread only.
 */
public interface NpcService {
	/** Adds a behaviour every embodied folk considers, by priority, alongside its routine. Register during mod init. */
	void registerBehaviour(FolkBehaviour behaviour);

	/** The embodied folk for a resident, or null if the resident is only a record right now. */
	@Nullable FolkHandle folk(UUID resident);

	/** The embodied folk within {@code radius} of {@code at}, nearest first. */
	List<FolkHandle> folkNear(ServerLevel level, Vec3 at, double radius);

	/**
	 * Asks the NPC module to embody a resident who follows the player toward {@code destination} (escort quests,
	 * a guard taking a prisoner to the cell). Returns the resident's id, or null if it cannot now (caps, no space).
	 */
	@Nullable UUID follow(ServerPlayer player, Settlement settlement, Resident resident, BlockPos destination);

	/** An embodied folk, as behaviours drive it. Valid only during the call that hands it out (or while its entity lives). */
	interface FolkHandle {
		Resident resident();

		Settlement settlement();

		PathfinderMob entity();

		/** Awake and able to notice things (not asleep, not knocked out). */
		boolean awake();

		/**
		 * Whether this folk can see {@code target} now: in range, inside its field of view, with a clear line of sight.
		 * Light, sneaking and armour are the law module's to weigh on top (docs/LIVING_WORLD.md §4).
		 */
		boolean canSee(Entity target);

		/** Walks toward a block (pathfinding, doors, stairs); returns false if there is no path. */
		boolean moveTo(BlockPos target, double speed);

		boolean arrived();

		void stop();

		void lookAt(Entity target);

		void lookAt(BlockPos target);

		/** Says a line: a speech bubble or subtitle to players near, or to one player if {@code to} is not null. */
		void say(Component line, @Nullable ServerPlayer to);

		/** Holds an item in the main hand (empty to clear). */
		void hold(ItemStack stack);

		void swing();

		long gameTime();

		/** Wakes a sleeping folk (a fire, an alarm bell, a thief caught at their bedside); they stay up a while. */
		default void wake() {
		}
	}

	/** How an escort started with {@link #follow} stands, or null if there is none (outcomes are kept ten minutes). */
	default @Nullable EscortStatus escort(UUID resident) {
		return null;
	}

	/**
	 * Whether the resident's day has them at work right now (a guard on the watch rather than abed or off duty), by the
	 * NPC module's schedules. True when there is no NPC module to say otherwise.
	 */
	default boolean onDuty(Settlement settlement, Resident resident) {
		return true;
	}

	/**
	 * Makes a resident who is only a record into folk at standing room near {@code near} (a guard sent for), within the
	 * server's cap. Returns their folk (the one already about, if they are), or null if there is no room or no place.
	 */
	default @Nullable FolkHandle embodyNear(ServerLevel level, Settlement settlement, Resident resident, BlockPos near) {
		return null;
	}

	/** A resident's traits by name ({@code brave}, {@code greedy}, {@code shy}...), from the npc module's personality. */
	default java.util.Set<String> traits(Resident resident) {
		return java.util.Set.of();
	}

	enum EscortStatus {
		ACTIVE, ARRIVED, LOST
	}

	/**
	 * Something folk do beyond their routine. The NPC module asks every registered behaviour, by priority, whether it
	 * {@link #wants} a folk (about once a second, staggered; keep it cheap), runs the highest that does until it finishes
	 * or a higher one wants the folk, then the routine resumes.
	 */
	interface FolkBehaviour {
		Identifier id();

		/**
		 * Higher wins. The routine (sleep, work, meals, free time) is below 100; work that interrupts it (repairs, hauling)
		 * 100–199; reactions (witnessing a crime, fleeing a fire) 200–299; guards on duty and emergencies 300 and up.
		 */
		int priority();

		boolean wants(FolkHandle folk);

		void start(FolkHandle folk);

		/** One step; return false when the behaviour is done. Called every tick while it runs. */
		boolean tick(FolkHandle folk);

		void stop(FolkHandle folk);
	}
}
