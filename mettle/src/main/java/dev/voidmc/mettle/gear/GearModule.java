package dev.voidmc.mettle.gear;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.GearService;
import dev.voidmc.mettle.api.GearSlot;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.core.CoreModule;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Gear (BURDEN.md §5–§8): the backpack, belt and quiver worn in three slots on the character, their pockets, carry
 * capacity, quiver ammo, belt quick use, and death. The menu patches live in {@code mixin.gear}; the screens and the
 * worn-gear render layer in {@code client.gear}.
 */
public final class GearModule {
	/** Models with in-place changes to commit at the end of the tick. */
	static final Set<GearInventory> DIRTY = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	private static final GearService SERVICE = new GearService() {
		@Override
		public ItemStack worn(Player player, GearSlot slot) {
			return GearInventory.of(player).worn(slot);
		}

		@Override
		public List<ItemStack> pockets(Player player, GearSlot slot) {
			return Collections.unmodifiableList(GearInventory.of(player).pockets(slot));
		}

		@Override
		public boolean isGear(ItemStack stack) {
			return stack.has(GearComponents.GEAR);
		}
	};

	private GearModule() {
	}

	public static void init() {
		GearComponents.init();
		GearItems.init();
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Mettle.id("gear_upgrade"), GearUpgradeRecipe.SERIALIZER);
		// Loads the attachment type so it registers during init.
		Mettle.LOGGER.debug("gear attachment {}", WornGear.TYPE.identifier());
		MettleServices.setGear(SERVICE);

		PayloadTypeRegistry.serverboundPlay().register(BeltUse.Request.TYPE, BeltUse.Request.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BeltUse.Request.TYPE, (payload, context) -> BeltUse.use(context.player(), payload.pocket()));

		ServerPlayerEvents.JOIN.register(GearEffects::apply);
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			// Fabric copies the attachment on a dimension return; after a death, only keepInventory keeps the gear.
			GearInventory.of(oldPlayer).flush();
			ServerLevel level = newPlayer.level();
			if (alive || level.getGameRules().get(GameRules.KEEP_INVENTORY) || oldPlayer.isSpectator()) {
				WornGear worn = oldPlayer.getAttached(WornGear.TYPE);
				if (worn != null) {
					newPlayer.setAttached(WornGear.TYPE, worn);
				}
			}
		});
		// Commit again once the client has its new player: a fresh snapshot syncs, and capacity is rebuilt (respawning
		// drops every modifier).
		ServerPlayerEvents.AFTER_RESPAWN.register(CoreModule.AFTER_ATTACHMENTS, (oldPlayer, newPlayer, alive) -> GearInventory.of(newPlayer).commit());
		ServerPlayerEvents.LEAVE.register(player -> {
			BeltUse.end(player);
			GearInventory.of(player).flush();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			BeltUse.tick(server);
			if (!DIRTY.isEmpty()) {
				for (GearInventory gear : List.copyOf(DIRTY)) {
					gear.flush();
				}
				DIRTY.clear();
			}
		});

		// Right-click gear in hand to put it on, swapping with what is worn, like armour.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			GearSpec spec = stack.get(GearComponents.GEAR);
			if (spec == null || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				GearInventory gear = GearInventory.of(player);
				ItemStack old = gear.worn(spec.slot());
				gear.setWorn(spec.slot(), stack.copyWithCount(1));
				stack.shrink(1);
				if (stack.isEmpty()) {
					player.setItemInHand(hand, old);
				} else if (!old.isEmpty() && !player.getInventory().add(old)) {
					player.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 1f);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** After every change to a player's gear: capacity, and the load recompute that burden hangs off this trigger. */
	static void changed(ServerPlayer player) {
		GearEffects.apply(player);
		CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), ItemStack.EMPTY);
	}

	/**
	 * Whether this exact stack lives in the player's worn quiver: a bow or crossbow that drew {@code arrowStack} (the
	 * stack {@code Player#getProjectile} returned) took it from the quiver. For Marksmanship's Quiverwise.
	 */
	public static boolean fromQuiver(Player player, ItemStack arrowStack) {
		return !arrowStack.isEmpty() && GearInventory.of(player).inQuiver(arrowStack);
	}

	/**
	 * Puts a piece of gear on if its slot is free (kits). The stack is consumed when worn.
	 *
	 * @return whether it was worn
	 */
	public static boolean wear(Player player, ItemStack stack) {
		GearSpec spec = stack.get(GearComponents.GEAR);
		if (spec == null) {
			return false;
		}
		GearInventory gear = GearInventory.of(player);
		if (!gear.worn(spec.slot()).isEmpty()) {
			return false;
		}
		gear.setWorn(spec.slot(), stack.split(1));
		return true;
	}

	/** Death without keepInventory (from {@code Player#dropEquipment}): vanishing gear is gone, the rest drops. */
	public static void dropOnDeath(Player player) {
		GearInventory gear = GearInventory.of(player);
		gear.removeIf(s -> EnchantmentHelper.has(s, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP));
		for (ItemStack stack : gear.takeAll()) {
			ItemEntity drop = player.createItemStackToDrop(stack, true, false);
			if (drop != null) {
				player.level().addFreshEntity(drop);
			}
		}
	}
}
