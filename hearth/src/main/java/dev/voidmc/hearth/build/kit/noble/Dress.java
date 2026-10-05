package dev.voidmc.hearth.build.kit.noble;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;

/**
 * The materials of a lord's seat, read once from the building's dress: the walling (the style's render or masonry; a
 * timber culture's plinth stone), the dressed stone of quoins, string courses and window surrounds, the footing stone,
 * the roof and its trim, the timbers, boards and floors. Every part of a castle takes its stone from here, so its towers,
 * walls, hall and palace are one building.
 */
public final class Dress {
	/** The walling: render over stone, coursed stone, adobe, brick. */
	public final Stone wall;
	/** Quoins, string courses, arches, window surrounds, merlon caps. */
	public final Stone dress;
	/** Footings, the talus at a wall's foot, retaining walls. */
	public final Stone footing;
	/** The base course above the ground. */
	public final Stone plinth;
	/** Stone for steps, wall walks and the courts' paving. */
	public final Stone paving;
	public final Mat roof;
	public final Mat trim;
	/** Posts, braces, hoarding timbers. */
	public final Mat frame;
	public final Mat beam;
	/** Boards for hoardings, galleries, shutters of a timber head. */
	public final Mat boards;
	public final Mat floor;
	public final Mat glass;
	public final Mat furniture;
	public final Mat cloth;
	/** Whether the roof covering is timber (shingle) rather than tile, slate or stone. */
	public final boolean shingled;

	private Dress(Draft d) {
		this.wall = Stone.masonry(d);
		this.dress = Stone.dressing(d);
		this.footing = Stone.footing(d);
		this.plinth = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : this.footing;
		this.paving = Stone.stony(d, Role.PAVING) ? Stone.of(d, Role.PAVING) : this.plinth;
		this.roof = d.mat(Role.ROOF);
		this.trim = d.mat(Role.ROOF_TRIM);
		this.frame = d.mat(Role.FRAME);
		this.beam = d.mat(Role.BEAM);

		// boards of the timber's own wood (dark hoardings and galleries), else the gable's boards, else the floor's
		Mat gable = d.mat(Role.GABLE);
		this.boards = this.frame.has(Form.LOG) && this.frame.has(Form.FENCE) ? this.frame
			: gable.has(Form.LOG) || gable.has(Form.FENCE) ? gable : d.mat(Role.FLOOR);
		this.floor = d.mat(Role.FLOOR);
		this.glass = d.mat(Role.GLASS);
		this.furniture = d.mat(Role.FURNITURE);
		this.cloth = d.mat(Role.CLOTH);
		net.minecraft.world.level.block.Block r = this.roof.main(Form.FULL);
		this.shingled = r != null && dev.voidmc.hearth.build.blueprint.MaterialBill.timber(r) && !d.habits().roof().thatch();
	}

	public static Dress of(Draft d) {
		return new Dress(d);
	}
}
