package dev.voidmc.gloam.motes;

import dev.voidmc.gloam.GloamSettings;
import dev.voidmc.gloam.air.Air;
import dev.voidmc.gloam.air.AirSample;
import dev.voidmc.gloam.air.Noise;
import dev.voidmc.gloam.air.Wind;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Places the motes around the camera, once a client tick, from what is really there and the air at the camera.
 *
 * <p>The ambient kinds (pollen, fireflies, mist, snow) each have a number they should come to around the camera, worked
 * out from the air (the hour, the season, the wind, the rain, the mist); the spawner tops each kind up toward its
 * number a little every tick, and when the air around the camera is nearly empty of a kind (the world just loaded,
 * the camera moved far, the motes were switched back on) fills it at once with motes already partway through their
 * lives, so the air looks as it would have all along. The rest are events: a leaf falls when a probe finds the
 * underside of a canopy and the wind and season say so, a breath when it is someone's turn to breathe out, spray where
 * a waterfall lands.
 *
 * <p>Every kind tests the place it would spawn at: on what the ground is there (a flower, grass, bare earth, water),
 * how much sky it sees (so nothing gathers in houses or caves), and whether it is inside something solid. All of it is
 * held to a spawn budget per tick and the {@code moteLimit} cap.
 */
final class MoteSpawner {
	/** How far around the camera each kind is placed, in blocks. */
	private static final int LEAF_REACH = 16, POLLEN_REACH = 12, FIREFLY_REACH = 22, WISP_REACH = 44, SNOW_REACH = 14, SPRAY_REACH = 40;
	/** The most motes started in one tick at density 1. */
	private static final int BUDGET = 48;

	private final RandomSource random = RandomSource.create();
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	/** Whether each kind of leaf block sheds leaves (broadleaves) or not (needles, and the leaves vanilla drops itself). */
	private final Map<Block, Boolean> sheds = new IdentityHashMap<>();
	/** The average colour of untinted leaf textures, by block. */
	private final Map<Block, Integer> leafColours = new IdentityHashMap<>();
	/** The feet of waterfalls found nearby: packed position to the tick it was last seen. */
	private final Long2IntOpenHashMap feet = new Long2IntOpenHashMap();
	private final List<LivingEntity> breathers = new ArrayList<>();
	private ClientLevel level;
	private int tick, budget, limit, sectionCursor;
	private int breathIn = 40, breathOut;

	void tick(Minecraft mc, ClientLevel level, AirSample air, Vec3 eye, float density, int limit) {
		if (level != this.level) {
			this.level = level;
			this.feet.clear();
			this.breathers.clear();
		}
		this.tick++;
		this.budget = Math.max(1, Mth.ceil(BUDGET * density));
		this.limit = limit;
		BlockPos at = BlockPos.containing(eye);
		// AirSample.indoors counts a leaf canopy as a roof (leaves dim the sky light); only call it indoors when the
		// camera's own sky light agrees, as it does in houses and caves and not under trees
		boolean enclosed = air.indoors() > 0.6F && level.getBrightness(LightLayer.SKY, at) < 10;

		this.leaves(air, eye, density);
		if (!enclosed) {
			this.wisps(air, eye, density);
			this.pollen(air, eye, density);
			this.fireflies(air, eye, density);
			this.snow(air, eye, at, density);
			this.spray(air, eye, density);
		}
		if (GloamSettings.get().breath) {
			this.breath(mc, air, eye, density);
		}
	}

	private boolean room() {
		return this.budget > 0 && Motes.total() < this.limit;
	}

	private void add(Mote mote) {
		Motes.add(mote);
		this.budget--;
	}

