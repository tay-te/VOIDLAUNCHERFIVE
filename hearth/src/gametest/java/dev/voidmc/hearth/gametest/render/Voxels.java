package dev.voidmc.hearth.gametest.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A sparse block world for the offline renderer. */
public final class Voxels {
	private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
	private int minX = Integer.MAX_VALUE;
	private int minY = Integer.MAX_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxY = Integer.MIN_VALUE;
	private int maxZ = Integer.MIN_VALUE;

	public static long key(int x, int y, int z) {
		return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 20)) << 21) | (z + (1 << 20));
	}

	public static int kx(long k) {
		return (int) (k >>> 42) - (1 << 20);
	}

	public static int ky(long k) {
		return (int) ((k >>> 21) & 0x1FFFFF) - (1 << 20);
	}

	public static int kz(long k) {
		return (int) (k & 0x1FFFFF) - (1 << 20);
	}

	public void set(int x, int y, int z, BlockState s) {
		if (s.isAir()) {
			this.blocks.remove(key(x, y, z));
			return;
		}
		this.blocks.put(key(x, y, z), s);
		this.minX = Math.min(this.minX, x);
		this.minY = Math.min(this.minY, y);
		this.minZ = Math.min(this.minZ, z);
		this.maxX = Math.max(this.maxX, x);
		this.maxY = Math.max(this.maxY, y);
		this.maxZ = Math.max(this.maxZ, z);
	}

	public @Nullable BlockState get(int x, int y, int z) {
		return this.blocks.get(key(x, y, z));
	}

	public Long2ObjectMap<BlockState> all() {
		return this.blocks;
	}

	/** A copy without anything above {@code maxY} except where {@code keep} says so. */
	public Voxels cut(int maxY, KeepTest keep) {
		Voxels v = new Voxels();
		for (Long2ObjectMap.Entry<BlockState> e : this.blocks.long2ObjectEntrySet()) {
			long k = e.getLongKey();
			int y = ky(k);
			if (y <= maxY || keep.keep(kx(k), y, kz(k), e.getValue())) {
				v.set(kx(k), y, kz(k), e.getValue());
			}
		}
		return v;
	}

	@FunctionalInterface
	public interface KeepTest {
		boolean keep(int x, int y, int z, BlockState s);
	}

	public int minX() {
		return this.minX;
	}

	public int minY() {
		return this.minY;
	}

	public int minZ() {
		return this.minZ;
	}

	public int maxX() {
		return this.maxX;
	}

	public int maxY() {
		return this.maxY;
	}

	public int maxZ() {
		return this.maxZ;
	}

	public boolean isEmpty() {
		return this.blocks.isEmpty();
	}
}
