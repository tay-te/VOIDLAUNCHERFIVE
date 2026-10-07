package dev.voidmc.lod.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One tile of far terrain as VOID LOD draws it for this player: {@code size} x {@code size} cells, {@code cell} blocks
 * apart, the first at ({@code minX}, {@code minZ}); every array row-major (x fastest), a fresh copy the listener may
 * keep. A far tile is coarse: one cell per 512 blocks at the horizon, one per block next to the chunks.
 *
 * @param dimension the level it is in
 * @param revision grows with every tile handed out: of two tiles covering the same ground, the later revision is the
 *     newer picture (a tile redrawn at a finer cell size, or rebuilt after the land changed)
 * @param heights the top's y per cell (the water's surface where wet), {@link Short#MIN_VALUE} where there is nothing
 * @param colors the top face's colour per cell, 0xRRGGBB, as drawn (biome tints, snow, water mixed in)
 * @param blockPalette the top blocks met in this tile
 * @param blocks per cell, an index into {@code blockPalette}, or -1 for a block not known
 * @param biomePalette the biomes met in this tile
 * @param biomes per cell, an index into {@code biomePalette}, or -1 for a biome not known (land saved before biomes
 *     were kept)
 * @param seen per cell, whether the chunks the player loaded showed most of it (true) or the world generator worked it
 *     out (false). Generator cells exist only where the player's own game has the generator: singleplayer and a LAN
 *     host. On a multiplayer server every cell is seen, and land the player never loaded is not drawn at all.
 */
public record VisibleTerrain(ResourceKey<Level> dimension, long revision, int minX, int minZ, int cell, int size, short[] heights,
	int[] colors, BlockState[] blockPalette, byte[] blocks, ResourceKey<Biome>[] biomePalette, byte[] biomes, boolean[] seen) {
}