	/**
	 * Tops {@code kind} up toward {@code target} motes: a twenty-fifth of the gap a tick, or a quarter of it with motes
	 * already partway through their lives when the air is nearly empty of them. {@code place} makes one mote somewhere
	 * fitting, or null if the place it tried did not fit.
	 */
	private void fill(Motes.Kind kind, int target, Supplier<Mote> place) {
		int gap = target - kind.count();
		if (gap <= 0) {
			return;
		}
		boolean empty = kind.count() < target * 0.3F;
		float want = gap * (empty ? 0.25F : 0.04F);
		int n = (int) want + (this.random.nextFloat() < want - (int) want ? 1 : 0);
		for (int tries = n * 4; n > 0 && tries > 0 && this.room(); tries--) {
			Mote mote = place.get();
			if (mote != null) {
				if (empty) {
					mote.begunAgo(this.random.nextInt(Math.max(1, mote.getLifetime())));
				}
				this.add(mote);
				n--;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------- leaves

	/**
	 * Leaves fall from the undersides of canopies near the camera: random probes in the air around it that land on a
	 * leaf block with air below drop a leaf with a chance set by the season (few in summer, many through autumn) and the
	 * wind (more in gusts). How many fall is then simply how much canopy there is.
	 */
	private void leaves(AirSample air, Vec3 eye, float density) {
		float season = switch (air.season()) {
			case SPRING -> 0.35F;
			case SUMMER -> 0.25F;
			case AUTUMN -> 0.7F + 2.3F * autumn(air.year());
			case WINTER -> 0.2F;
		};
		Wind wind = air.wind();
		float windy = 0.3F + 0.06F * wind.speed() + 1.4F * wind.gust();
		float chance = 0.55F * season * windy;
		int probes = Mth.ceil(60 * Math.min(1.5F, density));
		for (int i = 0; i < probes; i++) {
			int x = Mth.floor(eye.x + (this.random.nextFloat() * 2 - 1) * LEAF_REACH);
			int y = Mth.floor(eye.y - 4 + this.random.nextFloat() * 18);
			int z = Mth.floor(eye.z + (this.random.nextFloat() * 2 - 1) * LEAF_REACH);
			BlockState state = this.level.getBlockState(this.probe.set(x, y, z));
			if (!(state.getBlock() instanceof LeavesBlock) || !this.sheds(state.getBlock())) {
				continue;
			}
			if (!this.level.getBlockState(this.probe.set(x, y - 1, z)).isAir() || this.random.nextFloat() >= chance) {
				continue;
			}
			if (!this.room()) {
				return;
			}
			int colour = this.turn(this.leafColour(state, this.probe.set(x, y, z)), air);
			this.add(new LeafMote(this.level, x + this.random.nextDouble(), y - 0.08, z + this.random.nextDouble(), this.random, colour));
		}
	}

	/** How far into autumn the year is, 0 at its first day .. 1 at its last. */
	private static float autumn(float year) {
		return Math.clamp((year - 0.5F) / 0.25F, 0, 1);
	}

	/** Whether leaves of this block fall here: broadleaves, not needles, nor the ones vanilla already drops. */
	private boolean sheds(Block block) {
		return this.sheds.computeIfAbsent(block, b -> {
			if (b == Blocks.CHERRY_LEAVES || b == Blocks.PALE_OAK_LEAVES || b == Blocks.SPRUCE_LEAVES) {
				return false;
			}
			String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
			return !(path.contains("spruce") || path.contains("pine") || path.contains("fir_") || path.contains("larch") || path.contains("redwood")
				|| path.contains("cedar") || path.contains("palm") || path.contains("lumen"));
		});
	}

	/** The colour of the leaves at {@code pos}: the biome's tint for tinted leaves, the texture's own for the rest. */
	private int leafColour(BlockState state, BlockPos pos) {
		int tint = this.level.getClientLeafTintColor(pos);
		if (tint != -1) {
			return tint;
		}
		return this.leafColours.computeIfAbsent(state.getBlock(), b -> textureColour(b, state.getMapColor(this.level, pos).col));
	}

	/** The average colour of a block's texture ({@code textures/block/<name>.png}), or {@code fallback}. */
	private static int textureColour(Block block, int fallback) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(block);
		Identifier texture = Identifier.fromNamespaceAndPath(key.getNamespace(), "textures/block/" + key.getPath() + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
				long r = 0, g = 0, b = 0, n = 0;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int c = image.getPixel(x, y);
						if ((c >>> 24) > 128) {
							r += c >> 16 & 0xFF;
							g += c >> 8 & 0xFF;
							b += c & 0xFF;
							n++;
						}
					}
				}
				if (n > 0) {
					// the sprite is shaded grey around 0.85, so lift the average to match the block's look
					return 0xFF000000 | Math.min(255, (int) (r / n * 1.12)) << 16 | Math.min(255, (int) (g / n * 1.12)) << 8 | Math.min(255, (int) (b / n * 1.12));
				}
			} catch (Exception ignored) {
				// fall back to the map colour
			}
		}
		return 0xFF000000 | fallback;
	}

	/**
	 * The colour a leaf falls in: its tree's green, except that through autumn more and more of the falling leaves have
	 * turned yellow, orange or russet brown (birch yellow), and a few in late summer.
	 */
	private int turn(int green, AirSample air) {
		float turned = switch (air.season()) {
			case SUMMER -> 0.12F * Noise.smoothstep(0.4F, 0.5F, air.year());
			case AUTUMN -> 0.3F + 0.55F * autumn(air.year());
			case WINTER -> 0.8F;
			case SPRING -> 0.0F;
		};
		if (this.random.nextFloat() >= turned) {
			return green;
		}
		int[][] palette = {{214, 170, 52}, {200, 120, 40}, {170, 84, 36}, {138, 92, 50}, {186, 150, 70}};
		int[] c = palette[this.random.nextInt(palette.length)];
		float mix = 0.75F + this.random.nextFloat() * 0.25F;
		int r = (int) Mth.lerp(mix, green >> 16 & 0xFF, c[0]);
		int g = (int) Mth.lerp(mix, green >> 8 & 0xFF, c[1]);
		int b = (int) Mth.lerp(mix, green & 0xFF, c[2]);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------------------------------------- pollen, dust

	/** Pollen and dust in sunlit air over meadows on warm, still, dry days. */
	private void pollen(AirSample air, Vec3 eye, float density) {
		float h = air.hour();
		float day = Noise.smoothstep(6.8F, 8.5F, h) * (1 - Noise.smoothstep(17.0F, 18.8F, h));
		// pollen through spring and summer, only a little dust in autumn's sun, nothing in winter
		float season = switch (air.season()) {
			case SPRING, SUMMER -> 1.0F;
			case AUTUMN -> 0.3F;
			case WINTER -> 0.0F;
		};
		float warm = season * Noise.smoothstep(-0.3F, 0.3F, air.warmth()) * Noise.smoothstep(0.25F, 0.55F, air.climate().temperature());
		float still = 1 - Noise.smoothstep(3.0F, 7.5F, air.wind().now());
		float dry = (1 - Noise.smoothstep(0.0F, 0.08F, air.rain())) * (1 - Noise.smoothstep(0.15F, 0.5F, air.wetness()));
		float sun = 1 - 0.7F * air.cloudCover();
		float f = day * warm * still * dry * sun;
		if (f < 0.02F) {
			return;
		}
		this.fill(Motes.Kind.POLLEN, (int) (200 * density * f), () -> {
			int[] c = this.column(eye, POLLEN_REACH);
			if (c == null) {
				return null;
			}
			float meadow = this.meadow(c[0], c[1], c[2]);
			if (this.random.nextFloat() >= Math.max(meadow, 0.15F)) {
				return null;
			}
			double y = c[1] + 0.2 + Math.pow(this.random.nextFloat(), 1.5) * 4.5;
			if (this.level.getBrightness(LightLayer.SKY, this.probe.set(c[0], Mth.floor(y), c[2])) < 14) {
				return null;
			}
			return new PollenMote(this.level, c[0] + this.random.nextDouble(), y, c[2] + this.random.nextDouble(), this.random, meadow < 0.3F);
		});
	}

	/** What grows at a column's top: 1 flowers, 0.8 grasses and ferns, 0.4 a bare grass block, 0 anything else. */
	private float meadow(int x, int top, int z) {
		BlockState plant = this.level.getBlockState(this.probe.set(x, top, z));
		if (plant.is(BlockTags.FLOWERS)) {
			return 1.0F;
		}
		if (grass(plant)) {
			return 0.8F;
		}
		return this.level.getBlockState(this.probe.set(x, top - 1, z)).is(Blocks.GRASS_BLOCK) ? 0.4F : 0.0F;
	}

	private static boolean grass(BlockState plant) {
		return plant.is(Blocks.SHORT_GRASS) || plant.is(Blocks.TALL_GRASS) || plant.is(Blocks.FERN) || plant.is(Blocks.LARGE_FERN) || plant.is(Blocks.BUSH)
			|| plant.is(Blocks.FIREFLY_BUSH);
	}

	// ---------------------------------------------------------------------------------------------------- fireflies

	/**
	 * Fireflies at dusk and in the early night, from late spring through summer, low over grass and reeds and by the
	 * water; none in rain, wind, cold or desert heat.
	 */
	private void fireflies(AirSample air, Vec3 eye, float density) {
		float h = air.hour();
		float dusk = h > 12 ? Noise.smoothstep(18.5F, 19.5F, h) * (1 - Noise.smoothstep(23.0F, 24.0F, h)) : 0;
		float season = Noise.smoothstep(0.17F, 0.24F, air.year()) * (1 - Noise.smoothstep(0.42F, 0.5F, air.year()));
		float t = air.climate().temperature();
		float climate = Noise.smoothstep(0.4F, 0.6F, t) * (1 - Noise.smoothstep(1.4F, 1.8F, t));
		float calm = (1 - Noise.smoothstep(0.02F, 0.15F, air.rain())) * (1 - Noise.smoothstep(4.0F, 8.0F, air.wind().now()));
		float f = dusk * season * climate * calm;
		if (f < 0.02F) {
			return;
		}
		float wet = Math.min(1.6F, 0.5F + air.climate().water() + 0.5F * air.climate().downfall());
		float water = air.climate().water();
		this.fill(Motes.Kind.FIREFLY, (int) (300 * density * f * wet), () -> {
			int[] c = this.column(eye, FIREFLY_REACH);
			if (c == null) {
				return null;
			}
			BlockState plant = this.level.getBlockState(this.probe.set(c[0], c[1], c[2]));
			float weight;
			if (grass(plant) || plant.is(BlockTags.FLOWERS) || plant.is(Blocks.SUGAR_CANE) || BuiltInRegistries.BLOCK.getKey(plant.getBlock()).getPath().contains("cattail")) {
				weight = 1.0F;
			} else if (this.level.getFluidState(this.probe.set(c[0], c[1] - 1, c[2])).is(FluidTags.WATER)) {
				weight = 0.3F + 0.5F * water;
			} else {
				// over a lawn or bare ground they still pass, less often
				weight = this.level.getBlockState(this.probe.set(c[0], c[1] - 1, c[2])).is(Blocks.GRASS_BLOCK) ? 0.35F : 0.05F;
			}
			if (this.random.nextFloat() >= weight) {
				return null;
			}
			double y = c[1] + 0.3 + this.random.nextDouble() * 1.8;
			if (this.level.getBrightness(LightLayer.SKY, this.probe.set(c[0], Mth.floor(y), c[2])) < 10) {
				return null;
			}
			return new FireflyMote(this.level, c[0] + this.random.nextDouble(), y, c[2] + this.random.nextDouble(), this.random);
		});
	}

	// --------------------------------------------------------------------------------------------------------- mist

	/**
	 * Veils of mist while the ground mist is up: low over the ground and water around the camera, below the mist's top,
	 * more of them and thicker in the hollows and over water, where the mist lies deepest.
	 */
	private void wisps(AirSample air, Vec3 eye, float density) {
		float mist = air.mist();
		if (mist < 0.04F) {
			return;
		}
		float top = air.mistTop();
		float depth = Air.mistDepth(mist);
		this.fill(Motes.Kind.WISP, (int) (Math.min(320, 60 + 320 * mist) * density), () -> {
			int[] c = this.column(eye, WISP_REACH);
			if (c == null) {
				return null;
			}
			float room = top - c[1];
			if (room < 1.0F) {
				return null;
			}
			boolean water = this.level.getFluidState(this.probe.set(c[0], c[1] - 1, c[2])).is(FluidTags.WATER);
			float deep = Math.clamp(room / depth, 0.25F, 1.0F) * (water ? 1.3F : 1.0F);
			if (this.random.nextFloat() >= deep) {
				return null;
			}
			double y = c[1] + (water ? 0.2 : 0.4) + Math.pow(this.random.nextFloat(), 1.8) * Math.min(room - 0.5, 3 + 6 * mist);
			BlockPos p = this.probe.set(c[0], Mth.floor(y), c[2]);
			if (this.level.getBlockState(p).isSolid() || this.level.getBrightness(LightLayer.SKY, p) < 8) {
				return null;
			}
			float strength = Math.min(0.65F, (0.3F + 0.45F * mist) * (0.55F + 0.45F * deep));
			return new WispMote(this.level, c[0] + this.random.nextDouble(), y, c[2] + this.random.nextDouble(), this.random, strength);
		});
	}

	// --------------------------------------------------------------------------------------------------------- snow

	/** Fine snow streaming on the wind while it snows at the camera, placed upwind so it blows through the view. */
	private void snow(AirSample air, Vec3 eye, BlockPos at, float density) {
		if (air.rain() < 0.03F) {
			return;
		}
		Biome biome = this.level.getBiome(at).value();
		if (biome.getPrecipitationAt(at, this.level.getSeaLevel()) != Biome.Precipitation.SNOW) {
			return;
		}
		Wind wind = air.wind();
		float blow = 0.6F + Math.min(1.0F, wind.now() / 8.0F);
		this.fill(Motes.Kind.SNOW, (int) (200 * density * air.rain() * blow), () -> {
			double x = eye.x + (this.random.nextDouble() * 2 - 1) * SNOW_REACH - wind.x() * 6;
			double y = eye.y - 3 + this.random.nextDouble() * 10;
			double z = eye.z + (this.random.nextDouble() * 2 - 1) * SNOW_REACH - wind.z() * 6;
			if (this.level.getBrightness(LightLayer.SKY, this.probe.set(x, y, z)) < 15) {
				return null;
			}
			return new SnowMote(this.level, x, y, z, this.random);
		});
	}

	// ------------------------------------------------------------------------------------------------------- spray

	/**
	 * Spray where waterfalls land. The sections around the camera are searched a few a tick for falling water at least
	 * three blocks tall standing on something that is not falling water (the foot of a fall); each foot found then
	 * throws up spray every few ticks until it is gone.
	 */
	private void spray(AirSample air, Vec3 eye, float density) {
		this.findFeet(eye);
		if (this.feet.isEmpty()) {
			return;
		}
		double reach = SPRAY_REACH * SPRAY_REACH;
		for (Long2IntMap.Entry e : this.feet.long2IntEntrySet()) {
			BlockPos foot = BlockPos.of(e.getLongKey());
			if (foot.distToCenterSqr(eye) > reach || this.random.nextFloat() >= 0.5F * density || !this.room()) {
				continue;
			}
			double x = foot.getX() + 0.5 + (this.random.nextDouble() - 0.5) * 1.2;
			double z = foot.getZ() + 0.5 + (this.random.nextDouble() - 0.5) * 1.2;
			this.add(new SprayMote(this.level, x, foot.getY() + 0.2 + this.random.nextDouble() * 0.6, z, this.random));
		}
	}

	private void findFeet(Vec3 eye) {
		int cx = Mth.floor(eye.x) >> 4, cz = Mth.floor(eye.z) >> 4, cy = Mth.floor(eye.y) >> 4;
		for (int n = 0; n < 6; n++) {
			int i = this.sectionCursor++ % 125;
			int sx = cx + i % 5 - 2, sz = cz + i / 5 % 5 - 2, sy = cy + i / 25 - 2;
			if (sy < this.level.getMinSectionY() || sy > this.level.getMaxSectionY()) {
				continue;
			}
			LevelChunk chunk = this.level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
			if (chunk == null) {
				continue;
			}
			LevelChunkSection section = chunk.getSection(this.level.getSectionIndexFromSectionY(sy));
			if (section.hasOnlyAir() || !section.maybeHas(MoteSpawner::falling)) {
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!falling(section.getBlockState(x, y, z))) {
							continue;
						}
						int wx = (sx << 4) + x, wy = (sy << 4) + y, wz = (sz << 4) + z;
						if (falling(this.level.getBlockState(this.probe.set(wx, wy - 1, wz)))) {
							continue;
						}
						if (falling(this.level.getBlockState(this.probe.set(wx, wy + 1, wz))) && falling(this.level.getBlockState(this.probe.set(wx, wy + 2, wz)))) {
							this.feet.put(BlockPos.asLong(wx, wy, wz), this.tick);
						}
					}
				}
			}
		}
		// a foot not seen again in a full sweep (125 sections, 21 ticks) and a bit is gone
		this.feet.long2IntEntrySet().removeIf(e -> this.tick - e.getIntValue() > 60);
	}

	private static boolean falling(BlockState state) {
		FluidState fluid = state.getFluidState();
		return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValue(FlowingFluid.FALLING);
	}

	// ------------------------------------------------------------------------------------------------------- breath

	/**
	 * Breath in the cold: the player breathes out every three to five seconds, and so do the people and animals
	 * nearby, each on its own rhythm. How cold it feels comes from the biome, the season, the hour, the height and the
	 * rain. Breath shows indoors too (an unheated barn is still cold).
	 */
	private void breath(Minecraft mc, AirSample air, Vec3 eye, float density) {
		float cold = coldness(air, eye.y);
		if (cold <= 0.0F) {
			this.breathers.clear();
			return;
		}
		LocalPlayer player = mc.player;
		if (player != null && !player.isEyeInFluid(FluidTags.WATER)) {
			if (--this.breathIn <= 0) {
				this.breathIn = 60 + this.random.nextInt(45);
				this.breathOut = 6;
			}
			if (this.breathOut > 0 && this.random.nextFloat() < Math.max(0.5F, density)) {
				this.breathOut--;
				Camera camera = mc.gameRenderer.mainCamera();
				Vec3 look;
				Vec3 mouth;
				if (camera.isDetached()) {
					look = player.getLookAngle();
					mouth = player.getEyePosition().add(look.scale(0.35)).add(0, -0.1, 0);
				} else {
					Vector3fc f = camera.forwardVector();
					look = new Vec3(f.x(), f.y(), f.z());
					mouth = eye.add(look.scale(0.45)).add(0, -0.22, 0);
				}
				this.puff(mouth, look, cold);
			}
		}
		if (this.tick % 10 == 0) {
			this.breathers.clear();
			AABB box = new AABB(eye, eye).inflate(16);
			for (Entity e : this.level.getEntities((Entity) null, box, e -> e instanceof LivingEntity l && l.isAlive() && e != player
				&& (e instanceof Player || e instanceof AbstractVillager || e instanceof Animal))) {
				if (this.breathers.size() < 12) {
					this.breathers.add((LivingEntity) e);
				}
			}
		}
		for (LivingEntity e : this.breathers) {
			// each its own rhythm, from its id: out for five ticks of every 70-ish
			int period = 64 + Math.floorMod(e.getId() * 31, 30);
			if (Math.floorMod(this.tick + e.getId() * 53, period) < 5 && e.isAlive() && !e.isEyeInFluid(FluidTags.WATER)) {
				Vec3 look = e.getLookAngle();
				this.puff(e.getEyePosition().add(look.scale(e.getBbWidth() * 0.6 + 0.1)).add(0, -0.08, 0), look, cold);
			}
		}
	}

	private void puff(Vec3 mouth, Vec3 look, float cold) {
		if (!this.room()) {
			return;
		}
		double speed = 0.022 + this.random.nextDouble() * 0.012;
		BreathMote mote = new BreathMote(this.level, mouth.x, mouth.y, mouth.z, this.random, look.x * speed + (this.random.nextDouble() - 0.5) * 0.01,
			look.y * speed - 0.004, look.z * speed + (this.random.nextDouble() - 0.5) * 0.01);
		mote.thin(cold);
		this.add(mote);
	}

	/**
	 * How cold it feels, 0 (no breath shows) .. 1 (well below freezing): the biome's temperature, warmed or chilled by
	 * the season, chilled at night (coldest before dawn), by height (vanilla's lapse rate above y 80) and by rain.
	 */
	static float coldness(AirSample air, double y) {
		float t = air.climate().temperature() + 0.35F * air.warmth();
		if (y > 80) {
			t -= (float) (y - 80) * 0.00125F;
		}
		t -= 0.2F * (0.5F + 0.5F * (float) Math.cos(2 * Math.PI * (air.hour() - 4) / 24));
		t -= 0.1F * air.rain();
		return 1 - Noise.smoothstep(0.05F, 0.45F, t);
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/**
	 * A random column within {@code reach} of the camera (spread evenly over the disc), as {x, top, z}: {@code top} the
	 * first block above the ground or water surface. Null where the chunk is not loaded or the column's top lies far
	 * above or below the camera.
	 */
	private int[] column(Vec3 eye, int reach) {
		double r = reach * Math.sqrt(this.random.nextDouble());
		double a = this.random.nextDouble() * Math.PI * 2;
		int x = Mth.floor(eye.x + Math.cos(a) * r), z = Mth.floor(eye.z + Math.sin(a) * r);
		if (!this.level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(top - eye.y) > 40) {
			return null;
		}
		return new int[]{x, top, z};
	}
}
