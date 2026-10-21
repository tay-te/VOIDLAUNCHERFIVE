package dev.voidmc.fray.client.combat.feel;

import dev.voidmc.fray.api.GuardOutcome;
import dev.voidmc.fray.client.combat.FrayClientOptions;
import dev.voidmc.fray.combat.math.Feel;
import dev.voidmc.fray.combat.net.CombatPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** Turns a hit packet into hit-stop on the entities involved and the local camera's shake (COMBAT.md §5). */
public final class HitFeel {
	private HitFeel() {
	}

	public static void onHit(CombatPayloads.Hit h, GuardOutcome outcome, boolean mine, boolean onMe) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) {
			return;
		}
		int flags = h.flags();
		boolean broke = (flags & CombatPayloads.HIT_BREAK) != 0;
		boolean boss = (flags & CombatPayloads.HIT_BOSS) != 0;
		boolean killed = (flags & CombatPayloads.HIT_KILL) != 0;
		Entity attacker = h.attacker() >= 0 ? mc.level.getEntity(h.attacker()) : null;
		// The push: from the attacker to the victim.
		double px = 0, pz = 0;
		if (attacker != null) {
			px = h.x() - attacker.getX();
			pz = h.z() - attacker.getZ();
		}
		if (FrayClientOptions.get().hitStop) {
			Feel.Stop stop = Feel.hitStop(flags, outcome, broke, killed);
			HitStop.start(h.attacker(), stop.attacker(), 0, 0, false);
			HitStop.start(h.target(), stop.victim(), px, pz, true);
		}
		float w = Feel.weight(flags, outcome, broke, boss);
		if (w <= 0) {
			return;
		}
		Feel.Role role = onMe ? Feel.Role.INCOMING : mine ? Feel.Role.OUTGOING : Feel.Role.BYSTANDER;
		float dmg = onMe ? h.amount() / Math.max(1, me.getMaxHealth()) : 0;
		float dist = (float) Math.sqrt(me.distanceToSqr(h.x(), h.y(), h.z()));
		float deg = Feel.amplitude(role, w, dmg, dist, broke);
		if (deg <= 0) {
			return;
		}
		float yaw = mc.gameRenderer.mainCamera().yRot();
		float[] u;
		switch (role) {
			case INCOMING -> u = Feel.direction(px, pz, yaw);
			case BYSTANDER -> u = Feel.direction(me.getX() - h.x(), me.getZ() - h.z(), yaw);
			default -> u = new float[] {0, 1};
		}
		if (boss && Feel.slam(flags, true)) {
			// A slam also dips the view.
			u = new float[] {u[0] * 0.8F, Math.abs(u[1]) * 0.4F + 0.6F};
			float n = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1]);
			u[0] /= n;
			u[1] /= n;
		}
		CameraShake.add(deg, w, u[0], u[1]);
	}
}
