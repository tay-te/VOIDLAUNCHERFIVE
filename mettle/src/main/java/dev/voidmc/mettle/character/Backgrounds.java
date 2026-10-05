package dev.voidmc.mettle.character;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.EffectsApplier;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Choosing, applying and resetting a background. All on the server thread. */
public final class Backgrounds {
	private Backgrounds() {
	}

	/**
	 * Handles a player's "begin as": checks the request ({@link BackgroundRules}), records the character, gives the kit
	 * and applies the attributes, then tells the client how it went.
	 */
	public static boolean choose(ServerPlayer player, MettlePayloads.ChooseBackground request) {
		BackgroundRules.Result result = evaluate(player, request);
		if (!result.ok()) {
			ServerPlayNetworking.send(player, new MettlePayloads.BackgroundRejected(result.rejected().get()));
			return false;
		}
		Background background = player.level().registryAccess().lookupOrThrow(MettleRegistries.BACKGROUND).getValueOrThrow(request.background());
		CharacterStore.set(player, CharacterStore.get(player).withBackground(Optional.of(request.background()), result.spent(), result.favoured()));
		giveKit(player, background);
		EffectsApplier.apply(player);
		ServerPlayNetworking.send(player, new MettlePayloads.BackgroundChosen(request.background()));
		Mettle.LOGGER.info("{} began as {}", player.getName().getString(), request.background().identifier());
		return true;
	}

	private static BackgroundRules.Result evaluate(ServerPlayer player, MettlePayloads.ChooseBackground request) {
		if (CharacterStore.get(player).hasBackground()) {
			return BackgroundRules.Result.reject(BackgroundRules.Reason.ALREADY_CHOSEN);
		}
		Optional<Background> background = player.level().registryAccess().lookupOrThrow(MettleRegistries.BACKGROUND).getOptional(request.background());
		if (background.isEmpty()) {
			return BackgroundRules.Result.reject(BackgroundRules.Reason.UNKNOWN_BACKGROUND);
		}
		Registry<Skill> skills = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL);
		return BackgroundRules.validate(background.get(), request.spent(), request.favoured(), skills::getOptional);
	}

	/** Clears the background (an op's reset): bought perks are refunded, skill XP and earned perks stay, the picker opens. */
	public static void reset(ServerPlayer player) {
		CharacterStore.set(player, CharacterStore.get(player).withBackground(Optional.empty(), Map.of(), List.of()));
		Perks.respec(player, false);
		EffectsApplier.apply(player);
		ServerPlayNetworking.send(player, new MettlePayloads.OpenPicker(true));
	}

	/** Sets a background directly, without kit or validation (commands and tests). */
	public static void force(ServerPlayer player, ResourceKey<Background> key) {
		CharacterStore.set(player, CharacterStore.get(player).withBackground(Optional.of(key), Map.of(), List.of()));
		EffectsApplier.apply(player);
	}

	private static void giveKit(ServerPlayer player, Background background) {
		for (ItemStackTemplate template : background.kit()) {
			ItemStack stack = template.create();
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
	}
}
