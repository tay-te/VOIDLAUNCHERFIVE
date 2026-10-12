package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.character.skill.blades.Swings;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the twenty Mythics do (docs/CARDS.md §9.3), each only while its card is slotted. Read from the synced card cache,
 * so both sides agree where it matters (Hush, the Unclad's speed); everything else is decided on the server.
 */
public final class MythicEffects {
	private MythicEffects() {
	}

	private static boolean has(Player player, String id) {
		return Mythics.active(player, id);
	}

	private static final Identifier UNCLAD = Mettle.id("mythic_timed/unclad");
	private static final Identifier TRAILMAKER = Mettle.id("mythic_timed/trailmaker");

	/**
	 * Per-holder memory: counters, in game ticks. Server only, never saved; the cooldowns are saved with the player
	 * ({@link CardCooldowns}), so a relog or a restart never resets one.
	 */
	private static final class Memory {
		int homeward;
		long blockingSince = -1;
		boolean dashFalling;
		Map<Integer, int[]> twin = new HashMap<>();
	}

	private static final Map<UUID, Memory> MEMORY = new HashMap<>();
	/** Dragonfall standards: where, until when, the set lent out (keyword, tier). */
	private record Standard(ServerLevel level, Vec3 at, long until, Identifier keyword, int tier) {
	}

	private static final Map<UUID, Standard> STANDARDS = new HashMap<>();
	/** Sets lent by a standard: player → (keyword → tier), refreshed every second while in range. */
	private static final Map<UUID, Map<Identifier, Integer>> BORROWED = new HashMap<>();
	private record Dome(ServerLevel level, Vec3 at, long until) {
	}

	private static final Map<UUID, Dome> DOMES = new HashMap<>();
	private static final Map<AbstractArrow, UUID> SEEKING = new WeakHashMap<>();
	private static final Map<UUID, LivingEntity> LAST_HIT = new HashMap<>();
	/** Pardon's cooldown: once every 7 in-game days. */
	static final long PARDON_TICKS = 7 * 24000L;
	/** The Stride's cooldown: 8 s. */
	public static final int DASH_TICKS = 160;
	private static final Map<UUID, List<Wolf>> PACKS = new HashMap<>();
	private static final Set<UUID> PARDONED = new HashSet<>();
	private static boolean inner;

	private static Memory memory(Player player) {
		return MEMORY.computeIfAbsent(player.getUUID(), u -> new Memory());
	}

	private static long now(Player player) {
		return player.level().getGameTime();
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(MythicEffects::tick);
		ServerPlayerEvents.LEAVE.register(p -> {
			MEMORY.remove(p.getUUID());
			STANDARDS.remove(p.getUUID());
			BORROWED.remove(p.getUUID());
			DOMES.remove(p.getUUID());
			PACKS.remove(p.getUUID());
			LAST_HIT.remove(p.getUUID());
			SEEKING.values().removeIf(p.getUUID()::equals);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MythicEffects::afterDamage);
		// Hundredth Dawn's Pardon: the death goes ahead, but nothing is dropped, and the respawn gets it all back.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && has(p, "hundredth_dawn") && CardCooldowns.ready(p, CardCooldowns.PARDON)) {
				PARDONED.add(p.getUUID());
				// The cooldown is saved now and kept through the death (CardCooldowns copies on death).
				CardCooldowns.start(p, CardCooldowns.PARDON, PARDON_TICKS);
			}
			return true;
		});
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && PARDONED.remove(oldPlayer.getUUID())) {
				newPlayer.getInventory().replaceWith(oldPlayer.getInventory());
				newPlayer.experienceLevel = oldPlayer.experienceLevel;
				newPlayer.totalExperience = oldPlayer.totalExperience;
				newPlayer.experienceProgress = oldPlayer.experienceProgress;
				if (CardCooldowns.ready(newPlayer, CardCooldowns.PARDON)) {
					CardCooldowns.start(newPlayer, CardCooldowns.PARDON, PARDON_TICKS);
				}
				SignaturePerks.feedback(newPlayer, Mettle.id("mythic/hundredth_dawn"), Component.translatable("mettle.mythic.fired.hundredth_dawn"));
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer p && hand == InteractionHand.MAIN_HAND && p.isShiftKeyDown()
				&& p.getMainHandItem().is(ItemTags.BANNERS) && has(p, "dragonfall") && CardCooldowns.ready(p, CardCooldowns.STANDARD)) {
				raiseStandard(p);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer p && hand == InteractionHand.MAIN_HAND && p.isShiftKeyDown() && p.getMainHandItem().is(ItemTags.PICKAXES)
				&& has(p, "heart_of_the_mountain") && CardCooldowns.ready(p, CardCooldowns.BORE) && boreable(p, (ServerLevel) level, hit.getBlockPos())) {
				bore(p, hit.getBlockPos());
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerPlayNetworking.registerGlobalReceiver(MettlePayloads.Dash.TYPE, (payload, context) -> dashed(context.player()));
		MettleEvents.MODIFY_XP.register((player, skill, xp) -> xp * mentor(player, skill));
	}

	// ---------------------------------------------------------------- damage (called from CardHooks.hurt)

	/** The Lone Banner (+15 % alone) on damage a holder deals. */
	static double dealt(ServerPlayer player) {
		return has(player, "lone_banner") && alone(player) ? 1.15 : 1;
	}

	/** The Unclad (−25 % with no armour) and the Lone Banner (−15 % alone) on damage a holder takes. */
	static double taken(ServerPlayer player) {
		double m = 1;
		if (has(player, "unclad") && Mythics.unclad(player)) {
			m *= 0.75;
		}
		if (has(player, "lone_banner") && alone(player)) {
			m *= 0.85;
		}
		return m;
	}

	private static boolean alone(ServerPlayer player) {
		return player.level().players().stream().noneMatch(p -> p != player && CardHooks.allies(player, p) && p.distanceToSqr(player) <= 24 * 24);
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (inner || !(source.getEntity() instanceof ServerPlayer player) || player == target || taken <= 0) {
			return;
		}
		if (target instanceof Mob) {
			LAST_HIT.put(player.getUUID(), target);
		}
		Combat.Weapon weapon = Combat.weapon(source, player);
		ServerLevel level = (ServerLevel) target.level();
		// The Twin Edge: every fourth blade hit on the same target is struck again, for full damage.
		if (weapon == Combat.Weapon.BLADE && has(player, "twin_edge")) {
			Map<Integer, int[]> twins = memory(player).twin;
			long now = now(player);
			if (twins.size() > 64) {
				twins.values().removeIf(c -> now - c[1] > 100);
			}
			int[] count = twins.computeIfAbsent(target.getId(), k -> new int[] {0, 0});
			if (now - count[1] > 100) {
				count[0] = 0;
			}
			count[1] = (int) now;
			if (++count[0] >= 4 && target.isAlive()) {
				count[0] = 0;
				strike(level, target, player.damageSources().magic(), taken);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0, 0, 0, 0);
			}
		}
		// Worldsplitter: a fully charged sneak-attack sends a shockwave 8 blocks along the ground.
		if (weapon == Combat.Weapon.HEAVY && player.isShiftKeyDown() && has(player, "worldsplitter") && CardCooldowns.ready(player, CardCooldowns.SPLIT)
			&& Swings.strength(player) >= 0.9f) {
			CardCooldowns.start(player, CardCooldowns.SPLIT, 200);
			Vec3 dir = Vec3.directionFromRotation(0, player.getYRot()).normalize();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(9))) {
				if (e == player || e == target || e instanceof Player p && CardHooks.allies(player, p)) {
					continue;
				}
				Vec3 rel = e.position().subtract(player.position());
				double along = rel.dot(dir);
				double side = rel.subtract(dir.scale(along)).horizontalDistance();
				if (along > 0 && along <= 8 && side < 1.5 && Math.abs(rel.y) < 3) {
					strike(level, e, player.damageSources().playerAttack(player), taken / 2);
					e.push(0, 0.6, 0);
				}
			}
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, player.getX() + dir.x * 4, player.getY(), player.getZ() + dir.z * 4,
				3, dir.x * 2, 0.1, dir.z * 2, 0);
		}
	}

	private static void strike(ServerLevel level, LivingEntity target, DamageSource source, float damage) {
		inner = true;
		int cooldown = target.damageCooldownTime;
		try {
			target.damageCooldownTime = 0;
			target.hurtServer(level, source, damage);
		} finally {
			inner = false;
			if (target.isAlive()) {
				target.damageCooldownTime = cooldown;
			}
		}
	}

	// ---------------------------------------------------------------- effects (called from CardHooks.effect and a mixin)

	/** Witherbane: allies within 12 blocks of a holder take wither and poison at half strength for half as long. */
	static MobEffectInstance effect(LivingEntity entity, MobEffectInstance effect) {
		Holder<MobEffect> kind = effect.getEffect();
		if (!(entity instanceof ServerPlayer player) || !(kind.equals(MobEffects.WITHER) || kind.equals(MobEffects.POISON)) || effect.isInfiniteDuration()) {
			return effect;
		}
		for (ServerPlayer other : player.level().players()) {
			if (CardHooks.helps(other, player) && other.distanceToSqr(player) <= 144 && has(other, "witherbane")) {
				return new MobEffectInstance(kind, Math.max(1, effect.getDuration() / 2), effect.getAmplifier() / 2, effect.isAmbient(), effect.isVisible(),
					effect.showIcon());
			}
		}
		return effect;
	}

	/** Witherbane: wither and poison never touch a holder (LivingEntity#canBeAffected). */
	public static boolean canBeAffected(LivingEntity entity, MobEffectInstance effect, boolean original) {
		if (original && entity instanceof Player player && (effect.getEffect().equals(MobEffects.WITHER) || effect.getEffect().equals(MobEffects.POISON))
			&& has(player, "witherbane")) {
			return false;
		}
		return original;
	}

	/** The Warden's Hush: a holder makes no vibrations, nor do allies sneaking within 6 blocks of one. */
	public static boolean hushed(@Nullable Entity source) {
		if (!(source instanceof Player player)) {
			return false;
		}
		if (has(player, "wardens_hush")) {
			return true;
		}
		if (!player.isShiftKeyDown()) {
			return false;
		}
		for (Player other : player.level().players()) {
			if (other.distanceToSqr(player) <= 36 && CardHooks.helps(other, player) && has(other, "wardens_hush")) {
				return true;
			}
		}
		return false;
	}

	/** Pardon: whether this dying player keeps their items and XP (Player#dropEquipment, LivingEntity#dropExperience). */
	public static boolean pardoned(LivingEntity entity) {
		return entity instanceof Player p && PARDONED.contains(p.getUUID());
	}

	/** First of Masters (Mentor): allies within 16 blocks learn 20 % faster in skills where the holder is ahead. */
	private static double mentor(ServerPlayer player, ResourceKey<Skill> skill) {
		int mine = CharacterStore.get(player).level(skill);
		for (ServerPlayer other : player.level().players()) {
			if (CardHooks.helps(other, player) && other.distanceToSqr(player) <= 256 && has(other, "first_of_masters")
				&& CharacterStore.get(other).level(skill) > mine) {
				return 1.2;
			}
		}
		return 1;
	}

	/** Trailmaker: no sprint hunger on paths for the holder and the allies it leads. */
	static boolean trailFree(Player player) {
		if (!player.isSprinting() || !dev.voidmc.mettle.character.skill.survival.Camp.onPath(player)) {
			return false;
		}
		if (has(player, "long_walker")) {
			return true;
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.getModifier(TRAILMAKER) != null;
	}

	/** The Open Hand: the best market edge of a holder within 8 blocks also counts for this player (compat.hearth). */
	public static double openHandEdge(ServerPlayer player, java.util.function.ToDoubleFunction<ServerPlayer> edge) {
		double best = 0;
		for (ServerPlayer other : player.level().players()) {
			if (CardHooks.helps(other, player) && other.distanceToSqr(player) <= 64 && has(other, "open_hand")) {
				best = Math.max(best, edge.applyAsDouble(other));
			}
		}
		return best;
	}

	/** The sets a Dragon Standard lends this player (keyword → tier). */
	public static Map<Identifier, Integer> borrowed(Player player) {
		return BORROWED.getOrDefault(player.getUUID(), Map.of());
	}

	// ---------------------------------------------------------------- active Mythics

	private static void raiseStandard(ServerPlayer player) {
		Map<Identifier, Integer> tiers = dev.voidmc.mettle.core.CardStore.get(player).tiers();
		Identifier best = null;
		int tier = 0;
		for (Map.Entry<Identifier, Integer> e : tiers.entrySet()) {
			if (e.getValue() > tier) {
				best = e.getKey();
				tier = e.getValue();
			}
		}
		CardCooldowns.start(player, CardCooldowns.STANDARD, 12000);
		if (best == null) {
			SignaturePerks.feedback(player, Mettle.id("mythic/dragonfall"), Component.translatable("mettle.mythic.fired.dragonfall.none"));
			return;
		}
		STANDARDS.put(player.getUUID(), new Standard((ServerLevel) player.level(), player.position(), now(player) + 1200, best, tier));
		SignaturePerks.feedback(player, Mettle.id("mythic/dragonfall"), Component.translatable("mettle.mythic.fired.dragonfall", CardText.keyword(player.level().registryAccess(), best), tier));
	}

	/**
	 * Heart of the Mountain: a block the bore may take. Stone-like, mined with a pickaxe, no harder than deepslate ores
	 * (so never obsidian, ancient debris, reinforced deepslate or anything unbreakable), harvestable with the pickaxe in
	 * hand, holding nothing (no block entity), and where this player may build (spawn protection, the world border,
	 * adventure mode). Claim mods are asked as for any break (ServerPlayerGameMode#destroyBlock fires Fabric's events).
	 */
	public static boolean boreable(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		return !state.isAir() && hardness >= 0 && hardness <= BORE_HARDNESS && MiningSkill.isStoneLike(state)
			&& state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)
			&& !state.is(net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN) && !state.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS)
			&& !state.is(net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE) && !state.hasBlockEntity() && player.hasCorrectToolForDrops(state)
			&& player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
			&& !level.getServer().isUnderSpawnProtection(level, pos, player);
	}

	/** The hardest block the bore takes (deepslate ores; deepslate itself is 3). */
	static final float BORE_HARDNESS = 4.5f;

	/** Bores 1 × 2, 8 deep, stopping at the first block it may not take; each block costs durability and drops as normal. */
	public static void bore(ServerPlayer player, BlockPos start) {
		CardCooldowns.start(player, CardCooldowns.BORE, 600);
		ServerLevel level = (ServerLevel) player.level();
		Direction dir = player.getDirection();
		dig:
		for (int d = 0; d < 8; d++) {
			for (int y = 0; y < 2; y++) {
				BlockPos pos = start.relative(dir, d).above(y);
				if (player.getMainHandItem().isEmpty() || !player.getMainHandItem().is(ItemTags.PICKAXES)) {
					break dig;
				}
				if (level.getBlockState(pos).isAir()) {
					continue;
				}
				if (!boreable(player, level, pos)) {
					break dig;
				}
				player.gameMode.destroyBlock(pos);
			}
		}
		SignaturePerks.feedback(player, Mettle.id("mythic/heart_of_the_mountain"), Component.translatable("mettle.mythic.fired.heart_of_the_mountain"));
	}

	/** Seven-League Stride: the client dashed; the server forgives the fall that follows (8 s cooldown). */
	private static void dashed(ServerPlayer player) {
		Memory mem = memory(player);
		if (!has(player, "seven_league_stride") || CardCooldowns.remaining(player, CardCooldowns.DASH) > 10) {
			return;
		}
		CardCooldowns.start(player, CardCooldowns.DASH, DASH_TICKS);
		mem.dashFalling = true;
		player.resetFallDistance();
	}

	/** An arrow a holder of The Seeking Fletch loosed at full draw bends towards the last mob they hit. */
	static void loosed(ServerPlayer player, AbstractArrow arrow) {
		if (arrow.isCritArrow() && has(player, "seeking_fletch")) {
			SEEKING.put(arrow, player.getUUID());
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 100 == 0) {
			// Last hits on mobs that are gone (killed, unloaded) are let go.
			LAST_HIT.values().removeIf(e -> e.isRemoved() || !e.isAlive());
		}
		steer(now);
		domes(server, now);
		boolean second = now % 20 == 0;
		boolean slow = now % 10 == 0;
		if (second) {
			standards(server, now);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Memory mem = MEMORY.get(player.getUUID());
			if (mem != null && mem.dashFalling) {
				player.resetFallDistance();
				if (player.onGround()) {
					mem.dashFalling = false;
				}
			}
			if (!slow) {
				continue;
			}
			if (PARDONED.contains(player.getUUID()) && player.isAlive()) {
				PARDONED.remove(player.getUUID());
			}
			timed(player, UNCLAD, has(player, "unclad") && Mythics.unclad(player) ? 0.10 : 0);
			trailmaker(player);
			if (has(player, "elders_eye")) {
				for (ServerPlayer p : player.level().players()) {
					if ((p == player || CardHooks.helps(player, p) && p.distanceToSqr(player) <= 144) && p.isInWater()) {
						MobEffectInstance e = p.getEffect(MobEffects.CONDUIT_POWER);
						if (e == null || e.getDuration() < 200) {
							p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, false, true), player);
						}
					}
				}
			}
			homeward(player, mem);
			aegis(player, now);
			if (second && has(player, "wild_hunt")) {
				hunt(player);
			}
		}
	}

	private static void trailmaker(ServerPlayer player) {
		double best = 0;
		for (ServerPlayer leader : player.level().players()) {
			if (CardHooks.helps(leader, player) && leader.distanceToSqr(player) <= 256 && has(leader, "long_walker")) {
				Vec3 behind = player.position().subtract(leader.position());
				if (behind.dot(leader.getLookAngle()) < 0) {
					best = 0.10;
				}
			}
		}
		timed(player, TRAILMAKER, best);
	}

	private static void homeward(ServerPlayer player, @Nullable Memory known) {
		if (!has(player, "far_horizon")) {
			return;
		}
		Memory mem = known == null ? memory(player) : known;
		boolean compass = player.getMainHandItem().is(ItemTags.COMPASSES) || player.getOffhandItem().is(ItemTags.COMPASSES);
		if (!compass || !player.isShiftKeyDown()) {
			mem.homeward = 0;
			return;
		}
		mem.homeward += 10;
		if (mem.homeward < 100) {
			return;
		}
		mem.homeward = 0;
		homeward(player);
	}

	/**
	 * Homeward (The Far Horizon) fires: once per in-game day, to where a real respawn would put this player (the bed or
	 * charged anchor, on a safe standing spot beside it by vanilla's own rules, else the world spawn; an anchor's charge
	 * is not spent). False when it is cooling down or the Mythic isn't active.
	 */
	public static boolean homeward(ServerPlayer player) {
		if (!has(player, "far_horizon") || !CardCooldowns.ready(player, CardCooldowns.HOMEWARD)) {
			return false;
		}
		CardCooldowns.start(player, CardCooldowns.HOMEWARD, 24000);
		net.minecraft.world.level.portal.TeleportTransition home = player.findRespawnPositionAndUseSpawnBlock(false,
			net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
		if (home.missingRespawnBlock()) {
			player.sendSystemMessage(Component.translatable("block.minecraft.spawn.not_valid"));
		}
		player.teleport(home);
		player.resetFallDistance();
		SignaturePerks.feedback(player, Mettle.id("mythic/far_horizon"), Component.translatable("mettle.mythic.fired.far_horizon"));
		return true;
	}

	private static void aegis(ServerPlayer player, long now) {
		if (!has(player, "aegis")) {
			return;
		}
		Memory mem = memory(player);
		if (!player.isBlocking()) {
			mem.blockingSince = -1;
			return;
		}
		if (mem.blockingSince < 0) {
			mem.blockingSince = now;
		}
		if (now - mem.blockingSince >= 20 && CardCooldowns.ready(player, CardCooldowns.AEGIS)) {
			CardCooldowns.start(player, CardCooldowns.AEGIS, 900);
			DOMES.put(player.getUUID(), new Dome((ServerLevel) player.level(), player.position(), now + 80));
			SignaturePerks.feedback(player, Mettle.id("mythic/aegis"), Component.translatable("mettle.mythic.fired.aegis"));
		}
	}

	/** Every tick: projectiles fired from outside a raised Aegis dome stop at its edge. */
	private static void domes(MinecraftServer server, long now) {
		if (DOMES.isEmpty()) {
			return;
		}
		DOMES.values().removeIf(d -> now > d.until());
		for (Dome d : DOMES.values()) {
			double r = 3;
			for (Projectile p : d.level().getEntitiesOfClass(Projectile.class, new AABB(d.at(), d.at()).inflate(r + 2))) {
				Entity owner = p.getOwner();
				boolean fromOutside = owner == null || owner.position().distanceTo(d.at()) > r;
				if (fromOutside && p.position().distanceTo(d.at()) <= r + 1.5) {
					p.discard();
				}
			}
			d.level().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, d.at().x, d.at().y + 1.5, d.at().z, 2, r, 1.5, r, 0);
		}
	}

	/** Every tick: Seeking Fletch arrows turn towards their holder's last target when it lies within 20°. */
	private static void steer(long now) {
		if (SEEKING.isEmpty()) {
			return;
		}
		for (Map.Entry<AbstractArrow, UUID> e : List.copyOf(SEEKING.entrySet())) {
			AbstractArrow arrow = e.getKey();
			LivingEntity target = LAST_HIT.get(e.getValue());
			if (!arrow.isAlive() || ((dev.voidmc.mettle.mixin.cards.ArrowAccess) arrow).mettle$isInGround() || target == null || !target.isAlive() || target.level() != arrow.level()) {
				SEEKING.remove(arrow);
				continue;
			}
			Vec3 v = arrow.getDeltaMovement();
			double speed = v.length();
			if (speed < 0.1) {
				continue;
			}
			Vec3 to = target.getEyePosition().subtract(arrow.position()).normalize();
			Vec3 dir = v.scale(1 / speed);
			if (dir.dot(to) >= Math.cos(Math.toRadians(20))) {
				Vec3 bent = dir.scale(0.7).add(to.scale(0.3)).normalize().scale(speed);
				arrow.setDeltaMovement(bent);
				arrow.needsSync = true;
			}
		}
	}

	private static void standards(MinecraftServer server, long now) {
		Map<UUID, Map<Identifier, Integer>> next = new HashMap<>();
		STANDARDS.values().removeIf(s -> now > s.until());
		for (Map.Entry<UUID, Standard> e : STANDARDS.entrySet()) {
			Standard s = e.getValue();
			ServerPlayer holder = server.getPlayerList().getPlayer(e.getKey());
			for (ServerPlayer p : s.level().players()) {
				if (p.position().distanceTo(s.at()) <= 24 && (holder == null || p == holder || CardHooks.helps(holder, p))) {
					next.computeIfAbsent(p.getUUID(), u -> new HashMap<>()).merge(s.keyword(), s.tier(), Math::max);
				}
			}
		}
		Set<UUID> changed = new HashSet<>(BORROWED.keySet());
		changed.addAll(next.keySet());
		for (UUID id : changed) {
			Map<Identifier, Integer> before = BORROWED.getOrDefault(id, Map.of());
			Map<Identifier, Integer> after = next.getOrDefault(id, Map.of());
			if (!before.equals(after)) {
				if (after.isEmpty()) {
					BORROWED.remove(id);
				} else {
					BORROWED.put(id, after);
				}
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					CardEffects.apply(p);
				}
			}
		}
	}

	/** The Wild Hunt: up to three wild wolves in the holder's biome run with them and fight what they fight. */
	private static void hunt(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Holder<Biome> biome = level.getBiome(player.blockPosition());
		List<Wolf> pack = PACKS.computeIfAbsent(player.getUUID(), u -> new ArrayList<>());
		pack.removeIf(w -> !w.isAlive() || w.isTame() || w.distanceToSqr(player) > 48 * 48 || !level.getBiome(w.blockPosition()).equals(biome));
		if (pack.size() < 3) {
			for (Wolf w : level.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(24))) {
				if (pack.size() >= 3) {
					break;
				}
				if (w.getType() == EntityTypes.WOLF && !w.isTame() && !pack.contains(w) && level.getBiome(w.blockPosition()).equals(biome)) {
					pack.add(w);
				}
			}
		}
		LivingEntity foe = player.getLastHurtMob() != null && player.getLastHurtMob().isAlive() ? player.getLastHurtMob()
			: player.getLastHurtByMob() != null && player.getLastHurtByMob().isAlive() ? player.getLastHurtByMob() : null;
		for (Wolf w : pack) {
			if (w.getTarget() == player) {
				w.setTarget(null);
			}
			if (foe != null && foe != player && !(foe instanceof Wolf) && foe.distanceToSqr(player) <= 256) {
				w.setTarget(foe);
			} else if (w.distanceToSqr(player) > 100) {
				w.getNavigation().moveTo(player, 1.2);
			}
		}
	}

	private static void timed(ServerPlayer player, Identifier id, double amount) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier current = speed.getModifier(id);
		if (amount <= 0) {
			if (current != null) {
				speed.removeModifier(id);
			}
			return;
		}
		if (current == null || current.amount() != amount) {
			speed.removeModifier(id);
			speed.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
