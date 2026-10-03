package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Deer: the woodland's own grazers, smaller and quicker than the elk of the moors and taiga. Shy: a
 * herd keeps well clear of anyone walking upright (sneak, or hold out an apple, and they let you near),
 * and runs from wolves, flashing the white of their tails as they bound off. Bucks carry antlers, does
 * and fawns don't; fawns are spotted.
 */
public class Deer extends Animal {
	public Deer(EntityType<? extends Deer> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.25F)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 2.1));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, this::spooks, 12.0F, 1.5, 2.0, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
		this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Wolf.class, 12.0F, 1.5, 2.0));
		this.goalSelector.addGoal(5, new FollowParentGoal(this, 1.2));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	/** A player spooks the herd unless they are sneaking or offering food. */
	private boolean spooks(LivingEntity entity) {
		return entity instanceof Player p && !p.isShiftKeyDown() && !this.isFood(p.getMainHandItem()) && !this.isFood(p.getOffhandItem());
	}

	/** Bucks have antlers: half the adults, fixed by the deer's identity so it never changes. */
	public boolean isBuck() {
		return (this.getUUID().getLeastSignificantBits() & 1L) == 0L;
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return stack.is(Items.APPLE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.WHEAT) || stack.is(Items.CARROT);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return LifeEntities.DEER.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		// Deer are quiet: a soft snort now and then.
		return this.random.nextInt(3) == 0 ? SoundEvents.HORSE_BREATHE : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.HORSE_STEP, 0.1F, 1.3F);
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 1.25F;
	}
}
