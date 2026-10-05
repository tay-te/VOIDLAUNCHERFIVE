package dev.voidmc.mettle.character.signature;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * The eight signature perks (CHARACTER.md §1). Each belongs to one background; a player "has" it while their character
 * has that background. Event-driven ones live here; the rest are small mixins in {@code mixin.character} that call
 * {@link #has}. Every trigger sends a {@link MettlePayloads.Feedback} so the HUD can say what happened.
 */
public final class SignaturePerks {
	public static final Identifier RALLY = Mettle.id("rally");
	public static final Identifier FIELD_DRESSING = Mettle.id("field_dressing");
	public static final Identifier BACKSTAB = Mettle.id("backstab");
	public static final Identifier SMITHS_EYE = Mettle.id("smiths_eye");
	public static final Identifier HERB_LORE = Mettle.id("herb_lore");
	public static final Identifier COIN_SENSE = Mettle.id("coin_sense");
	public static final Identifier WELL_READ = Mettle.id("well_read");
	public static final Identifier GREEN_THUMB = Mettle.id("green_thumb");

	/** Rally heals at most once per this many ticks. */
	public static final int RALLY_COOLDOWN = 200;
	public static final float RALLY_HEAL = 2.0f;
	public static final double GREEN_THUMB_CHANCE = 0.25;
	public static final float HERB_LORE_DURATION = 1.5f;

	/** Marks a potion brewed with Herb Lore, so it is extended once and its tooltip can say so. */
	public static final DataComponentType<Unit> HERB_LORE_BREWED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mettle.id("herb_lore"),
		DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());

	/** On a brewing stand: whether the last player to open it had Herb Lore. */
	public static final AttachmentType<Boolean> BREWER_HERB_LORE = AttachmentRegistry.create(Mettle.id("character/brewer_herb_lore"), b -> b
		.persistent(Codec.BOOL));

	private static final Map<UUID, Long> RALLY_READY_AT = new HashMap<>();

	private SignaturePerks() {
	}

	public static void init() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(SignaturePerks::afterKill);
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
	}

	/** Whether the player's background carries this signature perk. Works on both sides (the background is synced). */
	public static boolean has(Player player, Identifier perk) {
		Background background = CharacterStore.background(player.level().registryAccess(), CharacterStore.get(player));
		return background != null && background.signature().map(s -> s.perk().equals(perk)).orElse(false);
	}

	public static void feedback(ServerPlayer player, Identifier perk, Component text) {
		ServerPlayNetworking.send(player, new MettlePayloads.Feedback(perk, text));
	}

	/** Ticks until Rally can heal again (0 when ready), for the overlay's live status. */
	public static int rallyCooldown(ServerPlayer player) {
		long readyAt = RALLY_READY_AT.getOrDefault(player.getUUID(), 0L);
		return (int) Math.max(0, readyAt - player.level().getGameTime());
	}

	private static void afterKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (!(killer instanceof ServerPlayer player)) {
			return;
		}
		// Rally (Soldier): a hostile kill heals a heart, once every ten seconds.
		if (killed instanceof Enemy && has(player, RALLY) && player.getHealth() < player.getMaxHealth()) {
			long now = level.getGameTime();
			if (now >= RALLY_READY_AT.getOrDefault(player.getUUID(), 0L)) {
				RALLY_READY_AT.put(player.getUUID(), now + RALLY_COOLDOWN);
				player.heal(RALLY_HEAL);
				feedback(player, RALLY, Component.translatable("mettle.feedback.rally"));
			}
		}
		// Field Dressing (Ranger): an animal you kill drops its loot a second time (more meat and hide), never for babies.
		if (killed instanceof Animal animal && !animal.isBaby() && has(player, FIELD_DRESSING)) {
			animal.getLootTable().ifPresent(table -> {
				animal.dropFromLootTable(level, source, true, table);
				feedback(player, FIELD_DRESSING, Component.translatable("mettle.feedback.field_dressing"));
			});
		}
	}

	/** Forgets cooldowns of players who left (called on disconnect). */
	public static void forget(UUID player) {
		RALLY_READY_AT.remove(player);
	}
}
