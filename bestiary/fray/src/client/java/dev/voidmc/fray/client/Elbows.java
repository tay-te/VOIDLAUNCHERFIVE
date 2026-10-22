package dev.voidmc.fray.client;

import dev.voidmc.fray.client.mixin.ModelAccess;
import dev.voidmc.fray.client.mixin.ModelPartAccess;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

/**
 * Elbows on every humanoid model's arms (the player, its armour, mannequins; zombies and kin too, which simply never
 * bend them): each arm part is cut at its box's middle into the upper half (the vanilla part, which keeps its name,
 * pose and children) and a {@value #FOREARM} child hinged at the elbow. Every child that sits on the arm unposed
 * (the player's sleeve) is cut the same way and gets its own {@value #FOREARM}, so the sleeve still follows its own
 * visibility.
 *
 * <p>The cut is exact: every face is split along the elbow plane with its texture coordinates interpolated, so the two
 * halves together are vanilla's vertices and UVs (any skin, wide or slim, any armour texture, trims and glint).
 * And while an elbow is straight the arm keeps vanilla's own boxes (the cut halves are swapped in only while it bends,
 * {@link #bend}), so an unbent model is vanilla's geometry and costs what vanilla's does. Around the joint the upper half has a thin skirt on its back and sides (1.5 px of the
 * band below the cut, set 0.15 px inside the surface): hidden while straight, it fills the gap on the outside of a bend.
 */
public final class Elbows {
	public static final String FOREARM = "fray_forearm";
	private static final float SKIRT = 1.5F, INSET = 0.15F;
	/** Arm part → its forearms (the arm's own and its sleeve's), for posing. */
	private static final Map<ModelPart, List<ModelPart>> FOREARMS = new IdentityHashMap<>();
	/** One cut part: its vanilla boxes, its upper half, its forearm. */
	private record Cut(ModelPart part, List<ModelPart.Cube> whole, List<ModelPart.Cube> upper, ModelPart forearm) {
	}

	private static final Map<ModelPart, List<Cut>> CUTS = new IdentityHashMap<>();
	/** Models split (counted for tests). */
	public static int splitModels;

	private Elbows() {
	}

