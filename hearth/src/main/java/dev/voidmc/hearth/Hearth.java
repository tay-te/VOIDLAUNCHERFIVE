package dev.voidmc.hearth;

import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.economy.EconomyModule;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.quest.QuestModule;
import dev.voidmc.hearth.sim.SimModule;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Hearth: living settlements. Each module owns one package and registers what it provides in
 * {@link dev.voidmc.hearth.api.HearthServices}; see docs/ARCHITECTURE.md for who owns what.
 */
public class Hearth implements ModInitializer {
	public static final String MOD_ID = "hearth";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BuildModule.init();     // blueprints: styles, archetypes, the building generators
		PlanModule.init();      // the settlement planner: layouts, roads, plots, growth
		SimModule.init();       // settlements: records, persistence, the simulation clock, worldgen and chunk application
		NpcModule.init();       // hearthfolk: residents embodied as entities, routines, jobs, conversation
		EconomyModule.init();   // coin, mints, markets, treasuries
		QuestModule.init();     // quests, notice boards, the journal, reputation
		HearthTab.init();       // last: every module has added its items by now
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
