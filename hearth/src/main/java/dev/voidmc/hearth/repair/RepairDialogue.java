package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Talking to a builder: a player whose house in town is damaged can have it mended ("Mend my house — 4 marks, 2 bits"),
 * and anyone can ask what needs mending.
 */
final class RepairDialogue implements DialogueProvider {
	private static final String MEND = "repair:mend_my_house";
	private static final String NEWS = "repair:what_needs_mending";

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		RepairRuntime rt = RepairRuntime.current();
		HearthWorld world = rt == null ? null : rt.worldOf(settlement);
		if (world == null || resident.job != JobType.BUILDER || !RepairModule.config().tracking().enabled()) {
			return;
		}
		OwnerRepairs.Quote quote = OwnerRepairs.quote(rt, world, settlement, player.getUUID());
		if (!quote.empty()) {
			EconomyService economy = HearthServices.economy();
			Component price = economy != null ? economy.format(quote.bits()) : Component.literal(quote.bits() + "");
			out.add(new Option(MEND, Component.translatable("hearth.repair.dialogue.mend", quote.blocks(), price), 150));
		}
		if (!settlement.data(RepairModule.QUEUE).isEmpty()) {
			out.add(new Option(NEWS, Component.translatable("hearth.repair.dialogue.news"), 40));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		RepairRuntime rt = RepairRuntime.current();
		HearthWorld world = rt == null ? null : rt.worldOf(settlement);
		if (world == null) {
			return false;
		}
		switch (optionId) {
			case MEND -> {
				if (!OwnerRepairs.pay(rt, world, settlement, player)) {
					player.sendSystemMessage(Component.translatable("hearth.repair.dialogue.cannot_pay"));
					return false;
				}
				player.sendSystemMessage(Component.translatable("hearth.repair.dialogue.paid", resident.givenName));
				return true;
			}
			case NEWS -> {
				int n = settlement.data(RepairModule.QUEUE).size();
				FireWatch.Episode fire = rt.fires.episode(settlement.id());
				player.sendSystemMessage(fire != null ? Component.translatable("hearth.repair.dialogue.news.fire", resident.givenName)
					: Component.translatable("hearth.repair.dialogue.news.blocks", resident.givenName, n));
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
