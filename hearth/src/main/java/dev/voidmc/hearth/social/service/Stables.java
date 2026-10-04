package dev.voidmc.hearth.social.service;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.Venues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The stable: hire a mount (a fee per day and a deposit, both kept on the player's record; return it at any stable for
 * the deposit, less days overdue) or buy one outright. Desert and mesa towns keep camels, mountain towns mules,
 * everyone else horses. A hired mount carries the tag {@code hearth.hired} so the law can tell it is the stable's.
 */
public final class Stables {
	public static final String HIRED_TAG = "hearth.hired";

	private Stables() {
	}

	public enum Result {
		OK, ALREADY_HIRED, NOT_HIRED, TOO_FAR, CANNOT_PAY, NO_ROOM
	}

	/** The kind of mount a culture keeps. */
	public static EntityType<? extends AbstractHorse> mount(Culture c) {
		return switch (c) {
			case DESERT, MESA -> EntityTypes.CAMEL;
			case ALPINE -> EntityTypes.MULE;
			default -> EntityTypes.HORSE;
		};
	}

	public static Result hire(ServerPlayer player, HearthWorld world, Settlement s, Resident keeper) {
		SocialConfig.Stables cfg = SocialModule.config().stables();
		PlayerLife life = PlayerLife.of(player);
		if (life.hire().isPresent() && findMount(world.level(), life.hire().get()) != null) {
			return Result.ALREADY_HIRED;
		}
		long cost = (long) cfg.hirePerDay() + cfg.deposit();
		if (!Hooks.charge(player, cost, keeper, s)) {
			return Result.CANNOT_PAY;
		}
		AbstractHorse horse = spawn(player, s, keeper, false);
		if (horse == null) {
			Hooks.pay(player, cost);
			return Result.NO_ROOM;
		}
		horse.addTag(HIRED_TAG);
		s.deposit(cost);
		s.markDirty();
		PlayerLife.set(player, life.withHire(new PlayerLife.Hire(horse.getUUID(), s.id(), cfg.deposit(), TownLife.today(world, s), 1)));
		return Result.OK;
	}

	public static Result buy(ServerPlayer player, Settlement s, Resident keeper) {
		SocialConfig.Stables cfg = SocialModule.config().stables();
		if (!Hooks.charge(player, cfg.buy(), keeper, s)) {
			return Result.CANNOT_PAY;
		}
		AbstractHorse horse = spawn(player, s, keeper, true);
		if (horse == null) {
			Hooks.pay(player, cfg.buy());
			return Result.NO_ROOM;
		}
		s.deposit(cfg.buy());
		s.markDirty();
		return Result.OK;
	}

	/**
	 * Returns the hired mount (it must be near); refunds the deposit less the fee for days past those paid, from this
	 * town's treasury (a deposit is honoured at any stable). Out param: bits refunded.
	 */
	public static Result giveBack(ServerPlayer player, HearthWorld world, Settlement s, long[] refunded) {
		SocialConfig.Stables cfg = SocialModule.config().stables();
		PlayerLife life = PlayerLife.of(player);
		if (life.hire().isEmpty()) {
			return Result.NOT_HIRED;
		}
		PlayerLife.Hire hire = life.hire().get();
		AbstractHorse horse = findMount(world.level(), hire);
		if (horse == null) {
			// lost or dead: the deposit is forfeit
			PlayerLife.set(player, life.withHire(null));
			return Result.NOT_HIRED;
		}
		if (horse.distanceToSqr(player) > (double) cfg.returnRadius() * cfg.returnRadius()) {
			return Result.TOO_FAR;
		}
		long overdue = Math.max(0, TownLife.today(world, s) - hire.day() - hire.paidDays() + 1);
		long refund = Math.max(0, hire.deposit() - overdue * cfg.hirePerDay());
		refund = Math.min(refund, s.treasury());
		if (refund > 0 && s.withdraw(refund)) {
			Hooks.pay(player, refund);
		} else {
			refund = 0;
		}
		horse.ejectPassengers();
		horse.discard();
		s.markDirty();
		PlayerLife.set(player, life.withHire(null));
		refunded[0] = refund;
		return Result.OK;
	}

	/** The hired mount, if it is loaded in this level. */
	public static @Nullable AbstractHorse findMount(ServerLevel level, PlayerLife.Hire hire) {
		Entity e = level.getEntity(hire.horse());
		return e instanceof AbstractHorse h && h.isAlive() ? h : null;
	}

	/** A saddled mount by the stable's hitching post (or beside the player), tamed to them. */
	static @Nullable AbstractHorse spawn(ServerPlayer player, Settlement s, Resident keeper, boolean bought) {
		ServerLevel level = (ServerLevel) player.level();
		AbstractHorse horse = mount(s.culture()).create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return null;
		}
		BlockPos at = null;
		List<Poi> posts = s.pois(PoiType.HITCHING_POST);
		if (!posts.isEmpty()) {
			BlockPos post = Venues.nearest(Venues.positions(posts), player.blockPosition());
			if (post.distSqr(player.blockPosition()) <= 48 * 48) {
				at = Venues.standable(level, post);
			}
		}
		if (at == null) {
			for (int i = 0; i < 8 && at == null; i++) {
				double a = i * Math.PI / 4;
				BlockPos p = player.blockPosition().offset((int) Math.round(Math.cos(a) * 3), 0, (int) Math.round(Math.sin(a) * 3));
				BlockPos ok = Venues.standable(level, p);
				if (ok != null && level.noCollision(horse.getType().getSpawnAABB(ok.getX() + 0.5, ok.getY(), ok.getZ() + 0.5))) {
					at = ok;
				}
			}
		}
		if (at == null) {
			return null;
		}
		horse.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0);
		horse.setTamed(true);
		horse.setOwner(player);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		// a bought mount is the better animal
		double speed = bought ? 0.27 : 0.22;
		double jump = bought ? 0.8 : 0.6;
		double health = bought ? 28 : 22;
		if (!(horse instanceof Camel)) { // a camel keeps its own gait and dash
			var sp = horse.getAttribute(Attributes.MOVEMENT_SPEED);
			if (sp != null) {
				sp.setBaseValue(speed);
			}
			var jp = horse.getAttribute(Attributes.JUMP_STRENGTH);
			if (jp != null) {
				jp.setBaseValue(jump);
			}
		}
		var hp = horse.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(health);
			horse.setHealth((float) health);
		}
		horse.setPersistenceRequired();
		if (!level.addFreshEntity(horse)) {
			return null;
		}
		level.playSound(null, horse.blockPosition(), net.minecraft.sounds.SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable(bought ? "hearth.social.stable.yours" : "hearth.social.stable.hired_where",
			Component.translatable(horse.getType().getDescriptionId())).withStyle(ChatFormatting.GRAY));
		return horse;
	}

	/** A hired mount died: the hire is over and the deposit forfeit. */
	public static void mountDied(ServerPlayer owner) {
		PlayerLife life = PlayerLife.of(owner);
		if (life.hire().isPresent()) {
			PlayerLife.set(owner, life.withHire(null));
			owner.sendSystemMessage(Component.translatable("hearth.social.stable.died").withStyle(ChatFormatting.RED));
		}
	}
}
