package dev.voidmc.mettle.client.moment;

import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.PerkText;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.moment.MomentKind;
import dev.voidmc.mettle.client.MettleClient;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.client.ui.kit.Ui;
import dev.voidmc.mettle.core.CharacterStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What a moment's toast says, read from the synced character so the points line is current: a lead in the accent
 * colour ("Mining 9"), the rest ("Apprentice Miner"), a small second line ("1 point to spend · press K"), the accent
 * (the skill's hue; champagne for earned perks; Temper for the character) and the icon.
 */
final class MomentText {
	/** Earned perks (feats, synergies, teachings) in every skill (UI.md §1b). */
	static final int CHAMPAGNE = 0xFFE8DDBC;

	record Lines(Component lead, Component rest, Component subtitle, int accent, Identifier icon) {
	}

	private MomentText() {
	}

	static Lines lines(Minecraft mc, MomentKind kind, String skillId, String perkId, int level, int tier, int rank) {
		RegistryAccess registries = mc.getConnection().registryAccess();
		Component key = MettleClient.characterKey.getTranslatedKeyMessage();
		ResourceKey<Skill> skillKey = skillId == null ? null : ResourceKey.create(MettleRegistries.SKILL, Identifier.parse(skillId));
		Skill skill = skillKey == null ? null : registries.lookupOrThrow(MettleRegistries.SKILL).getValue(skillKey);
		int skillAccent = skill == null || skill.colour() == Skill.NO_COLOUR ? Ui.ACCENT : 0xFF000000 | skill.colour();
		Component skillName = skill == null ? Component.literal("?") : skill.name();

		switch (kind) {
			case LEVEL, TIER -> {
				// Drafts waiting in the skill (the level just gained dealt one).
				int points = skillKey == null ? 0 : CharacterStore.get(mc.player).drafts().queued(skillKey);
				Component sub = points > 0
					? Component.translatable(points == 1 ? "mettle.moment.points.one" : "mettle.moment.points", points, key)
					: Component.translatable("mettle.hint.press_k", key);
				Identifier icon = skillKey == null ? null : Skill.icon(skillKey);
				if (kind == MomentKind.TIER) {
					return new Lines(Component.translatable("mettle.moment.tier", roman(tier)), skillName, sub, skillAccent, icon);
				}
				String path = skillKey == null ? "mining" : skillKey.identifier().getPath();
				Component title = Component.translatable("mettle.title." + MettleState.titleBand(level), Component.translatable("mettle.skill." + path + ".noun"));
				return new Lines(Component.translatable("mettle.moment.level", skillName, level), title, sub, skillAccent, icon);
			}
			case CHARACTER -> {
				int points = dev.voidmc.mettle.character.Perks.attributePoints(mc.player);
				Component sub = points > 0
					? Component.translatable(points == 1 ? "mettle.moment.attribute_points.one" : "mettle.moment.attribute_points", points, key)
					: Component.translatable("mettle.hint.press_k", key);
				return new Lines(Component.translatable("mettle.moment.character", level), null, sub, Ui.ACCENT, null);
			}
			default -> {
				ResourceKey<Perk> perkKey = perkId == null ? null : Perks.key(Identifier.parse(perkId));
				Perk perk = perkKey == null ? null : registries.lookupOrThrow(MettleRegistries.PERK).getValue(perkKey);
				Component name = perk == null ? Component.literal(String.valueOf(perkId)) : perk.name();
				if (perk != null && kind == MomentKind.LEARNED && perk.ranks() > 1) {
					name = Component.translatable("mettle.moment.perk_rank", name, roman(Math.max(1, rank)));
				}
				Component sub = perk == null ? null : PerkText.description(perk, Math.max(1, rank));
				Component lead = Component.translatable("mettle.moment." + kind.serializedName());
				Identifier icon = perkKey == null ? null : Perk.icon(perkKey);
				return new Lines(lead, name, sub, kind.earned() ? CHAMPAGNE : skillAccent, icon);
			}
		}
	}

	static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(n);
		};
	}
}
