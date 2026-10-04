package dev.voidmc.hearth.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.service.BehaviourRunner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/** The runner that lets other modules' behaviours take a folk over by priority (docs/FOLK.md §13). */
class BehaviourRunnerTest {
	/** A folk with nothing behind it but a clock: behaviours here never touch the entity. */
	static final class FakeFolk implements NpcService.FolkHandle {
		long time;

		@Override
		public Resident resident() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Settlement settlement() {
			throw new UnsupportedOperationException();
		}

		@Override
		public PathfinderMob entity() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean awake() {
			return true;
		}

		@Override
		public boolean canSee(Entity target) {
			return false;
		}

		@Override
		public boolean moveTo(BlockPos target, double speed) {
			return true;
		}

		@Override
		public boolean arrived() {
			return true;
		}

		@Override
		public void stop() {
		}

		@Override
		public void lookAt(Entity target) {
		}

		@Override
		public void lookAt(BlockPos target) {
		}

		@Override
		public void say(Component line, ServerPlayer to) {
		}

		@Override
		public void hold(ItemStack stack) {
		}

		@Override
		public void swing() {
		}

		@Override
		public long gameTime() {
			return this.time;
		}
	}

	static final class Probe implements NpcService.FolkBehaviour {
		final Identifier id;
		final int priority;
		boolean wants;
		int ticksLeft = Integer.MAX_VALUE;
		int started;
		int stopped;
		int asked;
		boolean throwInTick;

		Probe(String name, int priority) {
			this.id = Identifier.fromNamespaceAndPath("test", name);
			this.priority = priority;
		}

		@Override
		public Identifier id() {
			return this.id;
		}

		@Override
		public int priority() {
			return this.priority;
		}

		@Override
		public boolean wants(NpcService.FolkHandle folk) {
			this.asked++;
			return this.wants;
		}

		@Override
		public void start(NpcService.FolkHandle folk) {
			this.started++;
		}

		@Override
		public boolean tick(NpcService.FolkHandle folk) {
			if (this.throwInTick) {
				throw new IllegalStateException("boom");
			}
			return --this.ticksLeft > 0;
		}

		@Override
		public void stop(NpcService.FolkHandle folk) {
			this.stopped++;
		}
	}

	static final class Owner implements BehaviourRunner.Owner {
		int takeOvers;
		int handBacks;

		@Override
		public void takeOver(NpcService.FolkBehaviour behaviour) {
			this.takeOvers++;
		}

		@Override
		public void handBack(NpcService.FolkBehaviour behaviour) {
			this.handBacks++;
		}
	}

	private static List<NpcService.FolkBehaviour> sorted(Probe... probes) {
		List<NpcService.FolkBehaviour> list = new ArrayList<>(List.of(probes));
		list.sort(Comparator.comparingInt(NpcService.FolkBehaviour::priority).reversed());
		return list;
	}

	@Test
	void theHighestWantingBehaviourAboveTheRoutineRuns() {
		Probe repair = new Probe("repair", 150);
		Probe witness = new Probe("witness", 250);
		Probe festival = new Probe("festival", 30);
		repair.wants = true;
		festival.wants = true;
		BehaviourRunner runner = new BehaviourRunner();
		FakeFolk folk = new FakeFolk();
		Owner owner = new Owner();
		// at work (routine 50): the festival (30) does not get a look in, the repair does
		assertSame(repair, runner.poll(folk, 50, sorted(repair, witness, festival), owner));
		assertEquals(1, repair.started);
		assertEquals(1, owner.takeOvers);
		assertEquals(0, festival.asked, "behaviours below the floor are not even asked");
		// at leisure (routine 20) with nothing running, the festival would win
		BehaviourRunner idle = new BehaviourRunner();
		repair.wants = false;
		assertSame(festival, idle.poll(folk, 20, sorted(repair, witness, festival), new Owner()));
	}

	@Test
	void aHigherBehaviourTakesOverAndTheLowerOneStops() {
		Probe repair = new Probe("repair", 150);
		Probe arrest = new Probe("arrest", 320);
		repair.wants = true;
		BehaviourRunner runner = new BehaviourRunner();
		FakeFolk folk = new FakeFolk();
		Owner owner = new Owner();
		runner.poll(folk, 50, sorted(repair, arrest), owner);
		assertSame(repair, runner.current());
		int asked = repair.asked;
		arrest.wants = true;
		assertSame(arrest, runner.poll(folk, 50, sorted(repair, arrest), owner));
		assertEquals(1, repair.stopped);
		assertEquals(asked, repair.asked, "the running behaviour is not asked again");
		assertEquals(320, runner.priority());
	}

	@Test
	void finishedBehavioursHandTheFolkBack() {
		Probe haul = new Probe("haul", 120);
		haul.wants = true;
		haul.ticksLeft = 3;
		BehaviourRunner runner = new BehaviourRunner();
		FakeFolk folk = new FakeFolk();
		Owner owner = new Owner();
		runner.poll(folk, 50, sorted(haul), owner);
		assertTrue(runner.tick(folk, owner));
		assertTrue(runner.tick(folk, owner));
		assertFalse(runner.tick(folk, owner));
		assertNull(runner.current());
		assertEquals(1, haul.stopped);
		assertEquals(1, owner.handBacks);
	}

	@Test
	void aBehaviourThatThrowsIsStoppedAndSkippedForAWhile() {
		Probe bad = new Probe("bad", 200);
		bad.wants = true;
		bad.throwInTick = true;
		BehaviourRunner runner = new BehaviourRunner();
		FakeFolk folk = new FakeFolk();
		Owner owner = new Owner();
		runner.poll(folk, 50, sorted(bad), owner);
		assertFalse(runner.tick(folk, owner));
		assertEquals(1, bad.stopped);
		folk.time += 100;
		assertNull(runner.poll(folk, 50, sorted(bad), owner), "suspended after failing");
		folk.time += 2000;
		assertSame(bad, runner.poll(folk, 50, sorted(bad), owner), "tried again after the suspension");
	}

	@Test
	void theFloorKeepsLowBehavioursOutOfReactions() {
		Probe haul = new Probe("haul", 120);
		haul.wants = true;
		BehaviourRunner runner = new BehaviourRunner();
		// fleeing (250) outranks hauling
		assertNull(runner.poll(new FakeFolk(), 250, sorted(haul), new Owner()));
		assertEquals(0, haul.asked);
	}
}
