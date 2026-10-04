package dev.voidmc.lod.source;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.lod.core.ColumnFiles;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Saves materials as what they are rather than as this session's ids: a block state the way commands write it,
 * and which face ({@code minecraft:grass_block[snowy=false]@top}). A block a removed mod added comes back as
 * material 0, drawn by its saved colour alone. Safe from the file thread: it only reads the frozen block registry.
 */
public final class MaterialNames implements ColumnFiles.Names {
	private final Materials materials;
	/** Names already parsed: every region file repeats the same few dozen. */
	private final ConcurrentHashMap<String, Integer> parsed = new ConcurrentHashMap<>();

	public MaterialNames(Materials materials) {
		this.materials = materials;
	}

	@Override
	public String name(int id) {
		Materials.Face face = this.materials.face(id);
		return face == null ? null : BlockStateParser.serialize(face.state()) + (face.top() ? "@top" : "@side");
	}

	@Override
	public int id(String name) {
		return this.parsed.computeIfAbsent(name, n -> {
			int at = n.lastIndexOf('@');
			if (at < 0) {
				return 0;
			}
			try {
				BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, n.substring(0, at), false).blockState();
				return this.materials.id(state, n.endsWith("@top"));
			} catch (CommandSyntaxException | RuntimeException e) {
				return 0;
			}
		});
	}
}
