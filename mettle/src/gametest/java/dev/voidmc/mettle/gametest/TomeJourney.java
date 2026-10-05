package dev.voidmc.mettle.gametest;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Tomes;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Teaching tomes: reading one teaches its perk, a known one is refused, and they turn up in their chests. */
final class TomeJourney {
	private TomeJourney() {
	}

	static void run(TestSingleplayerContext world) {
		String problem = world.getServer().computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			Identifier id = Mettle.id("mining/stonesense");
			ResourceKey<Perk> key = Tomes.perk(id);
			// Forget it, in case a journey earned it.
			CharacterState state = CharacterStore.get(player);
			Map<ResourceKey<Perk>, Integer> perks = new HashMap<>(state.perks());
			perks.remove(key);
			CharacterStore.set(player, state.withPerks(perks));

			ItemStack tome = Tomes.of(id);
			ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
			player.setItemInHand(InteractionHand.MAIN_HAND, tome);
			try {
				InteractionResult first = tome.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
				player.stopUsingItem();
				if (!first.consumesAction()) {
					return "an unread tome did not start reading: " + first;
				}
				ItemStack after = tome.getItem().finishUsingItem(tome, player.level(), player);
				if (!Perks.has(player, id)) {
					return "reading The Delver's Almanac did not teach Stonesense";
				}
				if (after.isEmpty() || !after.has(Tomes.TEACHES)) {
					return "the tome was used up; it should be kept";
				}
				InteractionResult again = tome.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
				if (again != InteractionResult.FAIL || player.isUsingItem()) {
					return "a tome already learned started reading again: " + again;
				}
			} finally {
				player.stopUsingItem();
				player.setItemInHand(InteractionHand.MAIN_HAND, held);
			}

			// Without Expanse, the Citadel Drill lies in igloo chests, about one in twelve.
			LootTable igloo = s.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/igloo_chest")));
			int found = 0;
			int rolls = 600;
			for (int i = 0; i < rolls; i++) {
				LootParams params = new LootParams.Builder(player.level()).withParameter(LootContextParams.ORIGIN, player.position()).create(LootContextParamSets.CHEST);
				for (ItemStack stack : igloo.getRandomItems(params)) {
					if (Mettle.id("defense/roll_with_the_blow").equals(stack.get(Tomes.TEACHES))) {
						found++;
					}
				}
			}
			if (found < 20 || found > 80) {
				return "igloo chests held " + found + " Citadel Drills in " + rolls + ", expected about " + (int) (rolls * Tomes.VANILLA_CHANCE);
			}
			return null;
		});
		if (problem != null) {
			throw new AssertionError(problem);
		}
	}
}
