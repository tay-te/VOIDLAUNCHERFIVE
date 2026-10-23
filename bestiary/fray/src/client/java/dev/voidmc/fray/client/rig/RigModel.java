package dev.voidmc.fray.client.rig;

import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.client.FrayStats;
import dev.voidmc.fray.client.compat.IrisCompat;
import dev.voidmc.fray.motion.PartPose;
import dev.voidmc.fray.motion.PoseBuffer;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.RigSolver;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * A custom rig's model (ANIMATION.md §4.3): vanilla {@code ModelPart}s built from the rig file's geometry (the same
 * {@code CubeListBuilder.addBox} calls Blockbench's Java export writes), drawn with vanilla entity render types, so
 * Sodium draws its cubes and Iris shades and shadows it like any entity. {@link #setupAnim} poses every bone from the
 * render state's {@link PoseBuffer} through {@link RigSolver}: the same maths the bake placed the hit volumes with.
 */
public final class RigModel extends EntityModel<LivingEntityRenderState> {
	/**
	 * Bones to hide this frame, as a bit mask by bone index (a bone and everything under it): a renderer sets it in its
	 * extract step (the Gallows Knight's cage, torn off in its second phase).
	 *
	 * @since 0.3.0 (2026-10-22)
	 */
	public static final RenderStateDataKey<Long> HIDDEN = RenderStateDataKey.create(() -> "fray:hidden_bones");
	public final Rig rig;
	private final ModelPart[] bones;
	private final PartPose[] posed;
	private final RigSolver solver = new RigSolver();

	public RigModel(Rig rig) {
		super(build(rig).bakeRoot(), RenderTypes::entityCutout);
		this.rig = rig;
		this.bones = new ModelPart[rig.bones()];
		for (int b = 0; b < rig.bones(); b++) {
			ModelPart parent = rig.parent[b] < 0 ? this.root : this.bones[rig.parent[b]];
			this.bones[b] = parent.getChild(rig.name(b));
		}
		this.posed = RigSolver.poses(rig.bones());
	}

	/** The rig's geometry as vanilla's model definition. */
	public static LayerDefinition build(Rig rig) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition[] defs = new PartDefinition[rig.bones()];
		for (int b = 0; b < rig.bones(); b++) {
			CubeListBuilder cubes = CubeListBuilder.create();
			for (Rig.Cube c : rig.cubes[b]) {
				cubes.texOffs(c.u(), c.v()).mirror(c.mirror()).addBox(c.x(), c.y(), c.z(), c.dx(), c.dy(), c.dz(), new CubeDeformation(c.inflate()));
			}
			PartPose r = rig.rest[b];
			PartDefinition parent = rig.parent[b] < 0 ? mesh.getRoot() : defs[rig.parent[b]];
			defs[b] = parent.addOrReplaceChild(rig.name(b), cubes, net.minecraft.client.model.geom.PartPose.offsetAndRotation(r.x, r.y, r.z, r.xRot, r.yRot, r.zRot));
		}
		return LayerDefinition.create(mesh, rig.textureWidth, rig.textureHeight);
	}

	public ModelPart bone(int b) {
		return this.bones[b];
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		Long hidden = state.getData(HIDDEN);
		long mask = hidden == null ? 0 : hidden;
		for (int b = 0; b < this.bones.length && b < 64; b++) {
			this.bones[b].visible = (mask & 1L << b) == 0;
		}
		PoseBuffer buf = ClientMotion.enabled ? state.getData(ClientMotion.POSE) : null;
		if (buf == null || buf.rig != this.rig) {
			return;
		}
		long t0 = System.nanoTime();
		this.solver.solve(this.rig, buf, this.posed);
		for (int b = 0; b < this.bones.length; b++) {
			PartPose p = this.posed[b];
			ModelPart m = this.bones[b];
			m.x = p.x;
			m.y = p.y;
			m.z = p.z;
			m.xRot = p.xRot;
			m.yRot = p.yRot;
			m.zRot = p.zRot;
			m.xScale = p.xScale;
			m.yScale = p.yScale;
			m.zScale = p.zScale;
		}
		FrayStats.applyNanos += System.nanoTime() - t0;
		FrayStats.applies++;
		if (IrisCompat.inShadowPass()) {
			FrayStats.shadowApplies++;
		}
	}
}
