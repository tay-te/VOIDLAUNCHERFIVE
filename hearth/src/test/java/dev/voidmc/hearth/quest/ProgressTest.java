package dev.voidmc.hearth.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestReward;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.TurnIn;
import dev.voidmc.hearth.quest.service.Rewards;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProgressTest {
	@BeforeAll
	static void boot() {
		QuestTestKit.bootstrap();
	}

	private static QuestInstance quest(TurnIn turnIn, boolean sequential, Objective... objectives) {
		return new QuestInstance(UUID.randomUUID(), Identifier.parse("test:q"), QuestType.BOUNTY, QuestStatus.ACTIVE,
			new QuestInstance.Text("T", "", "", "", ""), new QuestInstance.Giver(Optional.empty(), "the council", ""), 1L, "Town", BlockPos.ZERO,
			List.of(objectives), new QuestReward(40, List.of(), 3, "40 bits"), 40, new QuestInstance.Timing(1, 4, 2, 1000, 1000 + 2 * 24000),
			new QuestInstance.Rules(sequential, turnIn, -1000, false), List.of());
	}

	private static QuestProgress.Kill kill(String id, boolean monster, BlockPos pos, boolean night) {
		return new QuestProgress.Kill(id, tag -> tag.equals("minecraft:skeletons") && id.contains("skeleton"), monster, pos, night);
	}

	@Test
	void killsCountByTargetPlaceAndTime() {
		Objective o = new Objective(ObjectiveKind.KILL, "", "monster", 3, 0, BlockPos.ZERO, 64, 1L, null, new CompoundTag());
		o.data.putBoolean(Objective.Keys.NIGHT, true);
		QuestInstance q = quest(TurnIn.GIVER, false, o);
		o = q.objectives.getFirst();
		assertFalse(QuestProgress.onKill(q, kill("minecraft:zombie", true, new BlockPos(10, 64, 10), false)), "not at night");
		assertFalse(QuestProgress.onKill(q, kill("minecraft:zombie", true, new BlockPos(500, 64, 10), true)), "too far from town");
		assertFalse(QuestProgress.onKill(q, kill("minecraft:cow", false, new BlockPos(10, 64, 10), true)), "not a monster");
		assertTrue(QuestProgress.onKill(q, kill("minecraft:zombie", true, new BlockPos(10, 64, 10), true)));
		assertTrue(QuestProgress.onKill(q, kill("minecraft:skeleton", true, new BlockPos(-30, 64, 20), true)));
		assertTrue(QuestProgress.onKill(q, kill("minecraft:spider", true, new BlockPos(0, 64, -40), true)));
		assertTrue(o.done());
		assertFalse(QuestProgress.onKill(q, kill("minecraft:spider", true, BlockPos.ZERO, true)), "no progress past the count");
		assertEquals(QuestStatus.READY, QuestProgress.settle(q));
	}

	@Test
	void killTargetsMatchIdsAndTags() {
		QuestProgress.Kill skeleton = kill("minecraft:skeleton", true, BlockPos.ZERO, true);
		assertTrue(QuestProgress.matches("minecraft:skeleton", skeleton));
		assertTrue(QuestProgress.matches("#minecraft:skeletons", skeleton));
		assertFalse(QuestProgress.matches("minecraft:stray", skeleton));
		assertTrue(QuestProgress.matches("monster", skeleton));
	}

	@Test
	void arrivalAtPlacesStructuresAndBiomes() {
		Objective reach = new Objective(ObjectiveKind.REACH, "", "", 1, 0, new BlockPos(100, Objective.SURFACE_Y, 50), 5, 1L, null, new CompoundTag());
		Objective biome = new Objective(ObjectiveKind.BIOME, "", "expanse:realm_amber,expanse:realm_mere", 1, 0, null, 0, -1L, null, new CompoundTag());
		QuestInstance q = quest(TurnIn.GIVER, false, reach, biome);
		reach = q.objectives.get(0);
		biome = q.objectives.get(1);
		assertTrue(QuestProgress.onPosition(q, new BlockPos(90, 70, 50), b -> false).isEmpty());
		List<Objective> done = QuestProgress.onPosition(q, new BlockPos(103, 200, 52), b -> false);
		assertEquals(List.of(reach), done, "surface targets ignore height");
		assertTrue(QuestProgress.onPosition(q, new BlockPos(0, -40, 0), b -> b.equals("minecraft:plains")).isEmpty());
		assertEquals(List.of(biome), QuestProgress.onPosition(q, new BlockPos(0, -40, 0), b -> b.equals("expanse:realm_mere")));
		assertEquals(QuestStatus.READY, QuestProgress.settle(q));
	}

	@Test
	void sequentialObjectivesOpenInOrder() {
		Objective find = new Objective(ObjectiveKind.REACH, "", "", 1, 0, new BlockPos(40, Objective.SURFACE_Y, 0), 4, 1L, null, new CompoundTag());
		Objective bring = Objective.of(ObjectiveKind.DELIVER, "", "minecraft:gold_nugget", 1);
		QuestInstance q = quest(TurnIn.GIVER, true, find, bring);
		find = q.objectives.get(0);
		bring = q.objectives.get(1);
		assertEquals(List.of(find), q.currentObjectives());
		GeneratorTest.FakeInventory inv = new GeneratorTest.FakeInventory(java.util.Map.of("minecraft:gold_nugget", 5));
		assertTrue(QuestProgress.handIn(q, inv).isEmpty(), "cannot hand in before the first step is done");
		QuestProgress.onPosition(q, new BlockPos(41, 64, 1), b -> false);
		assertEquals(List.of(bring), q.currentObjectives());
		assertEquals(1, QuestProgress.handIn(q, inv).size());
		assertEquals(QuestStatus.READY, QuestProgress.settle(q));
	}

	@Test
	void autoTurnInCompletesAtOnce() {
		Objective o = new Objective(ObjectiveKind.REACH, "", "", 1, 0, BlockPos.ZERO, 3, 1L, null, new CompoundTag());
		QuestInstance q = quest(TurnIn.AUTO, false, o);
		QuestProgress.onPosition(q, new BlockPos(1, 0, 1), b -> false);
		assertEquals(QuestStatus.COMPLETED, QuestProgress.settle(q));
	}

	@Test
	void deadlinesFailInProgressQuests() {
		QuestInstance q = quest(TurnIn.GIVER, false, Objective.of(ObjectiveKind.KILL, "", "monster", 2));
		assertFalse(QuestProgress.expired(q, 1000 + 24000));
		assertTrue(QuestProgress.expired(q, 1000 + 2 * 24000 + 1));
		q.status = QuestStatus.COMPLETED;
		assertFalse(QuestProgress.expired(q, Long.MAX_VALUE), "a finished quest never expires");
	}

	@Test
	void scoutBandIsMeasuredFromTheOrigin() {
		Objective o = new Objective(ObjectiveKind.SCOUT, "", "", 1, 0, BlockPos.ZERO, 0, 1L, null, new CompoundTag());
		o.data.putInt(Objective.Keys.MIN_DISTANCE, 600);
		o.data.putInt(Objective.Keys.MAX_DISTANCE, 2500);
		assertFalse(QuestProgress.inScoutBand(o, new BlockPos(300, 64, 300)));
		assertTrue(QuestProgress.inScoutBand(o, new BlockPos(800, 64, 0)));
		assertFalse(QuestProgress.inScoutBand(o, new BlockPos(3000, 64, 0)));
	}

	@Test
	void rewardsFallBackToEmeraldsAndEscrowComesFromTheTreasury() {
		assertEquals(0, Rewards.emeralds(0, 20));
		assertEquals(1, Rewards.emeralds(3, 20), "any coin is worth at least an emerald");
		assertEquals(5, Rewards.emeralds(100, 20));
		assertEquals(13, Rewards.emeralds(250, 20));

		SimpleSettlement s = new SimpleSettlement(1, "Town", Culture.TEMPERATE, BlockPos.ZERO, 0);
		s.deposit(100);
		assertEquals(60, Rewards.escrow(s, 60));
		assertEquals(40, s.treasury());
		assertEquals(40, Rewards.escrow(s, 60), "only what is left");
		assertEquals(0, s.treasury());
		QuestInstance q = quest(TurnIn.GIVER, false, Objective.of(ObjectiveKind.KILL, "", "monster", 1));
		q.escrow = 40;
		Rewards.refund(s, q);
		assertEquals(40, s.treasury(), "a failed quest's coin goes home");
		assertEquals(0, q.escrow);
	}

	@Test
	void playerAndSettlementDataRoundTrip() {
		Objective o = new Objective(ObjectiveKind.EXPLORE, "Find it", "expanse:karst_pagoda", 1, 0, new BlockPos(5, Objective.SURFACE_Y, 7), 8, -1L,
			UUID.randomUUID(), new CompoundTag());
		o.data.putString(Objective.Keys.TARGET_NAME, "the Karst Temple");
		QuestInstance q = quest(TurnIn.GIVER, true, o);
		PlayerQuests pq = new PlayerQuests();
		pq.active.add(q);
		pq.tracked = q.id;
		QuestInstance done = quest(TurnIn.GIVER, false, Objective.of(ObjectiveKind.KILL, "", "monster", 1));
		pq.active.add(done);
		pq.finish(done, QuestStatus.COMPLETED, 12);

		Tag tag = PlayerQuests.CODEC.encodeStart(NbtOps.INSTANCE, pq).getOrThrow();
		PlayerQuests back = PlayerQuests.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		assertEquals(1, back.active.size());
		assertEquals(q.id, back.tracked);
		assertEquals(Objective.SURFACE_Y, back.active.getFirst().objectives.getFirst().pos.getY(), "a surface marker survives saving");
		assertEquals("the Karst Temple", back.active.getFirst().objectives.getFirst().data.getStringOr(Objective.Keys.TARGET_NAME, ""));
		assertEquals(1, back.completed(Identifier.parse("test:q")));
		assertEquals(QuestStatus.COMPLETED, back.history.getFirst().outcome());

		SettlementQuests data = new SettlementQuests();
		data.offers.add(q);
		data.addNeed(new SettlementQuests.StalledNeed(2, dev.voidmc.hearth.api.Goods.WOOD, 30, 4, "bridge"));
		data.pressure = 7;
		var json = SettlementQuests.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		SettlementQuests again = SettlementQuests.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1, again.offers.size());
		assertEquals(7, again.pressure);
		assertEquals("bridge", again.needs.getFirst().place());
		again.satisfy(2, dev.voidmc.hearth.api.Goods.WOOD, 30);
		assertTrue(again.needs.isEmpty(), "a met need is dropped");
		assertNull(again.offer(UUID.randomUUID()));
	}
}