	public static synchronized void split(HumanoidModel<?> model) {
		if (Boolean.getBoolean("fray.noElbows")) {
			// Measurements only: vanilla's single-box arms.
			return;
		}
		boolean any = false;
		for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
			if (FOREARMS.containsKey(arm)) {
				continue;
			}
			List<ModelPart.Cube> cubes = ((ModelPartAccess) (Object) arm).fray$cubes();
			if (cubes.isEmpty() || arm.hasChild(FOREARM)) {
				continue;
			}
			ModelPart.Cube c = cubes.getFirst();
			float mid = (c.minY + c.maxY) / 2, px = (c.minX + c.maxX) / 2, pz = (c.minZ + c.maxZ) / 2;
			List<ModelPart> forearms = new ArrayList<>();
			List<Cut> cuts = new ArrayList<>();
			List<ModelPart> sleeves = new ArrayList<>(((ModelPartAccess) (Object) arm).fray$children().values());
			Cut f = splitPart(arm, mid, px, pz);
			if (f != null) {
				cuts.add(f);
				forearms.add(f.forearm());
			}
			for (ModelPart child : sleeves) {
				PartPose p = child.getInitialPose();
				if (p.x() == 0 && p.y() == 0 && p.z() == 0 && p.xRot() == 0 && p.yRot() == 0 && p.zRot() == 0) {
					Cut cf = splitPart(child, mid, px, pz);
					if (cf != null) {
						cuts.add(cf);
						forearms.add(cf.forearm());
					}
				}
			}
			FOREARMS.put(arm, List.copyOf(forearms));
			CUTS.put(arm, List.copyOf(cuts));
			any |= !forearms.isEmpty();
		}
		if (any) {
			((ModelAccess) (Object) model).fray$setAllParts(model.root().getAllParts());
			splitModels++;
		}
	}

	/** The arm's forearms (its own first), or an empty list for an arm without an elbow. */
	public static List<ModelPart> forearms(ModelPart arm) {
		List<ModelPart> l = FOREARMS.get(arm);
		return l == null ? List.of() : l;
	}

	/**
	 * The arm's upper halves (the cut above the elbow), its own first, then its sleeve's: each list of cubes, in its
	 * part's frame. Empty for an arm without an elbow. First person stacks them to lengthen a raised arm's sleeve.
	 */
	public static List<List<ModelPart.Cube>> uppers(ModelPart arm) {
		List<Cut> cuts = CUTS.get(arm);
		if (cuts == null) {
			return List.of();
		}
		List<List<ModelPart.Cube>> out = new ArrayList<>();
		for (Cut c : cuts) {
			out.add(c.upper());
		}
		return out;
	}

	/** Straightens the arm's elbows (vanilla's first-person hand resets only the arm). */
	public static void straighten(ModelPart arm) {
		bend(arm, false);
		for (ModelPart f : forearms(arm)) {
			f.resetPose();
		}
	}

	/** Bent: the cut halves and the forearms; straight: vanilla's own boxes, the forearms hidden. */
	public static void bend(ModelPart arm, boolean bent) {
		List<Cut> cuts = CUTS.get(arm);
		if (cuts == null) {
			return;
		}
		for (Cut c : cuts) {
			if (c.forearm().visible != bent) {
				((ModelPartAccess) (Object) c.part()).fray$setCubes(bent ? c.upper() : c.whole());
				c.forearm().visible = bent;
			}
		}
	}

	private static Cut splitPart(ModelPart part, float mid, float px, float pz) {
		ModelPartAccess acc = (ModelPartAccess) (Object) part;
		List<ModelPart.Cube> cubes = acc.fray$cubes();
		if (cubes.isEmpty()) {
			return null;
		}
		List<ModelPart.Polygon> upper = new ArrayList<>(), lower = new ArrayList<>();
		for (ModelPart.Cube c : cubes) {
			for (ModelPart.Polygon poly : c.polygons) {
				cut(poly, mid, px, pz, upper, lower);
			}
		}
		Map<String, ModelPart> children = acc.fray$children();
		ModelPart forearm = new ModelPart(cubes(lower), new HashMap<>());
		PartPose pivot = PartPose.offset(px, mid, pz);
		forearm.setInitialPose(pivot);
		forearm.loadPose(pivot);
		try {
			children.put(FOREARM, forearm);
		} catch (UnsupportedOperationException e) {
			return null;
		}
		forearm.visible = false;
		return new Cut(part, cubes, cubes(upper), forearm);
	}

	/** One face cut at y = mid: the part above to {@code upper}, below (moved into the forearm's frame) to {@code lower}, plus skirts. */
	private static void cut(ModelPart.Polygon poly, float mid, float px, float pz, List<ModelPart.Polygon> upper, List<ModelPart.Polygon> lower) {
		ModelPart.Vertex[] v = poly.vertices();
		float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
		for (ModelPart.Vertex x : v) {
			min = Math.min(min, x.y());
			max = Math.max(max, x.y());
		}
		if (max <= mid + 1e-4F) {
			upper.add(poly);
			return;
		}
		if (min >= mid - 1e-4F) {
			lower.add(moved(poly, px, mid, pz));
			return;
		}
		// A side face spanning the elbow (a vertical quad: two vertices at min, two at max).
		upper.add(slice(poly, min, max, min, mid, 0, px, mid, pz, false));
		lower.add(slice(poly, min, max, mid, max, 0, px, mid, pz, true));
		Vector3fc n = poly.normal();
		if (Math.abs(n.y()) < 0.5F && n.z() > -0.5F) {
			// A skirt on the upper half's back and sides (a forward bend opens the joint there; the front closes): the
			// band below the cut, set just inside the surface. Faces only: a bent arm draws 3 more quads per layer.
			upper.add(slice(poly, min, max, mid, Math.min(max, mid + SKIRT), INSET, px, mid, pz, false));
		}
	}

	/**
	 * The band [y0, y1] of a vertical quad spanning [min, max], UVs interpolated along its vertical edges, pushed
	 * {@code inset} px against its normal; into the forearm's frame when {@code toForearm}.
	 */
	private static ModelPart.Polygon slice(ModelPart.Polygon poly, float min, float max, float y0, float y1, float inset, float px, float mid, float pz,
		boolean toForearm) {
		ModelPart.Vertex[] v = poly.vertices();
		Vector3fc n = poly.normal();
		ModelPart.Vertex[] out = new ModelPart.Vertex[v.length];
		for (int i = 0; i < v.length; i++) {
			ModelPart.Vertex a = v[i];
			// Its partner on the same vertical edge: same x and z, the other end.
			ModelPart.Vertex b = a;
			for (ModelPart.Vertex c : v) {
				if (c != a && Math.abs(c.x() - a.x()) < 1e-4F && Math.abs(c.z() - a.z()) < 1e-4F) {
					b = c;
				}
			}
			ModelPart.Vertex top = a.y() <= b.y() ? a : b, bottom = a.y() <= b.y() ? b : a;
			float y = a.y() <= b.y() ? y0 : y1;
			float k = bottom.y() == top.y() ? 0 : (y - top.y()) / (bottom.y() - top.y());
			float u = top.u() + (bottom.u() - top.u()) * k, vv = top.v() + (bottom.v() - top.v()) * k;
			float x = a.x() - n.x() * inset, z = a.z() - n.z() * inset;
			out[i] = toForearm ? new ModelPart.Vertex(x - px, y - mid, z - pz, u, vv) : new ModelPart.Vertex(x, y, z, u, vv);
		}
		return new ModelPart.Polygon(out, n);
	}

	private static ModelPart.Polygon moved(ModelPart.Polygon poly, float px, float py, float pz) {
		ModelPart.Vertex[] v = poly.vertices();
		ModelPart.Vertex[] out = new ModelPart.Vertex[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = new ModelPart.Vertex(v[i].x() - px, v[i].y() - py, v[i].z() - pz, v[i].u(), v[i].v());
		}
		return new ModelPart.Polygon(out, poly.normal());
	}

	/** Polygons into cubes (a vanilla cube holds up to six; the geometry fields are informative only). */
	private static List<ModelPart.Cube> cubes(List<ModelPart.Polygon> polys) {
		List<ModelPart.Cube> out = new ArrayList<>();
		Direction[] dirs = Direction.values();
		for (int i = 0; i < polys.size(); i += 6) {
			int n = Math.min(6, polys.size() - i);
			Set<Direction> faces = EnumSet.noneOf(Direction.class);
			for (int k = 0; k < n; k++) {
				faces.add(dirs[k]);
			}
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
			for (int k = 0; k < n; k++) {
				for (ModelPart.Vertex x : polys.get(i + k).vertices()) {
					minX = Math.min(minX, x.x());
					minY = Math.min(minY, x.y());
					minZ = Math.min(minZ, x.z());
					maxX = Math.max(maxX, x.x());
					maxY = Math.max(maxY, x.y());
					maxZ = Math.max(maxZ, x.z());
				}
			}
			ModelPart.Cube c = new ModelPart.Cube(0, 0, minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ, 0, 0, 0, false, 64, 64, faces);
			for (int k = 0; k < n; k++) {
				c.polygons[k] = polys.get(i + k);
			}
			out.add(c);
		}
		return out;
	}
}
