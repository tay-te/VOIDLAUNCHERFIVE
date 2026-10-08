package dev.voidmc.mettle.compat.hearth;

import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Mettle's answer to Hearth's {@link PlayerSkills} slot: the numbers Hearth's speech, haggling, lockpicking, pickpocketing
 * and stealth read, and the door Hearth reports XP through. Keys: skills by id ({@code mettle:speech}; a bare
 * {@code speech} is taken as Mettle's), attributes by name ({@code presence}). Server thread.
 */
final class MettlePlayerSkills implements PlayerSkills {
	@Override
	public int level(ServerPlayer player, String skill) {
		ResourceKey<Skill> key = skillKey(player, skill);
		return key == null ? UNKNOWN_LEVEL : CharacterStore.get(player).level(key);
	}

	@Override
	public int attribute(ServerPlayer player, String attribute) {
		CharacterAttribute a = attribute(attribute);
		if (a == null) {
			return UNKNOWN_ATTRIBUTE;
		}
		CharacterState state = CharacterStore.get(player);
		return state.attribute(a, CharacterStore.background(player.level().registryAccess(), state));
	}

	/** A perk held (bought or earned), or the signature perk of the player's background ({@code mettle:coin_sense}). */
	@Override
	public boolean hasPerk(ServerPlayer player, Identifier perk) {
		return Perks.has(player, perk) || SignaturePerks.has(player, perk);
	}

	/** Odds by {@link HearthRules#checkOdds} from the skill's level and its governing attribute; rolled now. No XP: Hearth awards it. */
	@Override
	public Check check(ServerPlayer player, String skill, int difficulty) {
		return new Check(odds(player, skill, difficulty), player.getRandom().nextDouble());
	}

	static double odds(ServerPlayer player, String skill, int difficulty) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null) {
			return HearthRules.checkOdds(UNKNOWN_LEVEL, UNKNOWN_ATTRIBUTE, difficulty);
		}
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);
		Skill s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getValueOrThrow(key);
		return HearthRules.checkOdds(state.level(key), state.attribute(s.attribute(), background), difficulty);
	}

	/**
	 * Hearth's XP for a passed check, a struck haggle, a picked lock... through {@link Progress#award}, so the favoured and
	 * Wits multipliers and every {@code MODIFY_XP} listener apply. Unreleased skills (Speech, Thievery, Stealth) bank it
	 * for when their trees ship.
	 */
	@Override
	public void award(ServerPlayer player, String skill, float xp, String reason) {
		ResourceKey<Skill> key = skillKey(player, skill);
		if (key == null || !(xp > 0)) {
			return;
		}
		Mettle.LOGGER.debug("Hearth awards {} {} XP in {} ({})", player.getName().getString(), xp, key.identifier(), reason);
		Progress.award(player, key, xp);
	}

	/** A registered Mettle skill by id, or null. */
	static @Nullable ResourceKey<Skill> skillKey(ServerPlayer player, String skill) {
		Identifier id = skill.indexOf(':') < 0 ? Mettle.id(skill) : Identifier.tryParse(skill);
		if (id == null) {
			return null;
		}
		ResourceKey<Skill> key = ResourceKey.create(MettleRegistries.SKILL, id);
		Optional<Skill> s = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(key);
		return s.isPresent() ? key : null;
	}

	private static @Nullable CharacterAttribute attribute(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		int colon = n.indexOf(':');
		if (colon >= 0) {
			n = n.substring(colon + 1);
		}
		for (CharacterAttribute a : CharacterAttribute.values()) {
			if (a.getSerializedName().equals(n)) {
				return a;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return "VOID Mettle";
	}
}
