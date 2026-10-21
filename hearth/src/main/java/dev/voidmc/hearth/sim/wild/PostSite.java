package dev.voidmc.hearth.sim.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.sim.atlas.Atlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a post stands, as the site overlay decided it from the seed (docs/LIVING_WORLD_2.md §7.2): its type, centre,
 * the way its front faces, its age, and what its layout needs of the land round it.
 *
 * @param key    the site's key: its region and its index there ({@link #key(int, int, int)})
 * @param facing where the post's front faces: toward the road it serves, down the pass, toward the water
 * @param age    sim days the post had stood when the world began
 * @param road   the centreline of the road it stands by ({@code x, y, z} triples, every other column, about 80 blocks of
 *               it round the post), or empty for a post sited by the land alone
 * @param gx     for a nomad camp, the offset of its second ground from the first along x (the grounds stand at the centre
 *               minus and plus half of it); 0 for every other type
 * @param gz     likewise along z
 * @param score  how well the land suited it (for logs and the tour)
 */
public record PostSite(long key, PostType type, int x, int y, int z, Direction facing, int age, long seed, int[] road, int gx, int gz,
	double score) {

	public static final Codec<PostSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("key").forGetter(PostSite::key),
		PostType.CODEC.fieldOf("type").forGetter(PostSite::type),
		Codec.INT.fieldOf("x").forGetter(PostSite::x),
		Codec.INT.fieldOf("y").forGetter(PostSite::y),
		Codec.INT.fieldOf("z").forGetter(PostSite::z),
		Direction.CODEC.fieldOf("facing").forGetter(PostSite::facing),
		Codec.INT.fieldOf("age").forGetter(PostSite::age),
		Codec.LONG.fieldOf("seed").forGetter(PostSite::seed),
		Codec.INT_STREAM.xmap(java.util.stream.IntStream::toArray, java.util.Arrays::stream).optionalFieldOf("road", new int[0]).forGetter(PostSite::road),
		Codec.INT.optionalFieldOf("gx", 0).forGetter(PostSite::gx),
		Codec.INT.optionalFieldOf("gz", 0).forGetter(PostSite::gz),
		Codec.DOUBLE.optionalFieldOf("score", 0.0).forGetter(PostSite::score)
	).apply(i, PostSite::new));

	/** A site's key: region x and z (21 bits each) and its index in the region (2 bits). */
	public static long key(int rx, int rz, int index) {
		return (long) (rx + (1 << 20) & 0x1F_FFFF) << 23 | (long) (rz + (1 << 20) & 0x1F_FFFF) << 2 | index & 3;
	}

	public static int regionX(long key) {
		return (int) (key >>> 23 & 0x1F_FFFF) - (1 << 20);
	}

	public static int regionZ(long key) {
		return (int) (key >>> 2 & 0x1F_FFFF) - (1 << 20);
	}

	public BlockPos center() {
		return new BlockPos(this.x, this.y, this.z);
	}

	/** Whether the site lies in the region (its key says so too). */
	public boolean inRegion(int rx, int rz) {
		return Atlas.region(this.x) == rx && Atlas.region(this.z) == rz;
	}

	/** The square the post claims (both grounds of a nomad camp). */
	public int minX() {
		return this.x - this.type.radius - Math.abs(this.gx) / 2;
	}

	public int maxX() {
		return this.x + this.type.radius + Math.abs(this.gx) / 2;
	}

	public int minZ() {
		return this.z - this.type.radius - Math.abs(this.gz) / 2;
	}

	public int maxZ() {
		return this.z + this.type.radius + Math.abs(this.gz) / 2;
	}

	public boolean claims(int x, int z) {
		return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
	}

	/** The land a post's history may build on: its square and a little more for eaves and earthwork. */
	public BoundingBox land() {
		return new BoundingBox(minX() - 4, -2048, minZ() - 4, maxX() + 4, 4096, maxZ() + 4);
	}

	/** A nomad camp's ground {@code which} (0 or 1): the centre less or plus half the offset. */
	public int groundX(int which) {
		return this.x + (which == 0 ? -this.gx / 2 : this.gx - this.gx / 2);
	}

	public int groundZ(int which) {
		return this.z + (which == 0 ? -this.gz / 2 : this.gz - this.gz / 2);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PostSite p && p.key == this.key && p.type == this.type && p.x == this.x && p.y == this.y && p.z == this.z
			&& p.facing == this.facing && p.age == this.age && p.seed == this.seed && java.util.Arrays.equals(p.road, this.road) && p.gx == this.gx
			&& p.gz == this.gz;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.key) * 31 + this.type.ordinal() * 7 + this.x * 13 + this.z;
	}

	@Override
	public String toString() {
		return this.type.getSerializedName() + "@" + this.x + "," + this.y + "," + this.z + " facing " + this.facing.getSerializedName()
			+ " age " + this.age + String.format(java.util.Locale.ROOT, " score %.1f", this.score);
	}
}
