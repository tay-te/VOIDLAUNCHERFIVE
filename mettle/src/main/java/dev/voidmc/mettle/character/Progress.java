package dev.voidmc.mettle.character;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Skill XP in, levels out (CHARACTER.md §3.1). Applies the favoured and Wits multipliers, announces level-ups (and the
 * tier they open), character levels and synergies, and batches the "+12 Mining" chips to one per skill per second.
 */
public final class Progress {
	public static final double FAVOURED_BONUS = 0.25;
	public static final double WITS_PER_POINT = 0.02;

	private static final Map<UUID, Map<ResourceKey<Skill>, Double>> PENDING_CHIPS = new HashMap<>();

	private Progress() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0 || PENDING_CHIPS.isEmpty()) {
				return;
			}
			PENDING_CHIPS.forEach((uuid, gains) -> {
				ServerPlayer player = server.getPlayerList().getPlayer(uuid);
				if (player == null) {
					return;
				}
				gains.forEach((skill, xp) -> {
					Skill s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValue(skill);
					if (s != null && xp >= 1) {
						SignaturePerks.feedback(player, Mettle.id("xp/" + skill.identifier().getPath()),
							Component.translatable("mettle.feedback.xp", (int) Math.round(xp), s.name()));
					}
				});
			});
			PENDING_CHIPS.clear();
		});
	}

	/** The multiplier on a skill's XP for this character. */
	public static double multiplier(CharacterState state, Background background, ResourceKey<Skill> skill) {
		double m = 1.0;
		if (state.favouredSkills(background).contains(skill)) {
			m += FAVOURED_BONUS;
		}
		m += WITS_PER_POINT * (state.attribute(CharacterAttribute.WITS, background) - CharacterAttribute.BASE);
		// Perks that speed learning: Jack of All Trades (skills below 10).
		m *= dev.voidmc.mettle.character.skill.survival.GeneralFeats.jackBonus(state, skill);
		return Math.max(0.1, m);
	}

	/** Awards XP after the multipliers. {@code base} is BALANCE.md §3's number for the action. */
	public static void award(ServerPlayer player, ResourceKey<Skill> skill, double base) {
		if (base <= 0 || player.isSpectator()) {
			return;
		}
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		double gained = MettleEvents.MODIFY_XP.invoker().modify(player, skill, base * multiplier(state, background, skill));
		if (gained <= 0) {
			return;
		}
		int levelBefore = state.level(skill);
		int characterBefore = state.characterLevel();
		CharacterState next = state.withXp(skill, state.xp(skill) + gained);
		CharacterStore.set(player, next);
		PENDING_CHIPS.computeIfAbsent(player.getUUID(), u -> new LinkedHashMap<>()).merge(skill, gained, Double::sum);
		MettleEvents.XP_GAINED.invoker().gained(player, skill, gained);

		int levelAfter = next.level(skill);
		if (levelAfter > levelBefore) {
			Skill s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValueOrThrow(skill);
			Component text = Component.translatable("mettle.feedback.level", s.name(), levelAfter);
			for (int tier = 1; tier <= 4; tier++) {
				if (levelBefore < Curves.tierSkillGate(tier) && levelAfter >= Curves.tierSkillGate(tier)) {
					text = Component.translatable("mettle.feedback.level_tier", s.name(), levelAfter, tierName(tier));
				}
			}
			SignaturePerks.feedback(player, Mettle.id("level/" + skill.identifier().getPath()), text);
			MettleEvents.SKILL_LEVEL_UP.invoker().levelUp(player, skill, levelBefore, levelAfter);
			Perks.checkSynergies(player);
		}
		int characterAfter = next.characterLevel();
		if (characterAfter > characterBefore) {
			SignaturePerks.feedback(player, Mettle.id("level/character"), Component.translatable("mettle.feedback.character_level", characterAfter));
			MettleEvents.CHARACTER_LEVEL_UP.invoker().levelUp(player, characterBefore, characterAfter);
		}
	}

	private static String tierName(int tier) {
		return switch (tier) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> "IV";
		};
	}

	/** Shorthand for skills by path, e.g. {@code skill("mining")}. */
	public static ResourceKey<Skill> skill(String path) {
		return ResourceKey.create(MettleRegistries.SKILL, Mettle.id(path));
	}

	public static ResourceKey<Perk> perk(String path) {
		return ResourceKey.create(MettleRegistries.PERK, Mettle.id(path));
	}

	public static Identifier perkId(String path) {
		return Mettle.id(path);
	}

	public static void forget(UUID player) {
		PENDING_CHIPS.remove(player);
	}
}
