package dev.voidmc.fray.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.fray.client.combat.FrayClientOptions;
import dev.voidmc.fray.motion.Clip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.SwingAnimation;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * First person while {@link FrayClientOptions#fpAnimations} is off (the default since 2026-10-22): vanilla's own held
 * item and attack swing, driven by the local player's Fray action. A strike plays vanilla's swing timed so the swing's
 * contact lands on the clip's first ACTIVE tick (its baked timeline, the hit tick); heavy and charge release play
 * it more slowly.
 * Guard clips (parry, block) raise the sword a little, part of the way to vanilla's old sword-block pose. Everything is
 * read from the Animator's action time, so hit-stop's hold freezes the vanilla hand too. Pictures only: combat timing
 * comes from the clips' timelines either way.
 */
public final class VanillaFirstPerson {
	/** Vanilla's swing length in ticks, and the slower one for heavy strikes. */
	static final float LIGHT_TICKS = 6, SLOW_TICKS = 10;
	/** Where in vanilla's swing (0..1) the blade meets the target: the clip's windup_end lands here. */
	static final float CONTACT = 0.4F;
	/** How far toward vanilla's sword-block pose a guard raises the sword. */
	static final float GUARD_RAISE = 0.35F;
	private static final SwingAnimation SLOW = new SwingAnimation(SwingAnimation.DEFAULT.type(), (int) SLOW_TICKS);
	private static final Quaternionf BLOCK_R = new Quaternionf(), BLOCK_L = new Quaternionf(), Q = new Quaternionf();

	static {
		BLOCK_R.rotationX((float) Math.toRadians(-102.25)).rotateY((float) Math.toRadians(13.365)).rotateZ((float) Math.toRadians(78.05));
		BLOCK_L.rotationX((float) Math.toRadians(-102.25)).rotateY((float) Math.toRadians(-13.365)).rotateZ((float) Math.toRadians(-78.05));
	}

	/** Vanilla swings and guard raises drawn (tests). */
	public static long swings, guards;

	// The render state's own values, put back after the hand pass.
	private static boolean saved;
	private static float savedSwing;
	private static LivingEntity.@Nullable SwingDescription savedCurrent;
	private static @Nullable InteractionHand savedHand;
	private static float guard;

	private VanillaFirstPerson() {
	}

	/** Fray's custom first person (FirstPersonRig and the clips' {@code fp} curves): off by default. */
	public static boolean customOn() {
		return FrayClientOptions.get().fpAnimations || Boolean.getBoolean("fray.fpAnimations");
	}

	/** Vanilla's swing value (0..1) for a strike clip at {@code elapsed} ticks, or -1 when the clip shows no swing now. */
	public static float swing(Clip clip, double elapsed) {
		float contact = contactTick(clip);
		if (contact < 0 || Double.isNaN(elapsed)) {
			return -1;
		}
		float len = slow(clip) ? SLOW_TICKS : LIGHT_TICKS;
		double a = (elapsed - (contact - CONTACT * len)) / len;
		return a <= 0 || a >= 1 ? -1 : (float) a;
	}

	/**
	 * The clip's first ACTIVE tick (its baked timeline: the same data the server's hits come from), else its
	 * {@code windup_end} marker; -1 for a clip with no strike.
	 */
	static float contactTick(Clip clip) {
		dev.voidmc.fray.motion.Timeline t = dev.voidmc.fray.motion.MotionServer.timeline(clip.id);
		if (t != null && t.hasPhases() && t.activeStart >= 0) {
			return t.activeStart;
		}
		float m = clip.marker("windup_end");
		return m < 0 ? -1 : m * 20;
	}

	static boolean slow(Clip clip) {
		String p = clip.id.getPath();
		return p.contains("heavy") || p.endsWith("charge_release");
	}

	static boolean guardClip(Clip clip) {
		String p = clip.id.getPath();
		return p.equals("player/parry") || p.equals("player/block_raise") || p.equals("player/block_hold");
	}

	/** Before vanilla's hand pass: the local player's action as vanilla's swing (and the guard's raise). */
	public static void begin(float partialTicks, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
		guard = 0;
		saved = false;
		if (!ClientMotion.enabled || customOn()) {
			return;
		}
		AvatarRenderState avatar = playerState.avatarRenderState;
		Minecraft mc = Minecraft.getInstance();
		if (avatar == null || mc.player == null) {
			return;
		}
		Animator animator = ClientMotion.get(mc.player.getId());
		Clip clip = animator == null ? null : animator.clip(Animator.ACTION);
		if (clip == null || !clip.id.getPath().startsWith("player/")) {
			return;
		}
		double now = ClientMotion.now(partialTicks);
		if (guardClip(clip)) {
			guard = GUARD_RAISE * animator.actionWeight(now);
			if (guard > 0.002F) {
				guards++;
			}
			return;
		}
		float a = swing(clip, animator.actionElapsed(now));
		if (a < 0) {
			return;
		}
		saved = true;
		savedSwing = avatar.swingAnimation;
		savedCurrent = avatar.currentSwing;
		savedHand = state.attackHand;
		avatar.swingAnimation = a;
		avatar.currentSwing = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND, slow(clip) ? SLOW : SwingAnimation.DEFAULT,
			(int) (slow(clip) ? SLOW_TICKS : LIGHT_TICKS));
		state.attackHand = InteractionHand.MAIN_HAND;
		swings++;
	}

	/** After vanilla's hand pass: the render state as it was. */
	public static void end(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state) {
		guard = 0;
		if (!saved) {
			return;
		}
		saved = false;
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (avatar != null) {
			avatar.swingAnimation = savedSwing;
			avatar.currentSwing = savedCurrent;
		}
		state.attackHand = savedHand;
	}

	/** Just before vanilla submits a hand's item: the guard's raise on the main hand's weapon. */
	public static void raise(PoseStack poseStack, PlayerRenderState playerState, InteractionHand hand, net.minecraft.world.item.ItemStack item) {
		if (guard <= 0.002F || hand != InteractionHand.MAIN_HAND || item.isEmpty() || item.getItem() instanceof ShieldItem
			|| playerState.avatarRenderState == null) {
			return;
		}
		boolean right = playerState.avatarRenderState.mainArm == HumanoidArm.RIGHT;
		int invert = right ? 1 : -1;
		float k = guard;
		poseStack.translate(invert * -0.14142136F * k, 0.08F * k, 0.14142136F * k);
		poseStack.rotate(Q.identity().slerp(right ? BLOCK_R : BLOCK_L, k));
	}
}
