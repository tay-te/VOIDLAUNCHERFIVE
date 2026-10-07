package dev.voidmc.hearth.client;

import dev.voidmc.hearth.client.economy.EconomyClient;
import dev.voidmc.hearth.client.law.LawClient;
import dev.voidmc.hearth.client.map.MapClient;
import dev.voidmc.hearth.client.npc.NpcClient;
import dev.voidmc.hearth.client.quest.QuestClient;
import dev.voidmc.hearth.client.repair.RepairClient;
import dev.voidmc.hearth.client.sim.SimClient;
import dev.voidmc.hearth.client.social.SocialClient;
import dev.voidmc.hearth.client.standing.StandingClient;
import net.fabricmc.api.ClientModInitializer;

public class HearthClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SimClient.init();       // settlement banners on arrival, debug overlay
		NpcClient.init();       // hearthfolk rendering and skins, the conversation screen
		EconomyClient.init();   // trade and bank screens, coin tooltips
		QuestClient.init();     // the journal, quest markers and waypoints
		StandingClient.init();  // standing and titles in the HUD and screens
		LawClient.init();       // ownership hints, wanted status, the lockpicking and pickpocketing minigames
		RepairClient.init();    // damage and repair feedback
		SocialClient.init();    // dice and other tavern games, services
		MapClient.init();       // settlements and the tracked quest for VOID Atlas's map, when it is installed
	}
}
