package dev.voidmc.expanse.client.life;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the small creatures' models animate from. */
public class CritterRenderState extends LivingEntityRenderState {
	public int variant;
	public boolean flying;
	public boolean resting;
	public boolean sleeping;
	public boolean onGround;
	/** The entity's id: staggers each one's idle motions so a flock doesn't move in lockstep. */
	public int seed;
}
