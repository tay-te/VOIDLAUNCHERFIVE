package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import dev.voidmc.mettle.character.skill.athletics.Movement;
import dev.voidmc.mettle.character.skill.athletics.OxCarry;
import dev.voidmc.mettle.character.skill.athletics.Scramble;
import dev.voidmc.mettle.character.skill.athletics.Trail;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Athletics tree (TREES.md §8): XP from metres sprinted, swum and climbed ({@code athletics.AthleticsXp}), and every
 * Athletics perk. Plain numbers are data ({@code effects} in the perk files: Pack Mule, Beast of Burden and Ox
 * capacity, Swimmer's breath, Soft Landing, Ironlung, Mountain Goat); load-band perks are {@code BandModifier}s
 * registered here; movement perks follow the sprint on both sides ({@code athletics.Movement}).
 */
public final class AthleticsSkill {
	public static final ResourceKey<Skill> ATHLETICS = Progress.skill("athletics");

	public static final Identifier PACK_MULE = Mettle.id("athletics/pack_mule");
	public static final Identifier RUNNER = Mettle.id("athletics/runner");
	public static final Identifier STRONG_BACK = Mettle.id("athletics/strong_back");
	public static final Identifier STEADY_PACE = Mettle.id("athletics/steady_pace");
	public static final Identifier BEAST_OF_BURDEN = Mettle.id("athletics/beast_of_burden");
	public static final Identifier HAULERS_STRIDE = Mettle.id("athletics/haulers_stride");
	public static final Identifier SWIMMER = Mettle.id("athletics/swimmer");
	public static final Identifier SOFT_LANDING = Mettle.id("athletics/soft_landing");
	public static final Identifier VAULTER = Mettle.id("athletics/vaulter");
	public static final Identifier SPRING = Mettle.id("athletics/spring");
	public static final Identifier OX = Mettle.id("athletics/ox");
	public static final Identifier MARATHON = Mettle.id("athletics/marathon");
	public static final Identifier FREERUNNER = Mettle.id("athletics/freerunner");
	public static final Identifier IRONLUNG = Mettle.id("athletics/ironlung");
	public static final Identifier MOUNTAIN_GOAT = Mettle.id("athletics/mountain_goat");
	public static final Identifier COURIERS_STRIDE = Mettle.id("athletics/couriers_stride");

	/** Set while Steady Pace asks burden for the load, so a burden that asks for the rules again can't loop. */
	private static final ThreadLocal<Boolean> ASKING_LOAD = ThreadLocal.withInitial(() -> false);

	private AthleticsSkill() {
	}

	public static void init() {
		MettleServices.addBandModifier((player, rules) -> {
			boolean strongBack = Perks.has(player, STRONG_BACK);
			boolean beast = Perks.has(player, BEAST_OF_BURDEN);
			boolean ox = Perks.has(player, OX);
			boolean steadyPace = Perks.has(player, STEADY_PACE);
			if (!strongBack && !beast && !ox && !steadyPace) {
				return rules;
			}
			return AthleticsRules.bands(rules, strongBack, beast, ox, steadyPace, steadyPace ? loadRatio(player) : 0f);
		});
		Scramble.init();
		UseBlockCallback.EVENT.register(OxCarry::use);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Movement.tick(player);
				Scramble.tick(player);
				if ((tick + player.getId()) % 40 == 0) {
					Trail.check(player);
				}
			}
		});
	}

	private static float loadRatio(Player player) {
		if (ASKING_LOAD.get()) {
			return 0f;
		}
		ASKING_LOAD.set(true);
		try {
			float capacity = MettleServices.burden().capacity(player);
			return capacity <= 0 ? 0f : MettleServices.burden().load(player) / capacity;
		} finally {
			ASKING_LOAD.set(false);
		}
	}

	/** A feedback chip, {@code mettle.feedback.athletics.<key>}. */
	public static void chip(ServerPlayer player, Identifier perk, String key, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback.athletics." + key, args));
	}
}
