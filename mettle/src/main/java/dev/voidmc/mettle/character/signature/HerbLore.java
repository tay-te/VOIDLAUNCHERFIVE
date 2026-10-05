package dev.voidmc.mettle.character.signature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Herb Lore (Herbalist): potions brewed at a stand last opened by the Herbalist last half as long again. The potion keeps
 * its name and colour; its effects become custom effects with longer durations, and it is marked so it is never
 * extended twice.
 */
public final class HerbLore {
	private HerbLore() {
	}

	/** Extends a freshly brewed potion in place. Returns whether anything changed (instant potions do not). */
	public static boolean extend(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null || stack.has(SignaturePerks.HERB_LORE_BREWED)) {
			return false;
		}
		List<MobEffectInstance> effects = new ArrayList<>();
		boolean longer = false;
		for (MobEffectInstance e : contents.getAllEffects()) {
			if (e.getEffect().value().isInstantaneous() || e.isInfiniteDuration()) {
				effects.add(new MobEffectInstance(e));
				continue;
			}
			effects.add(new MobEffectInstance(e.getEffect(), Math.round(e.getDuration() * SignaturePerks.HERB_LORE_DURATION), e.getAmplifier(),
				e.isAmbient(), e.isVisible(), e.showIcon()));
			longer = true;
		}
		if (!longer) {
			return false;
		}
		Optional<String> name = contents.customName().or(() -> contents.potion().map(p -> p.value().name()));
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(contents.getColor()), List.copyOf(effects), name));
		stack.set(SignaturePerks.HERB_LORE_BREWED, Unit.INSTANCE);
		return true;
	}
}
