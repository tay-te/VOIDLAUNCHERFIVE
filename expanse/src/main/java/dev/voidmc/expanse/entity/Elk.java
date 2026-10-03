package dev.voidmc.expanse.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Elk: the big deer of the moors and the redwoods. Skittish — they keep their distance from anyone
 * walking upright, so you approach a herd by sneaking or by holding out wheat, which they will follow.
 */
public class Elk extends Animal {
	public Elk(EntityType<? extends Elk> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.24F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.9));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.15, this::isFood, false));
		this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, this::spooks, 9.0F, 1.3, 1.7, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.15));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	/** A player spooks the herd unless they are sneaking or offering food. */
	private boolean spooks(net.minecraft.world.entity.LivingEntity entity) {
		return entity instanceof Player p && !p.isShiftKeyDown() && !this.isFood(p.getMainHandItem()) && !this.isFood(p.getOffhandItem());
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.WHEAT) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.APPLE);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return ExpanseEntities.ELK.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HORSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HORSE_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.HORSE_STEP, 0.15F, 1.1F);
	}

	@Override
	public float getVoicePitch() {
		// Deeper than a horse: a bugle, not a whinny.
		return super.getVoicePitch() * 0.7F;
	}
}
