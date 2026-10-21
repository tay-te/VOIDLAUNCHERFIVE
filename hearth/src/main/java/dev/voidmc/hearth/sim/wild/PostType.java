package dev.voidmc.hearth.sim.wild;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The kinds of small places in the wild (docs/LIVING_WORLD_2.md §7.1). Each is a settlement of kind
 * {@link dev.voidmc.hearth.api.SettlementKind#POST} whose {@link dev.voidmc.hearth.api.Settlement#postType()} is the id
 * here; its planner lays out the type's fixed programme ({@link dev.voidmc.hearth.plan.wild.PostPlanner}) and nothing more.
 *
 * @param radius     half the side of the square the post claims round its centre (Expanse's sights, trees and towns keep
 *                   off it); its programme stays inside it
 * @param minFolk    founders at the least
 * @param maxFolk    founders at the most (the programme's beds hold them)
 * @param road       sited on a road of the network (inns, toll gates, caravan stops) rather than by the land
 */
public enum PostType implements StringRepresentable {
	HOSPICE("hospice", 36, 3, 4, false),
	INN("inn", 34, 3, 5, true),
	TOLL_GATE("toll_gate", 22, 2, 3, true),
	HERMITAGE("hermitage", 14, 1, 1, false),
	HUNTERS_CAMP("hunters_camp", 22, 2, 3, false),
	CARAVAN_STOP("caravan_stop", 36, 2, 3, true),
	MONASTERY("monastery", 40, 6, 9, false),
	NOMAD_CAMP("nomad_camp", 64, 6, 9, false),
	HARBOUR("harbour", 34, 3, 5, false),
	FARMSTEAD("farmstead", 40, 3, 5, false);

	public static final StringRepresentable.EnumCodec<PostType> CODEC = StringRepresentable.fromEnum(PostType::values);

	private final String id;
	public final int radius;
	public final int minFolk;
	public final int maxFolk;
	public final boolean road;

	PostType(String id, int radius, int minFolk, int maxFolk, boolean road) {
		this.id = id;
		this.radius = radius;
		this.minFolk = minFolk;
		this.maxFolk = maxFolk;
		this.road = road;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	/** The Atlas place kind ({@code hearth:post/<type>}, §7.6) and the map payload's tier field. */
	public String placeTier() {
		return "post/" + this.id;
	}

	/** The lang key of the type's name ("Pass hospice"). */
	public String translationKey() {
		return "hearth.wild.type." + this.id;
	}

	public static @Nullable PostType byId(String id) {
		String s = id.toLowerCase(Locale.ROOT);
		for (PostType t : values()) {
			if (t.id.equals(s) || t.name().toLowerCase(Locale.ROOT).equals(s)) {
				return t;
			}
		}
		return null;
	}
}
