package dev.voidmc.bestiary.elite;

import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.CreatureData;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.danger.DangerConfig;
import dev.voidmc.bestiary.danger.DangerMath;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Elites and champion packs (BESTIARY.md §7): who becomes one (danger's chance, at most one within 64 blocks, never
 * from spawners), its affixes, its name ("the Gravewalker of Kestrel Heath"), how it is shown (Fray's profile: rank,
 * subtitle and affix chips on the nameplate and target frame) and the affixes' running effects (regeneration, enrage,
 * drips, the sidestep). Damage and status effects of affixes are in {@link EliteRules}.
 */
public final class Elites {
	private static final Set<Mob> LIVE = Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private static final String[] FIRST = {"Kestrel", "Raven", "Ashby", "Thorn", "Hollin", "Wyke", "Barrow", "Cold", "Hob", "Elder", "Gallow", "Fen", "Grey",
		"Harrow", "Sorrow", "Bram", "Crake", "Wether", "Moss", "Lantern"};
	public static final List<String> ELIGIBLE = List.of("natural", "chunk_generation", "structure", "patrol");

	private Elites() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(Elites::tick);
	}

	/** Rolls a fresh creature (once): elite by danger, affixes, a champion pack at D3+. */
	public static CreatureData roll(Mob mob, CreatureProfile p, CreatureData d, ServerLevel level) {
		if (!ELIGIBLE.contains(d.reason()) || !p.rank().equals("common")) {
			return d.rolled(false, List.of(), 0);
		}
		RandomSource r = mob.getRandom();
		DangerConfig c = DangerConfig.get();
		float chance = DangerMath.eliteChance(d.danger()) * (c.mythic ? 2 : 1);
		if (r.nextFloat() >= chance || eliteNear(level, mob, c.eliteSpacing)) {
			return d.rolled(false, List.of(), 0);
		}
		List<String> affixes = pick(p, d.danger(), r);
		int pack = d.danger() >= c.packFrom && r.nextFloat() < c.packChance ? 3 + r.nextInt(3) : 0;
		return d.rolled(true, affixes, pack);
	}

	static boolean eliteNear(ServerLevel level, Mob mob, int spacing) {
		for (Mob m : LIVE) {
			if (m.isAlive() && m.level() == level && m.distanceToSqr(mob) < (double) spacing * spacing) {
				return true;
			}
		}
		return false;
	}

	public static List<String> pick(CreatureProfile p, float danger, RandomSource r) {
		List<Affix> pool = new ArrayList<>();
		for (Affix a : Affix.VALUES) {
			if (!a.humanoidOnly || p.shieldable()) {
				pool.add(a);
			}
		}
		int n = Math.min(pool.size(), DangerMath.affixes(danger, r.nextFloat()));
		List<String> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(pool.remove(r.nextInt(pool.size())).id);
		}
		return out;
	}

	/** The elite's name: its kind and a place, "the Gravewalker of Kestrel Heath" (stable per creature). */
	public static String name(Mob mob, CreatureProfile p, ServerLevel level) {
		RandomSource r = RandomSource.create(mob.getUUID().getLeastSignificantBits());
		Holder<Biome> biome = level.getBiome(mob.blockPosition());
		String place;
		if (mob.getY() < level.getSeaLevel() - 20) {
			place = "Deep";
		} else if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)) {
			place = "Wood";
		} else if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
			place = "Fell";
		} else if (biome.is(BiomeTags.IS_TAIGA)) {
			place = "Pines";
		} else if (biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN)) {
			place = "Strand";
		} else if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
			place = "Waste";
		} else if (biome.is(BiomeTags.IS_RIVER)) {
			place = "Mere";
		} else {
			place = r.nextBoolean() ? "Heath" : "Moor";
		}
		return "the " + p.codexName() + " of " + FIRST[r.nextInt(FIRST.length)] + " " + place;
	}

	/** Shows the creature: name, Fray profile (rank, subtitle, affix chips), a shield for Shielded. Called on every load. */
	public static void present(Mob mob, CreatureProfile p, CreatureData d, ServerLevel level) {
		List<Identifier> chips = new ArrayList<>();
		for (String a : d.affixes()) {
			chips.add(dev.voidmc.bestiary.Bestiary.id(a));
		}
		String danger = String.format(Locale.ROOT, "danger %.1f", d.danger());
		if (d.elite()) {
			if (!mob.hasCustomName()) {
				mob.setCustomName(Component.literal(name(mob, p, level)));
			}
			LIVE.add(mob);
			StringBuilder sub = new StringBuilder("Elite");
			if (d.packSize() > 0) {
				sub.append(" · champion pack");
			}
			for (String a : d.affixes()) {
				Affix af = Affix.byId(a);
				sub.append(" · ").append(af == null ? a : af.title());
			}
			sub.append(" · ").append(danger);
			FrayApi.profiles().set(mob, Rank.ELITE, Component.literal(sub.toString()), chips);
		} else {
			String sub = p.codexName() + (d.leader().isPresent() ? " · champion pack" : "") + " · " + danger;
			FrayApi.profiles().set(mob, p.rank().equals("boss") ? Rank.BOSS : Rank.COMMON, Component.literal(sub), chips);
			if (!chips.isEmpty()) {
				LIVE.add(mob);
			}
		}
		if (d.has(Affix.SHIELDED.id) && mob.getOffhandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			mob.setDropChance(EquipmentSlot.OFFHAND, 0);
		}
	}

	public static boolean affixed(LivingEntity e, Affix a) {
		return e instanceof Mob m && CreatureData.get(m) != null && CreatureData.of(m).has(a.id);
	}

	/** Running effects, every tick for the few live affixed creatures. */
	static void tick(ServerLevel level) {
		if (LIVE.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Mob m : List.copyOf(LIVE)) {
			if (m.level() != level) {
				continue;
			}
			if (!m.isAlive()) {
				LIVE.remove(m);
				continue;
			}
			CreatureData d = CreatureData.of(m);
			Brain b = Brain.get(m);
			if (d.has(Affix.REGENERATING.id) && now % 20 == 0 && m.getHealth() < m.getMaxHealth() && b != null && now - b.lastHurt >= 60
				&& !FrayApi.statuses().has(m, StatusType.BURN) && !FrayApi.statuses().has(m, StatusType.BLIGHT)) {
				m.heal(m.getMaxHealth() * 0.02F);
			}
			if (d.has(Affix.FRENZIED.id) && b != null && !b.enraged && m.getHealth() <= m.getMaxHealth() * 0.5F) {
				// Enrage: a snarl, the eyes redden (a red glint at the eyes).
				b.enraged = true;
				level.playSound(null, m.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.7F, 1.6F);
				Vec3 eye = m.getEyePosition();
				level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xC01010, 0.8F), eye.x, eye.y, eye.z, 8, 0.15, 0.05, 0.15, 0);
			}
			if (d.has(Affix.FRENZIED.id) && b != null && b.enraged && now % 30 == 0) {
				Vec3 eye = m.getEyePosition();
				level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xC01010, 0.5F), eye.x, eye.y, eye.z, 2, 0.12, 0.03, 0.12, 0);
			}
			if (d.has(Affix.VENOMOUS.id) && now % 12 == m.getId() % 12) {
				level.sendParticles(ParticleTypes.ITEM_SLIME, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 1, 0.25, 0.2, 0.25, 0);
			}
			if (d.has(Affix.IRONBOUND.id) && now % 40 == m.getId() % 40 && EliteRules.plateIntact(m)) {
				level.sendParticles(ParticleTypes.WAX_OFF, m.getX(), m.getY() + m.getBbHeight() * 0.7, m.getZ(), 1, 0.3, 0.3, 0.3, 0);
			}
			if (d.has(Affix.SWIFT.id) && b != null && now % 2 == 0) {
				sidestep(m, b, level, now);
			}
		}
	}

	/** Swift: sidesteps a heavy it sees coming (the target's heavy or charged action, aimed at it, within 4.5 blocks). */
	static void sidestep(Mob m, Brain b, ServerLevel level, long now) {
		LivingEntity t = b.target;
		if (t == null || b.busy() || now < b.nextSidestep || !m.onGround() || t.distanceToSqr(m) > 4.5 * 4.5) {
			return;
		}
		var cur = FrayApi.actions().current(t);
		if (cur == null || !(cur.getPath().endsWith("heavy") || cur.getPath().endsWith("charged"))) {
			return;
		}
		Vec3 look = t.getLookAngle().multiply(1, 0, 1);
		Vec3 to = m.position().subtract(t.position()).multiply(1, 0, 1);
		if (look.lengthSqr() < 1e-4 || to.lengthSqr() < 1e-4 || look.normalize().dot(to.normalize()) < 0.7) {
			return;
		}
		Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(m.getRandom().nextBoolean() ? 0.75 : -0.75);
		m.setDeltaMovement(side.x, 0.22, side.z);
		m.needsSync = true;
		b.nextSidestep = now + 60;
		level.playSound(null, m.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 0.6F, 1.5F);
		EliteRules.SIDESTEPS++;
	}

	/** Venomous: a poison cloud when hit hard (a fifth of its health or more in one hit), at most every 5 s. */
	static void venomCloud(Mob m, ServerLevel level) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, m.getX(), m.getY(), m.getZ());
		cloud.setRadius(2.5F);
		cloud.setRadiusOnUse(-0.3F);
		cloud.setWaitTime(5);
		cloud.setDuration(60);
		cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
		cloud.setOwner(m);
		level.addFreshEntity(cloud);
	}

	public static void forget(Mob m) {
		LIVE.remove(m);
	}

	public static int live() {
		return LIVE.size();
	}

	static float bluntShare(java.util.Map<DamageKind, Float> amounts) {
		float total = 0, blunt = 0;
		for (var e : amounts.entrySet()) {
			total += e.getValue();
			if (e.getKey() == DamageKind.BLUNT) {
				blunt += e.getValue();
			}
		}
		return total <= 0 ? 0 : blunt / total;
	}
}
