package dev.voidmc.hearth.client.npc;

import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.look.FolkLook;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What a folk looks like this frame: their composed skin, build, pose and quest marker. */
public class FolkRenderState extends HumanoidRenderState {
	public @Nullable FolkLook look;
	public @Nullable Identifier skin;
	public boolean slim;
	public boolean elder;
	public FolkPose folkPose = FolkPose.NONE;
	public QuestService.Marker marker = QuestService.Marker.NONE;
	public @Nullable Vec3 markerAttachment;
	/** Per-entity phase so a crowd's idle motions do not move in step. */
	public float phase;
}
