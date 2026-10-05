package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import dev.voidmc.mettle.mixin.marksmanship.ProjectileWeaponItemInvoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What happens as arrows leave a bow or crossbow: Steady Breath, Sixty Paces, Steppe Draw's saddle, Leaping Shot,
 * Double Nock and Volley. {@code ProjectileWeaponItemMixin} asks for a {@link Plan} before vanilla shoots, shoots with
 * it, and tags every arrow it creates on the way. Server thread only.
 */
public final class Shots {
	/**
	 * One loosing, after perks.
	 *
	 * @param speed    how much faster than vanilla the arrows fly (hits divide damage by it)
	 * @param spread   the least fan, in degrees, the arrows spread over
	 * @param fullDraw a fully drawn bow, or a crossbow
	 */
	public record Plan(List<ItemStack> projectiles, float power, float uncertainty, boolean crit, float spread, float speed,
		boolean fullDraw, Vec3 origin) {
	}

	private static @Nullable Plan current;
	private static final Map<UUID, Integer> SHOTS = new HashMap<>();

	private Shots() {
	}

	/** The plan being shot right now, if a player is shooting. */
	public static @Nullable Plan current() {
		return current;
	}

	public static void begin(Plan plan) {
		current = plan;
	}

	public static void end() {
		current = null;
	}

	public static Plan plan(ServerPlayer player, ItemStack weapon, List<ItemStack> projectiles, float power, float uncertainty, boolean crit) {
		boolean bow = weapon.getItem() instanceof BowItem;
		// A vanilla bow passes "critical" exactly when it was fully drawn; crossbows always loose at full charge.
		boolean fullDraw = bow ? crit : weapon.getItem() instanceof CrossbowItem;
		List<ItemStack> arrows = new ArrayList<>(projectiles);
		float speed = 1f;
		float spread = 0f;

		if (Perks.has(player, MarksmanshipSkill.SIXTY_PACES)) {
			speed *= MarksmanshipRules.SIXTY_PACES_SPEED;
		}
		if (fullDraw && player.isShiftKeyDown() && Perks.has(player, MarksmanshipSkill.STEADY_BREATH)) {
			speed *= MarksmanshipRules.STEADY_BREATH_SPEED;
			uncertainty = 0f;
			MarksmanshipSkill.chip(player, MarksmanshipSkill.STEADY_BREATH, MarksmanshipSkill.text(MarksmanshipSkill.STEADY_BREATH));
		}
		if (player.isPassenger() && Perks.has(player, MarksmanshipSkill.STEPPE_DRAW)) {
			uncertainty = 0f;
			MarksmanshipSkill.chip(player, MarksmanshipSkill.STEPPE_DRAW, MarksmanshipSkill.text(MarksmanshipSkill.STEPPE_DRAW));
		}
		if (airborne(player) && Perks.has(player, MarksmanshipSkill.LEAPING_SHOT)) {
			crit = true;
			MarksmanshipSkill.chip(player, MarksmanshipSkill.LEAPING_SHOT, MarksmanshipSkill.text(MarksmanshipSkill.LEAPING_SHOT));
		}

		// Volley: two more arrows from the player's own supply, fanned like Multishot.
		if (bow && fullDraw && player.isShiftKeyDown() && Perks.has(player, MarksmanshipSkill.VOLLEY)) {
			int before = arrows.size();
			for (int i = 0; i < 2; i++) {
				ItemStack ammo = player.getProjectile(weapon);
				if (ammo.isEmpty()) {
					break;
				}
				arrows.addAll(ProjectileWeaponItemInvoker.mettle$draw(weapon, ammo, player));
			}
			if (arrows.size() > before) {
				spread = Math.max(spread, MarksmanshipRules.VOLLEY_SPREAD);
				MarksmanshipSkill.chip(player, MarksmanshipSkill.VOLLEY, MarksmanshipSkill.text(MarksmanshipSkill.VOLLEY, arrows.size()));
			}
		}

		// Double Nock: every fifth shot a free twin. It can't be picked up, so it never makes arrows from nothing.
		if (!arrows.isEmpty() && Perks.has(player, MarksmanshipSkill.DOUBLE_NOCK)) {
			int shot = SHOTS.merge(player.getUUID(), 1, Integer::sum);
			if (MarksmanshipRules.doubleNock(shot)) {
				SHOTS.put(player.getUUID(), 0);
				ItemStack twin = arrows.getFirst().copyWithCount(1);
				twin.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
				arrows.add(twin);
				spread = Math.max(spread, MarksmanshipRules.DOUBLE_NOCK_SPREAD);
				MarksmanshipSkill.chip(player, MarksmanshipSkill.DOUBLE_NOCK, MarksmanshipSkill.text(MarksmanshipSkill.DOUBLE_NOCK));
			}
		}
		return new Plan(arrows, power * speed, uncertainty, crit, spread, speed, fullDraw, player.getEyePosition());
	}

	/** In the air of their own accord: jumping or falling, not riding, swimming or climbing. */
	public static boolean airborne(ServerPlayer player) {
		return !player.onGround() && !player.isPassenger() && !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying;
	}

	/** Marks an arrow the current plan just created. */
	public static void tag(Projectile projectile) {
		Plan plan = current;
		if (plan != null && projectile instanceof ShotArrow arrow) {
			arrow.mettle$loosed(plan.origin(), plan.fullDraw(), plan.speed());
		}
	}

	public static void forget(UUID player) {
		SHOTS.remove(player);
	}
}
