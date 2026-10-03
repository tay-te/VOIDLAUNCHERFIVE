package dev.voidmc.expanse.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Shore crab: scuttles sideways along warm beaches and breathes underwater, so a startled crab often
 * just walks into the sea. The renderer turns the model a quarter so it walks the way crabs do.
 */
public class Crab extends Animal {
	public Crab(EntityType<? extends Crab> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.22F);
	}

	/** Crabs come up on sand and gravel shores, not grass. */
	public static boolean checkCrabSpawnRules(EntityType<Crab> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(BlockTags.SAND) || below.is(net.minecraft.world.level.block.Blocks.GRAVEL))
			&& (EntitySpawnReason.ignoresLightRequirements(reason) || level.getRawBrightness(pos, 0) > 8);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.8));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.2, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.2));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0, 60));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 5.0F));
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.KELP);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return ExpanseEntities.CRAB.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.TURTLE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.TURTLE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SILVERFISH_STEP, 0.15F, 1.5F);
	}
}
