package dev.voidmc.mettle.character.skill.marksmanship;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.MarksmanshipSkill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What happens when a player's arrow strikes something living: Long Shot, Deadeye, Wingshot (damage), Pinning Shot and
 * Hunter's Mark (on the target), Retrieval, Deadeye and Huntsman (arrows coming back), and the Sixty Paces and
 * Wingshot feat counters. {@code AbstractArrowMixin} calls {@link #plan} as the arrow hits, then {@link #damage},
 * {@link #beforeHurt} and {@link #afterHurt} around vanilla's damage call. Server thread only.
 */
public final class Hits {
	/** Mobs that fly, for Wingshot's bonus. */
	private static final Set<EntityType<?>> FLIERS = Set.of(EntityTypes.PHANTOM, EntityTypes.BLAZE, EntityTypes.GHAST, EntityTypes.BREEZE,
		EntityTypes.VEX, EntityTypes.BAT, EntityTypes.BEE, EntityTypes.PARROT, EntityTypes.ALLAY, EntityTypes.ENDER_DRAGON,
		EntityTypes.WITHER, EntityTypes.HAPPY_GHAST);
	/** The four the Wingshot feat counts. */
	private static final Set<EntityType<?>> WINGSHOT_PREY = Set.of(EntityTypes.PHANTOM, EntityTypes.BLAZE, EntityTypes.GHAST, EntityTypes.BREEZE);

	/** Arrows waiting inside a mob for it to die (Retrieval). Weak: a mob that despawns takes its arrows with it. */
	private static final Map<LivingEntity, List<Returned>> LODGED = new WeakHashMap<>();
	/** Animals that already gave Huntsman's extra, so a second loot roll (Field Dressing) doesn't double it. */
	private static final Set<Entity> DRESSED = Collections.newSetFromMap(new WeakHashMap<>());

	private record Returned(UUID player, ItemStack arrow) {
	}

	/** One arrow striking one living target, worked out before the damage lands. */
	public static final class Hit {
		final ServerPlayer player;
		final LivingEntity target;
		final double distance;
		final boolean headshot;
		final float multiplier;
		final boolean returnable;
		boolean lodged;

		Hit(ServerPlayer player, LivingEntity target, double distance, boolean headshot, float multiplier, boolean returnable) {
			this.player = player;
			this.target = target;
			this.distance = distance;
			this.headshot = headshot;
			this.multiplier = multiplier;
			this.returnable = returnable;
		}
	}

	private Hits() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			List<Returned> lodged = LODGED.remove(entity);
			if (lodged != null && entity.level() instanceof ServerLevel level) {
				returnArrows(level, entity, lodged);
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof Animal animal
				&& context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof ServerPlayer player
				&& context.getOptional(LootContextParams.DIRECT_ATTACKING_ENTITY) instanceof AbstractArrow
				&& Perks.has(player, MarksmanshipSkill.HUNTSMAN) && DRESSED.add(animal)) {
				huntsmanDrops(player, drops);
			}
		});
	}

	/** Null when the hit is none of Marksmanship's business (not a player's arrow, not a living target). */
	public static @Nullable Hit plan(AbstractArrow arrow, EntityHitResult result) {
		if (!(arrow.getOwner() instanceof ServerPlayer player) || !(result.getEntity() instanceof LivingEntity target) || target == player) {
			return null;
		}
		Vec3 origin = arrow instanceof ShotArrow shot && shot.mettle$origin() != null ? shot.mettle$origin() : player.getEyePosition();
		double distance = origin.distanceTo(result.getLocation());
		boolean headshot = !(target instanceof Player) && Perks.has(player, MarksmanshipSkill.DEADEYE)
			&& MarksmanshipRules.headshot(result.getLocation().y, target.getEyeY());

		float multiplier = 1f;
		if (Perks.has(player, MarksmanshipSkill.LONG_SHOT)) {
			float bonus = MarksmanshipRules.longShot(distance);
			if (bonus > 1f) {
				multiplier *= bonus;
				MarksmanshipSkill.chip(player, MarksmanshipSkill.LONG_SHOT, MarksmanshipSkill.text(MarksmanshipSkill.LONG_SHOT,
					(int) distance, Math.round((bonus - 1f) * 100)));
			}
		}
		if (FLIERS.contains(target.getType()) && Perks.has(player, MarksmanshipSkill.WINGSHOT)) {
			multiplier *= MarksmanshipRules.WINGSHOT_BONUS;
			MarksmanshipSkill.chip(player, MarksmanshipSkill.WINGSHOT, MarksmanshipSkill.text(MarksmanshipSkill.WINGSHOT));
		}
		if (headshot) {
			multiplier *= 2f;
		}
		// Speed perks make arrows fly faster, and vanilla turns speed into damage; take that back out.
		if (arrow instanceof ShotArrow shot && shot.mettle$speed() > 0) {
			multiplier /= shot.mettle$speed();
		}
		boolean returnable = arrow.pickup == AbstractArrow.Pickup.ALLOWED && !arrow.getPickupItemStackOrigin().isEmpty();
		return new Hit(player, target, distance, headshot, multiplier, returnable);
	}

	/** Deadeye: a headshot is always critical. */
	public static boolean crit(Hit hit, boolean vanilla) {
		return vanilla || hit.headshot;
	}

	public static float damage(Hit hit, float damage) {
		return damage * hit.multiplier;
	}

	/** Retrieval rolls as the arrow goes in: if it kills, the mob's death drops it straight back. */
	public static void beforeHurt(AbstractArrow arrow, Hit hit) {
		int rank = Perks.rank(hit.player, MarksmanshipSkill.RETRIEVAL);
		if (rank > 0 && hit.returnable && !(hit.target instanceof Player) && hit.player.getRandom().nextFloat() < MarksmanshipRules.retrieval(rank)) {
			List<Returned> list = LODGED.computeIfAbsent(hit.target, t -> new ArrayList<>());
			if (list.size() < 64) {
				list.add(new Returned(hit.player.getUUID(), arrow.getPickupItemStackOrigin().copyWithCount(1)));
				hit.lodged = true;
			}
		}
	}

	public static void afterHurt(AbstractArrow arrow, Hit hit, boolean hurt) {
		ServerPlayer player = hit.player;
		LivingEntity target = hit.target;
		if (!hurt) {
			if (hit.lodged) {
				List<Returned> list = LODGED.get(target);
				if (list != null && !list.isEmpty()) {
					list.removeLast();
				}
			}
			return;
		}
		ServerLevel level = player.level();
		if (hit.distance >= MarksmanshipRules.SIXTY_PACES_DISTANCE) {
			FeatCounters.add(player, MarksmanshipSkill.SIXTY_PACES_COUNTER, 1);
		}
		if (hit.headshot) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
			MarksmanshipSkill.chip(player, MarksmanshipSkill.DEADEYE, MarksmanshipSkill.text(MarksmanshipSkill.DEADEYE));
		}
		if (target.isAlive()) {
			if (arrow instanceof ShotArrow shot && shot.mettle$fullDraw() && Perks.has(player, MarksmanshipSkill.PINNING_SHOT)) {
				// Slowness III is −45 %, the nearest a potion level comes to "by half".
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), player);
				MarksmanshipSkill.chip(player, MarksmanshipSkill.PINNING_SHOT, MarksmanshipSkill.text(MarksmanshipSkill.PINNING_SHOT));
			}
			if (!(target instanceof Player) && Perks.has(player, MarksmanshipSkill.HUNTERS_MARK)) {
				HuntersMark.mark(player, target);
			}
			return;
		}
		if (WINGSHOT_PREY.contains(target.getType())) {
			FeatCounters.add(player, MarksmanshipSkill.WINGSHOT_COUNTER, 1);
		}
		// Deadeye's headshot kill and Huntsman's animal kill bring this very arrow back (unless Retrieval already does).
		boolean deadeye = hit.headshot;
		boolean huntsman = target instanceof Animal && Perks.has(player, MarksmanshipSkill.HUNTSMAN);
		if ((deadeye || huntsman) && hit.returnable && !hit.lodged) {
			drop(level, target, arrow.getPickupItemStackOrigin().copyWithCount(1));
			if (huntsman) {
				MarksmanshipSkill.chip(player, MarksmanshipSkill.HUNTSMAN, MarksmanshipSkill.text(MarksmanshipSkill.HUNTSMAN));
			}
		}
	}

	private static void returnArrows(ServerLevel level, LivingEntity entity, List<Returned> lodged) {
		Map<UUID, Integer> perPlayer = new java.util.HashMap<>();
		for (Returned r : lodged) {
			drop(level, entity, r.arrow());
			perPlayer.merge(r.player(), 1, Integer::sum);
		}
		perPlayer.forEach((uuid, n) -> {
			if (level.getServer().getPlayerList().getPlayer(uuid) instanceof ServerPlayer player) {
				MarksmanshipSkill.chip(player, MarksmanshipSkill.RETRIEVAL, MarksmanshipSkill.text(MarksmanshipSkill.RETRIEVAL, n));
			}
		});
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 0.8f);
	}

	private static void drop(ServerLevel level, LivingEntity at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 0.3, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/** Huntsman: one more of the meat and one more of the hide (leather, rabbit hide or feathers) the kill dropped. */
	private static void huntsmanDrops(ServerPlayer player, List<ItemStack> drops) {
		ItemStack meat = null;
		ItemStack hide = null;
		for (ItemStack d : drops) {
			if (meat == null && d.is(ItemTags.MEAT)) {
				meat = d;
			} else if (hide == null && (d.is(Items.LEATHER) || d.is(Items.RABBIT_HIDE) || d.is(Items.FEATHER))) {
				hide = d;
			}
		}
		if (meat != null) {
			drops.add(meat.copyWithCount(1));
		}
		if (hide != null) {
			drops.add(hide.copyWithCount(1));
		}
	}

	/** Test hook: how many arrows are waiting inside this mob. */
	public static int lodged(LivingEntity entity) {
		List<Returned> list = LODGED.get(entity);
		return list == null ? 0 : list.size();
	}
}
