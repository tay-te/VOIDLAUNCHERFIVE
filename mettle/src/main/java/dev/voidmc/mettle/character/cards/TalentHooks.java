package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.core.CardStore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Talent effects that hook vanilla directly (docs/BUILD_FEEL.md §13.2; the rest sit in CardHooks beside the traits they
 * resemble): Light Step, Quick Hands, Light Sleeper's phantoms, Night Eyes. Both sides read the synced card cache, so the
 * client predicts what the server decides.
 */
public final class TalentHooks {
	private static final Identifier LIGHT_STEP = Identifier.fromNamespaceAndPath("mettle", "talent/light_step");
	private static final Identifier QUICK_HANDS = Identifier.fromNamespaceAndPath("mettle", "talent/quick_hands");
	private static final Identifier NIGHT_EYES = Identifier.fromNamespaceAndPath("mettle", "talent/night_eyes");

	private TalentHooks() {
	}

	/** Light Step: a sneaking player sets off no pressure plate or tripwire (Entity#isIgnoringBlockTriggers). */
	public static boolean ignoresTriggers(Entity entity, boolean ignores) {
		return ignores || entity instanceof Player p && p.isShiftKeyDown() && CardStore.get(p).trait(LIGHT_STEP) > 0;
	}

	/** Quick Hands: eating and drinking take X less time (the use duration of food and drink). */
	public static int useDuration(LivingEntity entity, ItemStack stack, int duration) {
		if (!(entity instanceof Player p) || duration <= 1 || stack == null) {
			return duration;
		}
		double quick = CardStore.get(p).trait(QUICK_HANDS) / 100.0;
		if (quick <= 0) {
			return duration;
		}
		ItemUseAnimation a = stack.getUseAnimation();
		if (a != ItemUseAnimation.EAT && a != ItemUseAnimation.DRINK) {
			return duration;
		}
		return Math.max(1, (int) Math.round(duration * (1 - quick)));
	}

	/** Light Sleeper: the phantom spawner passes this player by one night in two (as if they were a spectator). */
	public static boolean phantomsPass(ServerPlayer player, boolean spectator) {
		return spectator || Talents.phantomsSpare(player);
	}

	/** Night Eyes: the night-vision share the lightmap gets, 0.1–0.3 (the talent's value), never below vanilla's own. */
	public static float nightEyes(Player player, float intensity) {
		double v = CardStore.get(player).trait(NIGHT_EYES) / 100.0;
		return v <= 0 ? intensity : Math.max(intensity, (float) v);
	}
}
