package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The trades that work at a bench: smith at the anvil, carpenter at the bench, mason at the stonecutter, baker at the
 * oven, brewer at the stand, tailor at the loom, scholar at the lectern, priest at the altar. Each has its motion,
 * its tools in hand, its sounds and its sparks, chips, smoke or bubbles; between bouts they fetch from the store.
 */
final class CraftWork implements WorkBehaviour {
	private final Profile profile;
	private int bouts;

	CraftWork(JobType job) {
		this.profile = Profile.of(job);
	}

	record Profile(FolkPose pose, ItemStack main, ItemStack off, @Nullable SoundEvent sound, float volume, int interval,
		@Nullable ParticleOptions particle, int count, String what) {
		static Profile of(JobType job) {
			return switch (job) {
				case SMITH -> new Profile(FolkPose.HAMMER, new ItemStack(Items.MACE), new ItemStack(Items.IRON_INGOT), SoundEvents.ANVIL_USE, 0.25F, 22,
					ParticleTypes.ELECTRIC_SPARK, 4, "at the anvil");
				case CARPENTER -> new Profile(FolkPose.HAMMER, new ItemStack(Items.IRON_AXE), ItemStack.EMPTY, SoundEvents.WOOD_HIT, 0.6F, 18,
					new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), 5, "at the bench");
				case MASON -> new Profile(FolkPose.HAMMER, new ItemStack(Items.IRON_PICKAXE), ItemStack.EMPTY, SoundEvents.STONE_HIT, 0.6F, 18,
					new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 5, "dressing stone");
				case BAKER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.WHEAT), ItemStack.EMPTY, SoundEvents.SMOKER_SMOKE, 0.5F, 60,
					ParticleTypes.CAMPFIRE_COSY_SMOKE, 1, "at the ovens");
				case BREWER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.GLASS_BOTTLE), ItemStack.EMPTY, SoundEvents.BREWING_STAND_BREW, 0.4F, 90,
					ParticleTypes.BUBBLE_POP, 4, "brewing");
				case TAILOR -> new Profile(FolkPose.CRAFT, new ItemStack(Items.SHEARS), new ItemStack(Items.STRING), SoundEvents.UI_LOOM_SELECT_PATTERN, 0.5F, 70,
					null, 0, "at the loom");
				case SCHOLAR -> new Profile(FolkPose.READ, new ItemStack(Items.BOOK), ItemStack.EMPTY, SoundEvents.BOOK_PAGE_TURN, 0.6F, 140,
					null, 0, "reading at the lectern");
				case PRIEST -> new Profile(FolkPose.PRAY, ItemStack.EMPTY, ItemStack.EMPTY, null, 0.0F, 200, ParticleTypes.ENCHANT, 6, "tending the altar");
				// ---- the workshops of the chains (docs/LIVING_WORLD.md 7.5)
				case CHARCOAL_BURNER -> new Profile(FolkPose.TEND, new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.CHARCOAL), SoundEvents.CAMPFIRE_CRACKLE,
					0.7F, 50, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 1, "tending the charcoal clamp");
				case KILN_WORKER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.BRICK), new ItemStack(Items.CLAY_BALL), SoundEvents.FURNACE_FIRE_CRACKLE,
					0.6F, 60, ParticleTypes.LARGE_SMOKE, 2, "firing bricks at the kiln");
				case GLASSMAKER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.GLASS_PANE), SoundEvents.FIRE_AMBIENT,
					0.6F, 50, ParticleTypes.FLAME, 3, "blowing glass");
				case SMELTER -> new Profile(FolkPose.HAMMER, new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.RAW_IRON), SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
					0.7F, 40, ParticleTypes.LAVA, 2, "working the smelter");
				case TANNER -> new Profile(FolkPose.TEND, new ItemStack(Items.LEATHER), ItemStack.EMPTY, SoundEvents.BUCKET_EMPTY, 0.4F, 70,
					ParticleTypes.SPLASH, 6, "scraping hides at the vats");
				case BUTCHER -> new Profile(FolkPose.HAMMER, new ItemStack(Items.IRON_AXE), new ItemStack(Items.BEEF), SoundEvents.PLAYER_ATTACK_STRONG, 0.4F,
					30, null, 0, "at the block");
				case CHANDLER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.CANDLE), new ItemStack(Items.HONEYCOMB), SoundEvents.CANDLE_EXTINGUISH, 0.4F,
					80, ParticleTypes.SMALL_FLAME, 1, "dipping candles");
				case PAPERMAKER -> new Profile(FolkPose.CRAFT, new ItemStack(Items.PAPER), new ItemStack(Items.SUGAR_CANE), SoundEvents.BOOK_PAGE_TURN, 0.5F,
					70, ParticleTypes.SPLASH, 3, "pressing paper");
				case DYER -> new Profile(FolkPose.TEND, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.BLUE)), new ItemStack(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE)), SoundEvents.BUCKET_EMPTY, 0.4F, 70,
					ParticleTypes.SPLASH, 5, "at the dye vats");
				case BOATWRIGHT -> new Profile(FolkPose.HAMMER, new ItemStack(Items.IRON_AXE), new ItemStack(Items.OAK_PLANKS), SoundEvents.WOOD_HIT, 0.6F, 18,
					new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), 5, "building a boat");
				default -> new Profile(FolkPose.CRAFT, ItemStack.EMPTY, ItemStack.EMPTY, null, 0.0F, 60, null, 0, "working");
			};
		}
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		Poi site = p.workSite;
		BlockPos stand = site != null ? site.pos() : p.work;
		BlockPos facing = site != null ? site.pos().relative(site.facing()) : null;
		this.bouts++;
		// Every few bouts, a trip to the store for materials.
		if (this.bouts % 4 == 0 && p.workStorage != null) {
			Poi store = p.workStorage;
			return new Tasks.Linger(store.pos(), store.pos().relative(store.facing()), FolkPose.CRAFT, 50, ItemStack.EMPTY, ItemStack.EMPTY,
				Tasks.every(40, SoundEvents.CHEST_OPEN, 0.4F, null, true), "fetching from the store");
		}
		Profile pr = this.profile;
		Tasks.Effect effect = Tasks.every(pr.interval, pr.sound, pr.volume, pr.particle == null ? null : (b, lvl, elapsed, at) -> {
			Vec3 c = at != null ? Vec3.atCenterOf(at).add(0, 0.55, 0) : b.folk.getEyePosition().add(b.folk.getLookAngle());
			lvl.sendParticles(pr.particle, c.x, c.y, c.z, pr.count, 0.15, 0.1, 0.15, 0.02);
		}, pr.pose == FolkPose.HAMMER);
		return new Tasks.Linger(stand, facing, pr.pose, 400 + brain.random.nextInt(400), pr.main.copy(), pr.off.copy(), effect, pr.what);
	}
}
