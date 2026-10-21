package dev.voidmc.fray.combat;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.combat.action.Actions;
import dev.voidmc.fray.combat.net.CombatHandshake;
import dev.voidmc.fray.combat.net.CombatPayloads;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Fray's combat systems (stage 1B): the damage pipeline, statuses and build-up, stamina and poise, threat, the player's
 * actions, encounters, telegraphs, the combat handshake. Called from Fray's init; server-authoritative and free of
 * client classes (the dedicated server runs it as is).
 */
public final class FrayCombat {
	/** Vanilla creatures' poise (BESTIARY.md profiles; Bestiary overrides per creature). Others: 20. */
	private static final Map<EntityType<?>, Double> POISE = Map.of(EntityTypes.SKELETON, 15.0, EntityTypes.SPIDER, 15.0, EntityTypes.WOLF, 12.0,
		EntityTypes.IRON_GOLEM, 200.0, EntityTypes.RAVAGER, 120.0, EntityTypes.WARDEN, 500.0, EntityTypes.CREEPER, 10.0, EntityTypes.ENDERMAN, 30.0,
		EntityTypes.PIG, 8.0);

	private FrayCombat() {
	}

	public static void init() {
		FrayAttributes.init();
		DamageSplit.init();
		FabricDefaultAttributeRegistry.MODIFY.register(context -> {
			context.modify(type -> true, (type, builder) -> {
				builder.add(FrayAttributes.POISE, POISE.getOrDefault(type, 20.0));
				FrayAttributes.RESIST.values().forEach(builder::add);
				FrayAttributes.STATUS_RESIST.values().forEach(builder::add);
			});
			context.modify(EntityTypes.PLAYER, (type, builder) -> {
				builder.add(FrayAttributes.MAX_STAMINA).add(FrayAttributes.STAMINA_REGEN).add(FrayAttributes.MAX_MANA).add(FrayAttributes.MANA_REGEN)
					.add(FrayAttributes.SPELL_POWER).add(FrayAttributes.CAST_SPEED).add(FrayAttributes.CRIT_CHANCE).add(FrayAttributes.CRIT_DAMAGE)
					.add(FrayAttributes.MAX_TOXICITY);
			});
		});
		CombatPayloads.register();
		CombatHandshake.init();
		Actions.init();
		DamageProfiles.init();
		MotionBridge.init();
		CombatCommands.init();
		FrayApi.install(Pipeline.INSTANCE, Statuses.INSTANCE, Resources.INSTANCE, Threat.INSTANCE, Actions.INSTANCE, Encounters.INSTANCE, Telegraphs.INSTANCE,
			Profiles.INSTANCE, Defense.INSTANCE);

		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			Defense.INSTANCE.tick(level);
			Statuses.tickLevel(level);
			Encounters.INSTANCE.tick(level);
		});
		// Every player has combat state from the start (stamina, the HUD's first numbers).
		ServerTickEvents.START_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 20 == 0) {
				for (ServerPlayer p : level.players()) {
					CombatState.of(p);
				}
			}
		});
		EntityTrackingEvents.START_TRACKING.register(CombatSync::startTracking);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source instanceof FrayDamageSource || taken <= 0) {
				return;
			}
			if (entity instanceof Mob m && source.getEntity() instanceof LivingEntity attacker && attacker != m) {
				Threat.INSTANCE.add(m, attacker, taken, Fray.id("damage"));
			}
			if (entity instanceof ServerPlayer p && source.getEntity() != null) {
				CombatState.of(p).player.tagCombat(100);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob m && Encounters.INSTANCE.of(m) != null) {
				Encounters.INSTANCE.killed(m, source.getEntity() instanceof ServerPlayer p ? p : null);
			}
			if (!(source instanceof FrayDamageSource) && source.getEntity() != null && Profiles.INSTANCE.rank(entity) != Rank.COMMON) {
				dev.voidmc.fray.api.CombatEvents.KILLED.invoker().killed(source.getEntity(), entity, null, Profiles.INSTANCE.rank(entity));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Statuses.clearAll();
			Encounters.INSTANCE.clear();
			Defense.INSTANCE.clear();
			Telegraphs.INSTANCE.clear();
			MettleStats.clearCache();
		});
		Fray.LOGGER.info("Fray combat: api {}, protocol {}, movesets {}", FrayApi.API, FrayApi.PROTOCOL, CombatHandshake.contentHash());
	}
}
