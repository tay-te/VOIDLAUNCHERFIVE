package dev.voidmc.fray.client.combat.hud;

import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.client.combat.CombatInput;
import dev.voidmc.fray.client.combat.FrayClientOptions;
import dev.voidmc.fray.client.combat.FrayCombatClient;
import dev.voidmc.fray.client.combat.LockOn;
import dev.voidmc.fray.combat.net.CombatPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * VOID's native combat HUD (Figma file 3c0Wgc4ZKFSpm98fWJoARS, section 723:50512 "3a · ARPG stage 1"), drawn every
 * frame with flat fills: H1 stamina, H2 health up to 40 with absorption and build-up meters, H3 the status row (vanilla
 * effects move into it), H4 lock-on reticle and target frame, H5 nameplates, H6 boss bar, H7 encounter banners, H8 hit
 * feedback, H9 shape-coded telegraph cues, H10 the Art's well. Vanilla hearts and effect icons step aside while it
 * shows; food and air move up a row; vanilla boss bars move below Fray's. Atlas keeps the top right, Hearth's tracker
 * reads {@link dev.voidmc.fray.client.combat.FrayHudState} to fold during a boss fight.
 */
public final class FrayHud {
	public static boolean clusterShown;
	public static int clusterTop;
	/** Draw cost probes (perf test). */
	public static long drawNanos;
	public static long frames;
	/** Forces the HUD "in a fight" (screenshots). */
	public static boolean forceCombat;

	private static final Paint P = new Paint();
	private static float lastHealth = -1;
	private static float ghostHealth;
	private static long ghostAt;
	private static float lastTargetHealth = -1;
	private static float targetGhost;
	private static long targetGhostAt;
	private static int lastTargetId = -1;
	private static float lastBossHealth = -1;
	private static float bossGhost;
	private static long bossGhostAt;
	private static long staminaFullSince = -1;
	private static float[] reticleFrom;
	private static float[] reticleLast;
	private static int lastArtCooldown;
	private static long artReadyFlash;
	private static int topHeight;

	private FrayHud() {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return FrayClientOptions.get().hud && mc.player != null && FrayCombatClient.serverHasFray();
	}

