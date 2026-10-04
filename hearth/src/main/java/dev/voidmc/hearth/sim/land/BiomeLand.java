package dev.voidmc.hearth.sim.land;

import dev.voidmc.hearth.sim.chains.BiomeTable;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * What a biome's land gives the survey, resolved once per biome: its tree cover and the log of its woods (read from the
 * biome's vegetation features), the rock under it, the sand and clay of its banks, how fertile and open it is, and its
 * rarities. See {@link BiomeResources}.
 *
 * @param cover     the share of the ground under tree crowns, 0–1
 * @param ore       ore estimate multipliers: coal, iron, copper, gold
 */
public record BiomeLand(String key, float cover, Identifier wood, Identifier rock, Identifier sand, Identifier clay, float fertility, float pasture,
	boolean rocky, boolean salt, boolean sandy, boolean water, boolean snowy, float[] ore, List<BiomeTable.Specialty> specialties) {

	public static final Identifier OAK = Identifier.withDefaultNamespace("oak_log");
	public static final Identifier STONE = Identifier.withDefaultNamespace("stone");
	public static final Identifier SAND = Identifier.withDefaultNamespace("sand");
	public static final Identifier CLAY = Identifier.withDefaultNamespace("clay");

	/**
	 * Land with no biome to read (synthetic terrain in tests and previews): temperate country with woods here and there
	 * ({@code cover} from the caller's noise), or snow and desert at the extremes of temperature.
	 */
	public static BiomeLand plain(float temperature, float cover) {
		boolean snowy = temperature < 0.15F;
		boolean dry = temperature > 1.5F;
		float fertility = snowy || dry ? 0.1F : temperature < 0.4F ? 0.5F : 1F;
		return new BiomeLand("plain", dry ? 0 : cover, OAK, dry ? Identifier.withDefaultNamespace("sandstone") : STONE, SAND, CLAY, fertility,
			dry ? 0.1F : 0.9F, false, false, dry, false, snowy, new float[] {1, 1, 1, 1}, List.of());
	}
}
