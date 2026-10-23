package dev.voidmc.bestiary.boss;

import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.anim.GallowsKnight;
import dev.voidmc.bestiary.api.MoveBehaviour;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.Timeline;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Gallows Knight's move behaviour ({@code "behaviour": "knight"} in its profile). The clip owns the timing, the tell
 * and the melee capsules (hook, chain, shoulder, foot); this adds, by move:
 *
 * <ul>
 * <li>for every move: which phases it belongs to ({@code params.phases}: "1", "2", "12"), the "one red in any three"
 * rule, phase 2's shorter gaps (×0.65) and its combos ({@code params.combo}: the follow-up it forces in phase 2);</li>
 * <li>cuts, slam, kick: a step in as the strike begins ({@code power}); the slam's impact marks the ground;</li>
 * <li>shoulder charge: three blocks of charge over the ACTIVE ticks ({@code charge}, blocks a tick);</li>
 * <li>chain lash (purple): on the release the chain flies at the target; unless dodged it bites (6) and pulls them in;</li>
 * <li>the Hanging (red ultimate): the mound is marked on the ground through the 24-tick tell; on the drop, everyone on
 * the mound who didn't dodge takes 26;</li>
 * <li>tear cage (the transition): on its {@code tear} marker the cage comes off ({@link KnightScript#tearOff}).</li>
 * </ul>
 */
public final class KnightMoves implements MoveBehaviour {
	/** Phase 2's gaps between uses of a move, as a share of phase 1's. */
	public static final float PHASE_2_COOLDOWN = 0.65F;

	/** Specials that landed or missed (tests): move, target id, tick, outcome. */
	public record Special(String move, int target, long tick, String outcome) {
	}

	public static final List<Special> SPECIALS = new CopyOnWriteArrayList<>();

	static GallowsKnight knight(Mob mob) {
		return (GallowsKnight) mob;
	}

	static int phase(Mob mob) {
		BossData d = BossData.get(mob);
		return d == null ? 1 : d.phase;
	}

	static String colour(MoveDef m) {
		Timeline tl = MotionServer.timeline(m.clip());
		return tl == null || tl.tellColour.isEmpty() ? "white" : tl.tellColour;
	}

	static int event(Timeline tl, String name) {
		for (Timeline.Event e : tl.events) {
			if (e.name().equals(name)) {
				return e.tick();
			}
		}
		return -1;
	}

	@Override
	public boolean canStart(ServerLevel level, Mob mob, Brain brain, MoveDef move, LivingEntity target) {
		if (!(mob instanceof GallowsKnight k) || move.tagged("transition") || k.script.transitioning()) {
			return false;
		}
		String phases = move.params().has("phases") ? move.params().get("phases").getAsString() : "12";
		if (phases.indexOf((char) ('0' + phase(mob))) < 0) {
			return false;
		}
		if (colour(move).equals("red") && !BossMath.redAllowed(k.script.recent())) {
			return false;
		}
		if (move.param("mound", 0) > 0) {
			// The Hanging needs a gallows mound with someone on it.
			BossData d = BossData.get(mob);
			if (d == null || d.arena.mound().isEmpty()) {
				return false;
			}
			boolean someone = false;
			for (LivingEntity e : BossEncounter.inside(d, level)) {
				someone |= d.arena.onMound(e.position());
			}
			return someone;
		}
		return true;
	}

	@Override
	public void start(Context ctx) {
		GallowsKnight k = knight(ctx.mob());
		k.script.remember(colour(ctx.move()));
		if (phase(ctx.mob()) >= 2 && ctx.move().cooldown() > 0) {
			Brain b = ctx.brain();
			long ready = b.ready.getOrDefault(ctx.move().id(), 0L);
			long gap = ready - (ctx.start() + ctx.timeline().ticks);
			b.ready.put(ctx.move().id(), ctx.start() + ctx.timeline().ticks + (long) (gap * PHASE_2_COOLDOWN));
		}
	}

	@Override
	public void tick(Context ctx, int tick) {
		Mob mob = ctx.mob();
		MoveDef m = ctx.move();
		Timeline tl = ctx.timeline();
		switch (m.id()) {
			case "shoulder_charge" -> this.charge(ctx, tick);
			case "chain_lash" -> {
				if (tick == event(tl, "release")) {
					this.lash(ctx);
				}
			}
			case "the_hanging" -> this.hanging(ctx, tick);
			case "tear_cage" -> {
				if (tick == event(tl, "tear") && mob.level() instanceof ServerLevel level) {
					knight(mob).script.tearOff(level);
				}
			}
			default -> {
				if (m.param("power", 0) > 0 && mob.onGround()) {
					for (int[] s : tl.strikes()) {
						if (tick == s[0] - 1) {
							Vec3 d = toward(mob, ctx.target()).scale(m.param("power", 0.25F));
							mob.setDeltaMovement(mob.getDeltaMovement().add(d.x, 0.05, d.z));
							mob.needsSync = true;
						}
					}
				}
				if (m.id().equals("slam") && tick == event(tl, "impact")) {
					this.impact(ctx);
				}
			}
		}
	}

	@Override
	public void end(Context ctx, boolean interrupted) {
		Mob mob = ctx.mob();
		if (ctx.move().id().equals("tear_cage") && mob.level() instanceof ServerLevel level) {
			// Interrupted before the tear (a stagger): the cage still comes off, phase 2 needs it.
			knight(mob).script.tearOff(level);
		}
		if (!interrupted && ctx.move().params().has("combo") && phase(mob) >= (int) ctx.move().param("combo_phase", 2)) {
			String next = ctx.move().params().get("combo").getAsString();
			Brain b = ctx.brain();
			MoveDef nm = b.profile.move(next);
			if (nm != null && b.ready(next, ctx.level().getGameTime()) && ctx.target().isAlive() && mob.distanceTo(ctx.target()) <= nm.maxRange() + 0.5
				&& (!colour(nm).equals("red") || BossMath.redAllowed(knight(mob).script.recent()))) {
				b.force(next);
			}
		}
	}

	static Vec3 toward(Mob mob, LivingEntity t) {
		Vec3 d = t.position().subtract(mob.position()).multiply(1, 0, 1);
		return d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize();
	}

	static Vec3 facing(Mob mob) {
		double r = Math.toRadians(mob.getYRot() + 90);
		return new Vec3(Math.cos(r), 0, Math.sin(r));
	}

	/** The shoulder charge: driven forward over the ACTIVE ticks (three blocks), stopping at a wall. */
	void charge(Context ctx, int tick) {
		Mob mob = ctx.mob();
		Timeline tl = ctx.timeline();
		if (tick >= tl.activeStart - 1 && tick < tl.activeEnd) {
			Vec3 f = facing(mob).scale(ctx.move().param("charge", 0.6F));
			mob.setDeltaMovement(f.x, mob.getDeltaMovement().y, f.z);
			mob.needsSync = true;
			if (ctx.level() instanceof ServerLevel level && tick % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.1, mob.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
			}
		} else if (tick == tl.activeEnd) {
			mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1, 0.2));
			mob.needsSync = true;
		}
	}

	/** The slam's impact: dust and a crack on the ground where the hook bites (the area it struck, shown). */
	void impact(Context ctx) {
		Mob mob = ctx.mob();
		Vec3 at = mob.position().add(facing(mob).scale(1.8));
		ServerLevel level = ctx.level();
		BlockPos below = BlockPos.containing(at.x, mob.getY() - 0.5, at.z);
		var state = level.getBlockState(below);
		if (!state.isAir()) {
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), at.x, mob.getY() + 0.1, at.z, 40, 0.8, 0.05, 0.8,
				0.15);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, mob.getY() + 0.2, at.z, 8, 0.6, 0.05, 0.6, 0.01);
	}

	/** The chain lash: the hook flies at the target; unless dodged, it bites and drags them in. */
	void lash(Context ctx) {
		Mob mob = ctx.mob();
		LivingEntity t = ctx.target();
		ServerLevel level = ctx.level();
		Vec3 hand = mob.position().add(facing(mob).scale(0.6)).add(0, 1.9, 0);
		Vec3 aim = t.getEyePosition().add(0, -0.4, 0);
		ItemParticleOption chain = new ItemParticleOption(ParticleTypes.ITEM, Items.IRON_CHAIN);
		int n = (int) Math.max(6, hand.distanceTo(aim) * 3);
		for (int i = 0; i <= n; i++) {
			Vec3 p = hand.lerp(aim, i / (double) n);
			level.sendParticles(chain, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
		}
		level.playSound(null, mob.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2F, 1.3F);
		boolean reaches = mob.distanceTo(t) <= ctx.move().maxRange() + 1.5 && mob.getSensing().hasLineOfSight(t);
		GuardOutcome g = t instanceof ServerPlayer ? FrayApi.defense().preview(t, mob, HitFlags.GRAB | HitFlags.UNBLOCKABLE) : GuardOutcome.NONE;
		boolean dodged = g == GuardOutcome.DODGED || g == GuardOutcome.SLIP || FrayApi.actions().invulnerable(t);
		long now = level.getGameTime();
		if (!reaches || dodged) {
			SPECIALS.add(new Special("chain_lash", t.getId(), now, dodged ? "dodged" : "missed"));
			return;
		}
		HitContext hit = new HitContext(mob, ItemStack.EMPTY, ctx.move().clip(), DamageSpec.of(DamageKind.PIERCE, ctx.move().param("damage", 6), 2),
			HitFlags.MELEE | HitFlags.GRAB | HitFlags.UNBLOCKABLE, 0, null);
		FrayApi.damage().deal(t, hit);
		// The pull: lands them a couple of blocks in front of the knight.
		Vec3 to = mob.position().subtract(t.position()).multiply(1, 0, 1);
		double d = to.length();
		if (d > 2.5) {
			Vec3 v = to.normalize().scale(Math.min(1.8, (d - 2.0) * 0.21));
			t.setDeltaMovement(v.x, 0.38, v.z);
			t.needsSync = true;
			t.resetFallDistance();
		}
		FrayApi.statuses().apply(t, StatusType.ROOTED, 6, mob);
		SPECIALS.add(new Special("chain_lash", t.getId(), now, "pulled"));
	}

	/** The Hanging: the mound marked through the tell; on the drop, everyone on it who didn't dodge is struck. */
	void hanging(Context ctx, int tick) {
		Mob mob = ctx.mob();
		ServerLevel level = ctx.level();
		BossData d = BossData.get(mob);
		if (d == null || d.arena.mound().isEmpty()) {
			return;
		}
		BlockPos m = d.arena.mound().get();
		int drop = event(ctx.timeline(), "drop");
		double r = d.arena.moundRadius() + 0.5;
		if (tick < drop && tick % 3 == 0) {
			// The area, shown on the ground: a ring of red dust round the mound's edge and grave dust inside it.
			DustParticleOptions red = new DustParticleOptions(0xB3262A, 1.4F);
			for (int i = 0; i < 24; i++) {
				double a = i * Math.PI * 2 / 24 + tick * 0.05;
				level.sendParticles(red, m.getX() + 0.5 + Math.cos(a) * r, m.getY() + 0.15, m.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			level.sendParticles(ParticleTypes.ASH, m.getX() + 0.5, m.getY() + 0.3, m.getZ() + 0.5, 12, r * 0.5, 0.05, r * 0.5, 0);
		}
		if (tick != drop) {
			return;
		}
		ItemParticleOption chain = new ItemParticleOption(ParticleTypes.ITEM, Items.IRON_CHAIN);
		for (int i = 0; i <= 14; i++) {
			level.sendParticles(chain, m.getX() + 0.5, m.getY() + 5.5 - i * 0.38, m.getZ() + 0.5, 2, 0.05, 0.05, 0.05, 0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, m.getX() + 0.5, m.getY() + 0.4, m.getZ() + 0.5, 2, r * 0.4, 0.1, r * 0.4, 0);
		level.playSound(null, m, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.5F);
		long now = level.getGameTime();
		for (LivingEntity e : BossEncounter.inside(d, level)) {
			if (!d.arena.onMound(e.position())) {
				continue;
			}
			GuardOutcome g = e instanceof ServerPlayer ? FrayApi.defense().preview(e, mob, HitFlags.UNBLOCKABLE) : GuardOutcome.NONE;
			if (g == GuardOutcome.DODGED || g == GuardOutcome.SLIP || FrayApi.actions().invulnerable(e)) {
				SPECIALS.add(new Special("the_hanging", e.getId(), now, "dodged"));
				continue;
			}
			HitContext hit = new HitContext(mob, ItemStack.EMPTY, ctx.move().clip(), DamageSpec.of(DamageKind.BLUNT, ctx.move().param("damage", 26), 4),
				HitFlags.MELEE | HitFlags.UNBLOCKABLE | HitFlags.AOE, 0, null);
			FrayApi.damage().deal(e, hit);
			SPECIALS.add(new Special("the_hanging", e.getId(), now, "struck"));
		}
	}
}
