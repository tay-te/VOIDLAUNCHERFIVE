package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import dev.voidmc.mettle.mixin.marksmanship.ProjectileWeaponItemInvoker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Arrowstorm: keep holding a fully drawn bow and it looses an arrow every half second at three-quarter power, until
 * you let go or run dry. The server ticks it; the arrows are ordinary shots, so every other perk applies to them.
 */
public final class Arrowstorm {
	/** Per player, during one draw: when the next storm arrow flies, and how many have. */
	private static final class Storm {
		long next;
		int loosed;
	}

	private static final Map<UUID, Storm> STORMS = new HashMap<>();

	private Arrowstorm() {
	}

	public static void tick(ServerPlayer player) {
		if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof BowItem)) {
			STORMS.remove(player.getUUID());
			return;
		}
		if (!Perks.has(player, MarksmanshipSkill.ARROWSTORM) || !Draw.fullyDrawn(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Storm storm = STORMS.get(player.getUUID());
		if (storm == null) {
			// Full draw reached: the first storm arrow follows half a second later.
			storm = new Storm();
			storm.next = now + MarksmanshipRules.ARROWSTORM_INTERVAL;
			STORMS.put(player.getUUID(), storm);
			return;
		}
		if (now < storm.next) {
			return;
		}
		storm.next = now + MarksmanshipRules.ARROWSTORM_INTERVAL;
		if (loose(player) && storm.loosed++ == 0) {
			MarksmanshipSkill.chip(player, MarksmanshipSkill.ARROWSTORM, MarksmanshipSkill.text(MarksmanshipSkill.ARROWSTORM));
		}
	}

	/** One storm arrow, exactly as the bow would loose it at three-quarter draw; false when the quiver is dry. */
	private static boolean loose(ServerPlayer player) {
		ItemStack bow = player.getUseItem();
		ItemStack ammo = player.getProjectile(bow);
		if (ammo.isEmpty()) {
			return false;
		}
		List<ItemStack> drawn = ProjectileWeaponItemInvoker.mettle$draw(bow, ammo, player);
		if (drawn.isEmpty()) {
			return false;
		}
		((ProjectileWeaponItemInvoker) bow.getItem()).mettle$shoot(player.level(), player, player.getUsedItemHand(), bow, drawn,
			MarksmanshipRules.ARROWSTORM_POWER * 3f, 1f, false, null);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f,
			1f / (player.getRandom().nextFloat() * 0.4f + 1.2f) + MarksmanshipRules.ARROWSTORM_POWER * 0.5f);
		player.awardStat(Stats.ITEM_USED.get(bow.getItem()));
		return true;
	}

	public static void forget(UUID player) {
		STORMS.remove(player);
	}
}
