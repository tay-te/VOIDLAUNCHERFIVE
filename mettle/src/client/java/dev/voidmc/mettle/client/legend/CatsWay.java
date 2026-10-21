package dev.voidmc.mettle.client.legend;

import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.cards.legend.LegendPayloads;
import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Cat's Way (a drawn Mythic, docs/BUILD_FEEL.md §5.4): sprint-jump along a wall and, from the top of the jump, run on
 * along it for up to 2 s without falling, every 8 s. The run is the client's own movement: it only stops the fall (no
 * upward push, no added speed: the air keeps its own pace). The server is told so it keeps the saved cooldown and
 * forgives the fall while it lasts.
 */
final class CatsWay {
	private static boolean running;
	private static int ticks;
	private static long readyAt;
	private static @Nullable Vec3 side;

	private CatsWay() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(CatsWay::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			running = false;
			return;
		}
		Vec3 v = p.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0, v.z);
		if (running) {
			ticks++;
			if (ticks > LegendWild.WALL_RUN_TICKS || !free(p) || p.isShiftKeyDown() || flat.lengthSqr() < 0.0025 || side == null || !wall(p, side)) {
				running = false;
				return;
			}
			p.setDeltaMovement(v.x, 0, v.z);
			p.resetFallDistance();
			return;
		}
		// From the top of a sprint-jump only: the run holds the height reached, it never lifts.
		if (!free(p) || v.y > 0 || flat.lengthSqr() < 0.01) {
			return;
		}
		long now = p.level().getGameTime();
		if (now < readyAt || now < CardCooldowns.readyAt(p, LegendWild.CATS_WAY) || !Mythics.active(p, "cats_way")) {
			return;
		}
		Vec3 dir = flat.normalize();
		Vec3 left = new Vec3(dir.z, 0, -dir.x);
		Vec3 found = wall(p, left) ? left : wall(p, left.reverse()) ? left.reverse() : null;
		if (found == null) {
			return;
		}
		running = true;
		ticks = 0;
		side = found;
		readyAt = now + LegendWild.CATS_WAY_TICKS;
		p.setDeltaMovement(v.x, 0, v.z);
		p.resetFallDistance();
		ClientPlayNetworking.send(new LegendPayloads.WallRun());
	}

	/** In the air on their own feet, sprinting: not swimming, climbing, gliding, flying or riding. */
	private static boolean free(LocalPlayer p) {
		return !p.onGround() && p.isSprinting() && !p.isInWater() && !p.isInLava() && !p.onClimbable() && !p.isFallFlying() && !p.getAbilities().flying
			&& !p.isPassenger();
	}

	/** A solid face right beside the player on this side, at the feet or the chest. */
	private static boolean wall(LocalPlayer p, Vec3 side) {
		Vec3 probe = p.position().add(side.scale(p.getBbWidth() / 2 + 0.3));
		for (double up : new double[] {0.2, 1.2}) {
			BlockPos pos = BlockPos.containing(probe.x, probe.y + up, probe.z);
			if (!p.level().getBlockState(pos).getCollisionShape(p.level(), pos, CollisionContext.of(p)).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
