package dev.voidmc.hearth.client;

import dev.voidmc.hearth.client.economy.EconomyClient;
import dev.voidmc.hearth.client.npc.NpcClient;
import dev.voidmc.hearth.client.quest.QuestClient;
import dev.voidmc.hearth.client.sim.SimClient;
import net.fabricmc.api.ClientModInitializer;

public class HearthClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SimClient.init();       // settlement banners on arrival, debug overlay
		NpcClient.init();       // hearthfolk rendering and skins, the conversation screen
		EconomyClient.init();   // trade and bank screens, coin tooltips
		QuestClient.init();     // the journal, quest markers and waypoints
	}
}