	public static boolean clusterActive() {
		Minecraft mc = Minecraft.getInstance();
		return active() && !mc.player.isCreative() && !mc.player.isSpectator();
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Fray.id("combat_hud"), FrayHud::extract);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, old -> (g, d) -> {
			if (!clusterActive()) {
				old.extractRenderState(g, d);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, old -> (g, d) -> {
			if (!active()) {
				old.extractRenderState(g, d);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, old -> shifted(old, () -> clusterActive() ? -10 : 0));
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, old -> shifted(old, () -> clusterActive() ? -10 : 0));
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR,
			old -> shifted(old, () -> clusterActive() ? HudStatusBarHeightRegistry.getElementHeight(VanillaHudElements.HEALTH_BAR) - 10 : 0));
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, old -> shifted(old, () -> active() ? topHeight : 0));
	}

	private static HudElement shifted(HudElement old, java.util.function.IntSupplier dy) {
		return (g, d) -> {
			int y = dy.getAsInt();
			if (y == 0) {
				old.extractRenderState(g, d);
				return;
			}
			g.pose().pushMatrix();
			g.pose().translate(0, y);
			old.extractRenderState(g, d);
			g.pose().popMatrix();
		};
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		clusterShown = false;
		if (!active() || mc.gui.hud.isHidden()) {
			topHeight = 0;
			return;
		}
		long t0 = System.nanoTime();
		LocalPlayer player = mc.player;
		FrayClientOptions opt = FrayClientOptions.get();
		Paint p = P;
		p.begin(g, mc, opt.hudScale);
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Project.setup(mc, p.guiW, p.guiH);
		long now = ClientCombat.ms();

		cues(p, mc, opt, now, true);
		if (opt.nameplates != FrayClientOptions.Nameplates.OFF) {
			nameplates(p, mc, opt, partial);
		}
		reticle(p, mc, partial, now);
		feedback(p, mc, opt, now);
		cues(p, mc, opt, now, false);
		topHeight = 0;
		if (ClientCombat.bar != null) {
			bossBar(p, mc, now);
			topHeight = Math.round(112 * p.k);
		} else if (LockOn.target >= 0) {
			targetFrame(p, mc, now);
			topHeight = Math.round(128 * p.k);
		}
		if (clusterActive()) {
			cluster(p, mc, player, now);
			arts(p, mc, now);
			clusterShown = true;
			clusterTop = Math.round((980 - 880) * p.k) + 22;
		}
		if (mc.gui.screen() == null) {
			statusRow(p, mc, player, now);
		}
		banner(p, now);
		tellSubtitles(p, mc, now);
		if (opt.damageNumbers) {
			numbers(p, now);
		}
		drawNanos += System.nanoTime() - t0;
		frames++;
	}

	private static boolean inFight() {
		CombatPayloads.Self s = ClientCombat.self;
		return forceCombat || s != null && (s.flags() & CombatPayloads.SELF_IN_COMBAT) != 0 || LockOn.target >= 0 || ClientCombat.bar != null;
	}

	// ------------------------------------------------------------------ H1 · H2 the player cluster

	private static void cluster(Paint p, Minecraft mc, LocalPlayer pl, long now) {
		p.pushBottom();
		float max = Math.max(1, pl.getMaxHealth());
		float hp = Math.max(0, Math.min(max, pl.getHealth()));
		float abs = pl.getAbsorptionAmount();
		if (lastHealth < 0) {
			lastHealth = hp;
		}
		if (hp < lastHealth - 0.01F) {
			ghostHealth = Math.max(ghostHealth > hp && now - ghostAt < 600 ? ghostHealth : 0, lastHealth);
			ghostAt = now;
		}
		lastHealth = hp;
		float w = 160 + 3 * Math.min(40, max);
		float left = 950 - w;
		float y = 937;
		boolean low = hp <= max * 0.25F;
		// Numerals on a plate, left of the bar.
		String num = Integer.toString(Mth.ceil(hp));
		String of = "/" + Mth.ceil(max);
		float nw = p.width(num, 10) + p.width(of, 7) + 2;
		float plateW = Math.max(44, nw + 14);
		p.round(left - plateW - 6, 933, plateW, 22, 11, Paint.ink(0.72F));
		float tx = left - 6 - plateW + (plateW - nw) / 2;
		tx += p.text(num, tx, 939, low ? Paint.LOW : Paint.TEXT, 10) + 1;
		p.text(of, tx, 941.5F, Paint.DIM, 7);
		// Track, health, absorption, recent damage.
		p.round(left, y, w, 12, 6, Paint.ink(0.72F));
		if (low) {
			p.outline(left, y, w, 12, 6, 1, Paint.alpha(Paint.LOW, 0.9F));
		}
		float inner = w - 4;
		float hpW = inner * hp / Math.max(max, hp + abs > max ? max : max);
		float ghostF = now - ghostAt < 600 ? 1 - (now - ghostAt) / 600F : 0;
		if (ghostF > 0 && ghostHealth > hp) {
			float gw = inner * Math.min(max, ghostHealth) / max;
			p.round(left + 2, y + 2, gw, 8, 4, Paint.alpha(Paint.RECENT, ghostF));
		}
		p.round(left + 2, y + 2, hpW, 8, 4, low ? Paint.LOW : Paint.HEALTH);
		if (abs > 0) {
			float aw = Math.min(inner - hpW, inner * abs / max);
			if (aw > 0.5F) {
				p.round(left + 2 + hpW - 1, y + 2, aw + 1, 8, 4, Paint.ABSORB);
			}
		}
		// Build-up meters under the bar: fullest first, at most 4, then "+N".
		meterRow(p, pl.getId(), left, 957, 70, 44, 14, now, true);

		// H1 stamina, right of health.
		CombatPayloads.Self s = ClientCombat.self;
		float st = ClientCombat.stamina();
		float smax = s == null ? 100 : Math.max(1, s.max());
		boolean full = st >= smax - 0.01F;
		boolean fight = inFight();
		if (!full || fight) {
			staminaFullSince = -1;
		} else if (staminaFullSince < 0) {
			staminaFullSince = now;
		}
		float fade = staminaFullSince < 0 ? 1 : now - staminaFullSince < 3000 ? 1 : Math.max(0, 1 - (now - staminaFullSince - 3000) / 300F);
		if (fade > 0) {
			float sx = 968, sw = 280;
			boolean winded = s != null && s.windedLeft() > 0;
			int fillCol = winded ? Paint.WINDED : Paint.STAMINA;
			p.round(sx, y, sw, 12, 6, Paint.ink(0.72F * fade));
			if (winded) {
				p.outline(sx, y, sw, 12, 6, 1, Paint.alpha(Paint.WINDED, fade));
			}
			float fw = (sw - 4) * st / smax;
			float ghost = CombatInput.ghostCost(mc);
			if (ghost > 0 && !winded) {
				float keep = (sw - 4) * Math.max(0, st - ghost) / smax;
				p.round(sx + 2, y + 2, fw, 8, 4, Paint.alpha(fillCol, 0.34F * fade));
				p.round(sx + 2, y + 2, keep, 8, 4, Paint.alpha(fillCol, fade));
				p.rect(sx + 2 + keep, y + 2, 1, 8, Paint.ink(fade));
			} else {
				p.round(sx + 2, y + 2, Math.max(winded ? 8 : 0, fw), 8, 4, Paint.alpha(fillCol, fade));
			}
			// A refund (parry +15) flashes white.
			if (now - ClientCombat.refundAt < 700 && ClientCombat.refund > 0) {
				float f = 1 - (now - ClientCombat.refundAt) / 700F;
				float rw = (sw - 4) * ClientCombat.refund / smax;
				p.round(sx + 2 + Math.max(0, fw - rw), y + 2, Math.min(fw, rw), 8, 4, Paint.alpha(0xFFFFFFFF, f * fade));
				p.round(1252, 933, 34, 21, 10, Paint.ink(0.72F * f));
				p.centred("+" + Math.round(ClientCombat.refund), 1269, 939, Paint.alpha(Paint.TEXT, f), 8);
			}
			// The regen delay: a 2 px line under the bar, shrinking over 12 ticks.
			if (s != null && s.regenDelayLeft() > 0 && !full) {
				float age = (now - ClientCombat.selfAt) / 50F;
				float left12 = Math.max(0, s.regenDelayLeft() - age);
				p.rect(sx + 6, y + 14, (sw - 12) * left12 / 12F, 2, Paint.alpha(Paint.STAMINA, 0.6F * fade));
			}
			if (winded) {
				p.round(1252, 933, 54, 21, 10, Paint.ink(0.72F * fade));
				p.text("Winded", 1259, 939, Paint.alpha(Paint.WINDED, fade), 8);
			}
		}
		p.pop();
	}

	/** A row of build-up meters for an entity (H2 under health, H4, H6). */
	private static void meterRow(Paint p, int entity, float x, float y, float step, float meterW, float icon, long now, boolean self) {
		List<StatusType> list = new ArrayList<>();
		for (StatusType s : StatusType.METERED) {
			boolean flashing = self && now - ClientCombat.PROC_AT[s.ordinal()] < 500;
			if (ClientCombat.meter(entity, s) > 0.004F || flashing) {
				list.add(s);
			}
		}
		list.sort(Comparator.comparingDouble(s -> -ClientCombat.meter(entity, s)));
		int shown = Math.min(4, list.size());
		for (int i = 0; i < shown; i++) {
			StatusType s = list.get(i);
			float mx = x + i * step;
			float f = ClientCombat.meter(entity, s);
			boolean flash = self && now - ClientCombat.PROC_AT[s.ordinal()] < 500;
			if (flash) {
				p.round(mx - 3, y - 3, icon + 6, icon + 6, 5, Paint.alpha(s.colour, 0.45F));
				f = 1;
			}
			Icons.status(p, s, mx, y, icon, 1);
			p.bar(mx + icon + 4, y + icon / 2 - 2, meterW, 4, f, Paint.ink(0.6F), s.colour, 0);
		}
		if (list.size() > shown) {
			p.text("+" + (list.size() - shown), x + shown * step, y + 2, Paint.DIM, 8);
		}
	}

	// ------------------------------------------------------------------ H3 status row

	private record Chip(StatusType fray, MobEffectInstance vanilla, int left, int total, boolean harmful) {
	}

	private static void statusRow(Paint p, Minecraft mc, LocalPlayer pl, long now) {
		List<Chip> chips = new ArrayList<>();
		float elapsedTicks = (now - ClientCombat.statusesAt) / 50F;
		for (CombatPayloads.StatusLine l : ClientCombat.statuses) {
			StatusType s = StatusType.VALUES[l.status()];
			int left = (int) (l.left() - elapsedTicks);
			if (left > 0 && s != StatusType.STAGGER && s != StatusType.DAZE && s != StatusType.SHOCK) {
				chips.add(new Chip(s, null, left, l.total(), s.harmful));
			}
		}
		chips.sort(Comparator.comparing((Chip c) -> !c.harmful()).thenComparing(c -> c.fray().ordinal()));
		for (MobEffectInstance e : pl.getActiveEffects()) {
			if (e.showIcon()) {
				chips.add(new Chip(null, e, e.isInfiniteDuration() ? -1 : e.getDuration(), 0, !e.getEffect().value().isBeneficial()));
			}
		}
		if (chips.isEmpty()) {
			return;
		}
		p.pushBottom();
		float y = 880;
		if (clusterActive() && pl.getArmorValue() > 0) {
			y -= 34;
		}
		if (!clusterActive()) {
			y = 940;
		}
		int n = Math.min(8, chips.size());
		for (int i = 0; i < n; i++) {
			Chip c = chips.get(i);
			float x = 673 + i * 34;
			p.round(x, y, 30, 36, 8, Paint.ink(0.78F));
			int outlineCol = c.fray() != null ? c.fray().colour : 0x66FFFFFF;
			p.outline(x, y, 30, 36, 8, 1, outlineCol);
			if (c.fray() != null) {
				Icons.status(p, c.fray(), x + 8, y + 4, 14, 1);
				if (c.fray() == StatusType.CHILL) {
					int stacks = (int) Math.min(2, ClientCombat.meter(pl.getId(), StatusType.CHILL) * 3);
					for (int k = 0; k < stacks; k++) {
						p.rect(x + 20 + k * 4, y - 5, 3, 3, StatusType.CHILL.colour);
					}
				}
			} else {
				Holder<MobEffect> eff = c.vanilla().getEffect();
				p.g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(eff), Math.round(x + 7), Math.round(y + 3), 16, 16);
			}
			String t = c.left() < 0 ? "**" : time(c.left());
			boolean last3 = c.left() >= 0 && c.left() <= 60;
			int col = last3 ? (now / 250 % 2 == 0 ? 0x88F2F2EE : 0x55F2F2EE) : Paint.TEXT;
			p.centred(t, x + 15, y + 23, col, 6);
		}
		if (chips.size() > n) {
			float x = 673 + n * 34;
			p.round(x, y, 30, 36, 8, Paint.ink(0.6F));
			p.centred("+" + (chips.size() - n), x + 15, y + 14, Paint.DIM, 7);
		}
		p.pop();
	}

	private static String time(int ticks) {
		int s = ticks / 20;
		return s >= 60 ? s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60) : "0:" + String.format(Locale.ROOT, "%02d", s);
	}

	// ------------------------------------------------------------------ H10 the Art's well

	private static void arts(Paint p, Minecraft mc, long now) {
		CombatPayloads.Self s = ClientCombat.self;
		p.pushBottom();
		float x = 1259, y = 980, w = 74;
		p.round(x, y, w, w, 18, Paint.ink(0.72F));
		int cd = s == null ? 0 : Math.max(0, s.artCooldown() - (int) ((now - ClientCombat.selfAt) / 50));
		if (lastArtCooldown > 0 && cd == 0) {
			artReadyFlash = now;
		}
		lastArtCooldown = cd;
		if (s == null || s.art() < 0) {
			// A dashed outline: "No Art".
			p.ring(x + w / 2, y + w / 2, 30, 1.5F, 0x55FFFFFF, 0, 1, 24, true);
			p.centred("No Art", x + w / 2, y + 33, Paint.DIM, 7);
		} else {
			p.round(x + 9, y + 9, 56, 56, 12, Paint.ink(0.85F));
			boolean ready = cd == 0;
			if (ready) {
				p.outline(x + 9, y + 9, 56, 56, 12, 2, Paint.STAMINA);
				Icons.spiral(p, x + w / 2, y + w / 2, 30, Paint.STAMINA);
				if (now - artReadyFlash < 250) {
					p.round(x + 9, y + 9, 56, 56, 12, Paint.alpha(0xFFFFFFFF, 1 - (now - artReadyFlash) / 250F));
				}
			} else {
				Icons.spiral(p, x + w / 2, y + w / 2, 30, 0x55EDE6CF);
				float frac = s.artCooldownTotal() <= 0 ? 0 : cd / (float) s.artCooldownTotal();
				p.round(x + 9, y + 9 + 56 * (1 - frac), 56, 56 * frac, 8, Paint.ink(0.55F));
				p.centred(Integer.toString((cd + 19) / 20), x + w / 2, y + 30, Paint.TEXT, 12);
			}
		}
		// The keycap: sneak + C.
		p.round(x + 44, y - 8, 30, 18, 9, Paint.ink(0.85F));
		p.centred("⇧C", x + 59, y - 4, Paint.TEXT, 7);
		p.pop();
	}

	// ------------------------------------------------------------------ H4 lock-on reticle and target frame

	private static float[] chest(Entity e, float partial) {
		double x = Mth.lerp(partial, e.xo, e.getX());
		double y = Mth.lerp(partial, e.yo, e.getY()) + e.getBbHeight() * 0.6;
		double z = Mth.lerp(partial, e.zo, e.getZ());
		return Project.point(x, y, z);
	}

	private static void reticle(Paint p, Minecraft mc, float partial, long now) {
		if (LockOn.target >= 0) {
			Entity e = mc.level.getEntity(LockOn.target);
			if (e == null) {
				return;
			}
			float[] at = chest(e, partial);
			if (at == null) {
				return;
			}
			float x = at[0], y = at[1];
			if (LockOn.previous >= 0 && now - LockOn.switchedAt < 80 && reticleFrom != null) {
				float f = (now - LockOn.switchedAt) / 80F;
				x = Mth.lerp(f, reticleFrom[0], x);
				y = Mth.lerp(f, reticleFrom[1], y);
			} else {
				reticleFrom = new float[] {x, y};
			}
			reticleLast = new float[] {x, y};
			p.push(x, y);
			p.diamondOutline(0, 0, 32, 2, 0xF2FFFFFF);
			p.rect(-2, -2, 4, 4, 0xF2FFFFFF);
			p.pop();
		} else if (LockOn.lostId >= 0 && reticleLast != null && now - LockOn.lostAt < 500) {
			float f = 1 - (now - LockOn.lostAt) / 500F;
			p.push(reticleLast[0], reticleLast[1]);
			p.diamondOutline(0, 0, 32, 2, Paint.alpha(0xF2FFFFFF, f));
			p.pop();
		}
	}

	private static void targetFrame(Paint p, Minecraft mc, long now) {
		Entity e = mc.level.getEntity(LockOn.target);
		if (!(e instanceof LivingEntity le)) {
			return;
		}
		CombatPayloads.Profile prof = ClientCombat.PROFILES.get(le.getId());
		p.pushTop();
		float x = 740, y = 26, w = 440, h = 94;
		p.round(x, y, w, h, 18, Paint.ink(0.78F));
		p.bold(le.getName().getString(), x + 16, y + 14, Paint.TEXT, 9);
		boolean staggered = ClientCombat.active(le.getId(), StatusType.STAGGER);
		String sub;
		Rank rank = prof == null ? Rank.COMMON : Rank.values()[Math.max(0, Math.min(2, prof.rank()))];
		if (staggered) {
			CombatPayloads.Meters m = ClientCombat.METERS.get(le.getId());
			float left = m == null ? 0 : Math.max(0, m.staggerLeft() - (now - ClientCombat.METERS_AT.getOrDefault(le.getId(), Long.valueOf(now))) / 50F);
			sub = (rank == Rank.ELITE ? "Elite · " : "") + String.format(Locale.ROOT, "Staggered %.1f s · open to a riposte", left / 20F);
		} else if (prof != null && prof.subtitle().isPresent()) {
			sub = prof.subtitle().get().getString();
		} else {
			sub = rank == Rank.ELITE ? "Elite" : le instanceof Player ? "Player" : "";
		}
		if (!sub.isEmpty()) {
			p.text(sub, x + 16, y + 33, Paint.DIM, 7);
		}
		// Affix chips, right of the name.
		if (prof != null) {
			float ax = x + w - 16 - 24;
			for (int i = prof.affixes().size() - 1; i >= 0 && i >= prof.affixes().size() - 4; i--) {
				p.round(ax, y + 12, 24, 24, 6, 0xFF2A2A2E);
				Icons.shield(p, ax + 5, y + 17, 14, affixColour(prof.affixes().get(i).getPath()));
				ax -= 28;
			}
		}
		float max = Math.max(1, le.getMaxHealth());
		float hp = Math.max(0, le.getHealth());
		if (lastTargetId != le.getId()) {
			lastTargetHealth = hp;
			lastTargetId = le.getId();
		}
		if (hp < lastTargetHealth - 0.01F) {
			targetGhost = lastTargetHealth;
			targetGhostAt = now;
		}
		lastTargetHealth = hp;
		float bx = x + 16, bw = w - 32, by = y + 54;
		p.round(bx, by, bw, 8, 4, Paint.ink(0.9F));
		float gf = now - targetGhostAt < 600 ? 1 - (now - targetGhostAt) / 600F : 0;
		if (gf > 0) {
			p.round(bx, by, bw * Math.min(1, targetGhost / max), 8, 4, Paint.alpha(Paint.RECENT, gf));
		}
		p.round(bx, by, bw * hp / max, 8, 4, Paint.HEALTH);
		meterRow(p, le.getId(), bx, y + 70, 80, 52, 12, now, false);
		p.pop();
	}

	private static int affixColour(String path) {
		return switch (path.hashCode() & 3) {
			case 0 -> 0xFFE8C46A;
			case 1 -> 0xFFE5484D;
			case 2 -> 0xFF8BD45E;
			default -> 0xFFDCD6C4;
		};
	}

	// ------------------------------------------------------------------ H5 nameplates

	private static void nameplates(Paint p, Minecraft mc, FrayClientOptions opt, float partial) {
		int bossId = ClientCombat.bar == null ? -1 : ClientCombat.bar.boss();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof Player || e instanceof ArmorStand || !le.isAlive() || le.isInvisible()
				|| e.getId() == LockOn.target || e.getId() == bossId) {
				continue;
			}
			double d = e.distanceTo(mc.player);
			if (d > 24) {
				continue;
			}
			CombatPayloads.Profile prof = ClientCombat.PROFILES.get(e.getId());
			Rank rank = prof == null ? Rank.COMMON : Rank.values()[Math.max(0, Math.min(2, prof.rank()))];
			if (rank == Rank.BOSS) {
				continue;
			}
			boolean hurt = le.getHealth() < le.getMaxHealth() - 0.01F;
			boolean elite = rank == Rank.ELITE;
			if (!elite && (!hurt || opt.nameplates == FrayClientOptions.Nameplates.ELITES)) {
				continue;
			}
			double x = Mth.lerp(partial, e.xo, e.getX());
			double y = Mth.lerp(partial, e.yo, e.getY()) + e.getBbHeight() + 0.45;
			double z = Mth.lerp(partial, e.zo, e.getZ());
			float[] at = Project.point(x, y, z);
			if (at == null || !mc.player.hasLineOfSight(e)) {
				continue;
			}
			float scale = (float) Mth.clampedLerp(1.0, 0.7, (d - 8) / 16);
			p.g.pose().pushMatrix();
			p.g.pose().translate(at[0], at[1]);
			p.g.pose().scale(p.k * scale, p.k * scale);
			float frac = le.getHealth() / Math.max(1, le.getMaxHealth());
			if (elite) {
				String name = le.getName().getString();
				float nw = p.width(name, 7);
				int chips = prof == null ? 0 : Math.min(3, prof.affixes().size());
				float w = Math.max(150, nw + chips * 18 + 24);
				p.round(-w / 2, -40, w, 40, 10, Paint.ink(0.55F));
				float tx = -w / 2 + 10;
				for (int i = 0; i < chips; i++) {
					Icons.shield(p, tx, -34, 12, affixColour(prof.affixes().get(i).getPath()));
					tx += 16;
				}
				p.text(name, tx + 2, -33, Paint.TEXT, 7);
				p.bar(-w / 2 + 10, -16, w - 20, 6, frac, Paint.ink(0.8F), Paint.HEALTH, 0);
			} else {
				p.round(-36, -14, 72, 14, 7, Paint.ink(0.55F));
				p.bar(-28, -9, 56, 4, frac, Paint.ink(0.8F), Paint.HEALTH, 0);
			}
			p.g.pose().popMatrix();
		}
	}

	// ------------------------------------------------------------------ H6 boss bar

	private static void bossBar(Paint p, Minecraft mc, long now) {
		CombatPayloads.EncounterBar b = ClientCombat.bar;
		p.pushTop();
		float x = 580, w = 760;
		p.round(x - 24, 12, w + 48, 100, 22, Paint.ink(0.38F));
		float nx = x + p.text(b.name(), x, 26, Paint.TEXT, 12);
		b.subtitle().ifPresent(s -> p.text(s.getString(), nx + 12, 31, Paint.DIM, 7));
		p.right("PHASE " + (b.phase() + 1), x + w, 29, Paint.DIM, 7);
		float hp = b.health();
		if (lastBossHealth < 0) {
			lastBossHealth = hp;
		}
		if (hp < lastBossHealth - 0.0005F) {
			bossGhost = Math.max(bossGhost > hp && now - bossGhostAt < 600 ? bossGhost : 0, lastBossHealth);
			bossGhostAt = now;
		}
		lastBossHealth = hp;
		float by = 55;
		p.round(x, by, w, 12, 6, Paint.ink(0.78F));
		float gf = now - bossGhostAt < 600 ? 1 - (now - bossGhostAt) / 600F : 0;
		if (gf > 0) {
			p.round(x, by, w * bossGhost, 12, 6, Paint.alpha(Paint.RECENT, gf));
		}
		p.round(x, by, w * hp, 12, 6, Paint.BOSS);
		for (float t : b.notches()) {
			float nxp = x + w * t;
			p.rect(nxp - 1, by - 8, 2, 22, 0xF2FFFFFF);
			p.diamond(nxp, by - 9, 9, 0xF2FFFFFF);
		}
		boolean broken = b.brokenLeft() > 0;
		p.text("BREAK", x, 82, broken ? Paint.BREAK : Paint.DIM, 6);
		p.bar(x + 46, 84, 240, 5, b.breakMeter(), Paint.ink(0.78F), Paint.BREAK, 0);
		if (broken) {
			float left = Math.max(0, b.brokenLeft() - (now - ClientCombat.barAt) / 50F);
			p.text(String.format(Locale.ROOT, "Broken · %.0f s to punish", Math.ceil(left / 20F)), x + 296, 82, Paint.BREAK, 7);
		}
		// Status meters on the right.
		float mx = x + w;
		for (int i = StatusType.METERED.length - 1; i >= 0; i--) {
			StatusType s = StatusType.METERED[i];
			int v = b.meters().length > i ? b.meters()[i] & 0xFF : 0;
			if (v <= 1) {
				continue;
			}
			mx -= 48;
			p.bar(mx + 14, 84, 32, 4, v / 255F, Paint.ink(0.78F), s.colour, 0);
			Icons.status(p, s, mx, 79, 12, 1);
			mx -= 10;
		}
		p.pop();
	}

	// ------------------------------------------------------------------ H7 banners

	private static void banner(Paint p, long now) {
		CombatPayloads.Banner b = ClientCombat.banner;
		if (b == null) {
			return;
		}
		boolean felled = b.kind() == CombatPayloads.BANNER_FELLED;
		long hold = felled ? 3000 : 2000;
		long age = now - ClientCombat.bannerAt;
		float f = age < 200 ? age / 200F : age < hold ? 1 : Math.max(0, 1 - (age - hold) / 400F);
		if (f <= 0) {
			return;
		}
		p.pushTop();
		float cy = 300;
		int glow = felled ? 0xE8C46A : 0xB33A3A;
		for (int i = 0; i < 6; i++) {
			float w = 1100 - i * 140, h = 190 - i * 24;
			p.round(960 - w / 2, cy - h / 2, w, h, h / 2, (Math.round(0x14 * f) << 24) | glow);
		}
		String title = b.title().getString();
		float tw = p.width(title, 26);
		int tc = Paint.alpha(felled ? 0xFFF3D98C : Paint.TEXT, f);
		p.text(title, 960 - tw / 2, cy - 26, tc, 26);
		int lc = Paint.alpha(0xCCE8E2D0, f);
		p.rect(960 - tw / 2 - 150, cy - 7, 120, 2, lc);
		p.diamond(960 - tw / 2 - 26, cy - 6, 9, lc);
		p.rect(960 + tw / 2 + 30, cy - 7, 120, 2, lc);
		p.diamond(960 + tw / 2 + 26, cy - 6, 9, lc);
		String sub = b.subtitle().getString();
		if (!sub.isEmpty()) {
			p.centred(sub, 960, cy + 16, Paint.alpha(0xFFD8D2C2, f), 7);
		}
		p.pop();
	}

	// ------------------------------------------------------------------ H8 hit feedback

	private static void feedback(Paint p, Minecraft mc, FrayClientOptions opt, long now) {
		if (ClientCombat.FX.isEmpty() && (ClientCombat.self == null || ClientCombat.self.riposteLeft() <= 0)) {
			return;
		}
		p.push(p.cx, p.cy);
		float flash = opt.flashReduction ? 0.5F : 1;
		for (ClientCombat.Fx fx : ClientCombat.FX) {
			long age = now - fx.at();
			if (fx.mine() && (fx.outcome() == GuardOutcome.NONE || fx.outcome() == GuardOutcome.BLOCKED) && opt.hitMarkers && age < 250) {
				boolean kill = (fx.flags() & CombatPayloads.HIT_KILL) != 0;
				boolean crit = HitFlags.has(fx.flags(), HitFlags.CRIT);
				int col = Paint.alpha(kill ? 0xFFFF5A5A : crit ? Paint.GOLD : 0xFFFFFFFF, 1 - age / 250F);
				float r0 = kill ? 11 : 8, r1 = kill ? 20 : 14;
				for (int q = 0; q < 4; q++) {
					p.g.pose().pushMatrix();
					p.g.pose().rotate((float) (Math.PI / 4 + q * Math.PI / 2));
					p.rect(-1, r0, kill ? 3 : 2, r1 - r0, col);
					p.g.pose().popMatrix();
				}
			}
			if (fx.onMe() && fx.outcome() == GuardOutcome.BLOCKED && age < 600) {
				pill(p, "Blocked", 0, 44, 0xFFE8E2D0, 1 - age / 600F);
			}
			if (fx.onMe() && fx.outcome() == GuardOutcome.SLIP && age < 900) {
				float f = (1 - age / 900F) * flash;
				p.ring(0, 0, 44, 3, Paint.alpha(0xFFA8C8FF, f), 0.62F, 0.88F, 40, false);
				p.ring(0, 0, 44, 3, Paint.alpha(0xFFA8C8FF, f), 0.12F, 0.38F, 40, false);
				pill(p, "Slip  +25 %", 0, 58, 0xFFE8EEFF, 1 - age / 900F);
			}
			if (fx.onMe() && fx.outcome() == GuardOutcome.GUARD_BREAK && age < 1000) {
				float f = 1 - age / 1000F;
				Icons.shield(p, -8, -50, 16, Paint.alpha(Paint.WINDED, f));
				pill(p, "Guard broken", 0, 44, Paint.WINDED, f);
			}
			if (fx.onMe() && fx.outcome() == GuardOutcome.PARRIED && age < 300) {
				p.ring(0, 0, 30 + age / 30F, 2, Paint.alpha(0xFFFFFFFF, (1 - age / 300F) * flash), 0, 1, 48, false);
			}
		}
		// The parry ring holds while the riposte window is open, its outer arc draining.
		CombatPayloads.Self s = ClientCombat.self;
		if (s != null && s.riposteLeft() > 0) {
			float left = Math.max(0, s.riposteLeft() - (now - ClientCombat.selfAt) / 50F);
			if (left > 0) {
				p.ring(0, 0, 32, 2, Paint.alpha(0xFFFFFFFF, 0.9F * flash), 0, 1, 48, false);
				p.ring(0, 0, 40, 3, Paint.alpha(0xFFFFFFFF, 0.75F * flash), 0, Math.min(1, left / 20F), 56, false);
				pill(p, "Parried   riposte LMB", 0, 72, 0xFFF2F2EE, 1);
			}
		}
		p.pop();
	}

	private static void pill(Paint p, String s, float cx, float y, int col, float f) {
		float w = p.width(s, 7) + 20;
		p.round(cx - w / 2, y, w, 22, 11, Paint.ink(0.72F * f));
		p.centred(s, cx, y + 7, Paint.alpha(col, f), 7);
	}

	private static void numbers(Paint p, long now) {
		for (ClientCombat.Fx fx : ClientCombat.FX) {
			long age = now - fx.at();
			if (!fx.mine() || fx.amount() <= 0 || age > 800) {
				continue;
			}
			float[] at = Project.point(fx.x(), fx.y(), fx.z());
			if (at == null) {
				continue;
			}
			boolean crit = HitFlags.has(fx.flags(), HitFlags.CRIT);
			int col = crit ? Paint.GOLD : DamageKind.VALUES[Math.max(0, Math.min(DamageKind.VALUES.length - 1, fx.kind()))].colour;
			p.push(at[0], at[1] - age / 40F);
			String n = fx.amount() >= 10 ? Integer.toString(Math.round(fx.amount())) : String.format(Locale.ROOT, "%.1f", fx.amount());
			p.centred(n, 0, 0, Paint.alpha(col, 1 - Math.max(0, age - 500) / 300F), crit ? 14 : 10);
			p.pop();
		}
	}

	// ------------------------------------------------------------------ H9 telegraph cues

	private static boolean cued(ClientCombat.Tell t, FrayClientOptions opt) {
		TellColour c = TellColour.values()[Math.max(0, Math.min(3, t.colour()))];
		return opt.telegraphCues && (c == TellColour.RED || c == TellColour.PURPLE || opt.cueAllColours);
	}

	private static void cues(Paint p, Minecraft mc, FrayClientOptions opt, long now, boolean edge) {
		if (ClientCombat.TELLS.isEmpty()) {
			return;
		}
		boolean any = false;
		for (ClientCombat.Tell t : ClientCombat.TELLS) {
			Entity e = mc.level.getEntity(t.attacker());
			long dur = Math.max(1, t.windup() * 50L);
			long age = now - t.at();
			if (e == null || age > dur + 150 || !cued(t, opt)) {
				continue;
			}
			TellColour c = TellColour.values()[Math.max(0, Math.min(3, t.colour()))];
			float bearing = Project.bearing(e.getX(), e.getZ());
			if (edge) {
				float a = 0.4F * Math.max(0, 1 - age / (float) dur) * (opt.flashReduction ? 0.5F : 1);
				edgeFlash(p, bearing, c.colour, a);
				continue;
			}
			any = true;
			p.push(p.cx, p.cy);
			double rad = Math.toRadians(bearing);
			float x = (float) (Math.sin(rad) * 84), y = (float) (-Math.cos(rad) * 84);
			shape(p, c, x, y, 28, rad);
			p.pop();
		}
		if (any && !edge) {
			p.push(p.cx, p.cy);
			p.ring(0, 0, 84, 1, 0x40FFFFFF, 0, 1, 64, true);
			p.pop();
		}
	}

	/** The tell's shape (colour-blind safe): circle white, square orange, diamond red, triangle purple (points out). */
	private static void shape(Paint p, TellColour c, float x, float y, float size, double rad) {
		int col = c.colour;
		int ink = Paint.ink(0.85F);
		switch (c) {
			case WHITE -> {
				p.disc(x, y, size + 4, ink);
				p.disc(x, y, size, col);
			}
			case ORANGE -> {
				p.round(x - size / 2 - 2, y - size / 2 - 2, size + 4, size + 4, 6, ink);
				p.round(x - size / 2, y - size / 2, size, size, 5, col);
			}
			case RED -> {
				p.diamond(x, y, size + 6, ink);
				p.diamond(x, y, size, col);
			}
			case PURPLE -> {
				p.g.pose().pushMatrix();
				p.g.pose().translate(x, y);
				p.g.pose().rotate((float) rad);
				p.triangle(0, 0, size + 6, ink);
				p.triangle(0, 1, size, col);
				p.g.pose().popMatrix();
			}
		}
	}

	/** A soft flash along the screen edge on the attacker's side (strips of falling alpha; flat, no gradient texture). */
	private static void edgeFlash(Paint p, float bearing, int colour, float a) {
		if (a <= 0.01F) {
			return;
		}
		int strips = 10;
		float depth = Math.min(p.guiW, p.guiH) * 0.18F;
		float abs = Math.abs(bearing);
		for (int i = 0; i < strips; i++) {
			float f = a * (1 - i / (float) strips);
			int col = Paint.alpha(colour | 0xFF000000, f * 0.55F);
			float d0 = depth * i / strips, d1 = depth * (i + 1) / strips;
			if (abs <= 50) {
				p.g.fill(0, Math.round(d0), p.guiW, Math.round(d1), col);
			} else if (abs >= 130) {
				p.g.fill(0, Math.round(p.guiH - d1), p.guiW, Math.round(p.guiH - d0), col);
			} else if (bearing > 0) {
				p.g.fill(Math.round(p.guiW - d1), 0, Math.round(p.guiW - d0), p.guiH, col);
			} else {
				p.g.fill(Math.round(d0), 0, Math.round(d1), p.guiH, col);
			}
		}
	}

	private static void tellSubtitles(Paint p, Minecraft mc, long now) {
		FrayClientOptions opt = FrayClientOptions.get();
		float y = 884;
		p.push(p.guiW - 1920 * p.k, p.hotbarTop - 980 * p.k);
		for (ClientCombat.Tell t : ClientCombat.TELLS) {
			Entity e = mc.level.getEntity(t.attacker());
			if (e == null || t.subtitle().isEmpty() || now - t.at() > t.windup() * 50L + 600 || !cued(t, opt)) {
				continue;
			}
			TellColour c = TellColour.values()[Math.max(0, Math.min(3, t.colour()))];
			float bearing = Project.bearing(e.getX(), e.getZ());
			String arrow = Math.abs(bearing) <= 45 ? "^" : Math.abs(bearing) >= 135 ? "v" : bearing > 0 ? ">" : "<";
			float w = p.width(t.subtitle(), 7) + 54;
			p.round(1891 - w, y, w, 28, 14, Paint.ink(0.72F));
			shape(p, c, 1891 - w + 16, y + 14, 10, 0);
			p.text(t.subtitle(), 1891 - w + 30, y + 10, Paint.TEXT, 7);
			p.text(arrow, 1891 - 16, y + 10, Paint.DIM, 7);
			y -= 34;
		}
		p.pop();
	}
}
