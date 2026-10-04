package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.lock.LockpickSessions;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.own.OwnerRules;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.pocket.PickpocketSessions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * How the law learns of deeds (docs/LIVING_WORLD.md §4): breaking blocks in someone's plot (vandalism of the blueprint;
 * crops taken; a chest smashed open), using locked doors and chests (the key, the pick, or "it's locked"), opening owned
 * containers (watched by {@link TheftWatch}), picking berries, leading off or killing penned animals, picking pockets,
 * hurting and killing folk, and PvP inside towns. Each is checked only when it happens; nothing here runs per tick
 * except the short list of drops waiting to be marked.
 */
public final class CrimeHooks {
	private static final Identifier FOLK = Hearth.id("folk");
	/** A hit on a folk counts as one assault per this many ticks. */
	private static final long ASSAULT_TICKS = 200;
	private static final Map<Integer, Victim> VICTIMS = new HashMap<>();
	private static final List<DropCheck> DROPS = new ArrayList<>();

	private CrimeHooks() {
	}

	/** A folk recently hurt by a player: who they were (the entity's resident is gone once they die). */
	private record Victim(UUID resident, long settlementId, UUID attacker, long lastAssault, long until) {
	}

	/** Items a use is about to drop (berries picked), marked stolen on the next tick. */
	private record DropCheck(ServerPlayer player, BlockPos pos, Owner owner, long settlementId, long tick) {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(CrimeHooks::afterBreak);
		UseBlockCallback.EVENT.register(CrimeHooks::useBlock);
		UseEntityCallback.EVENT.register(CrimeHooks::useEntity);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CrimeHooks::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CrimeHooks::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(CrimeHooks::afterDeath);
	}

	// ---------------------------------------------------------------- blocks

	private static void afterBreak(Level world, Player p, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(world instanceof ServerLevel level) || !(p instanceof ServerPlayer player) || Law.exempt(player) || !Law.config().enabled()) {
			return;
		}
		Ownership.Place place = Ownership.place(level, pos);
		if (place == null || place.plot() == null) {
			return;
		}
		PlotView plot = place.plot();
		Settlement s = place.settlement();
		Owner owner = place.owner();
		ArchetypeCategory category = OwnerRules.category(plot.spec().archetype());
		if (category == ArchetypeCategory.EXTRACTION || owner != null && Ownership.isOwner(owner, player.getUUID())) {
			return;
		}
		if (owner != null && owner.kind() == Owner.Kind.PLAYER && !Law.config().protectPlayerProperty()) {
			return;
		}
		if (owner != null && isCrop(state)) {
			long value = markDrops(level, pos, player, s, owner);
			CrimeDesk.commit(level, player, s, Offence.THEFT, pos, owner.resident(), Math.max(1, value), 0.15, null);
			return;
		}
		if (owner != null && blockEntity instanceof Container) {
			long value = markDrops(level, pos, player, s, owner);
			if (value > 0) {
				CrimeDesk.commit(level, player, s, Offence.THEFT, pos, owner.resident(), value, 0.6, null);
			}
		}
		if (plot.state() == PlotState.PLANNED || !isBlueprintBlock(level, plot, pos)) {
			return;
		}
		long value = Law.config().fines().vandalismPerBlock();
		double noise = 0.5;
		Locks.Lock lock = Locks.at(level, pos, state);
		if (lock != null && lock.locked()) {
			// a forced lock: loud, and found in the morning even if nobody heard it
			Locks.markBroken(s, lock.pos());
			value += Law.config().fines().vandalismPerBlock() * 4L;
			noise = 1.0;
		}
		UUID victim = owner == null ? null : owner.resident();
		CrimeDesk.commit(level, player, s, Offence.VANDALISM, pos, victim, value, noise, null);
	}

	static boolean isCrop(BlockState state) {
		return state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock || state.getBlock() instanceof SweetBerryBushBlock
			|| state.getBlock() instanceof CocoaBlock || state.getBlock() instanceof NetherWartBlock || state.is(net.minecraft.world.level.block.Blocks.PUMPKIN)
			|| state.is(net.minecraft.world.level.block.Blocks.MELON);
	}

	/** Whether {@code pos} is one of the blocks the plot's built stages placed (so breaking it damages the building). */
	static boolean isBlueprintBlock(ServerLevel level, PlotView plot, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		if (world == null || !HearthServices.hasBlueprints()) {
			return false;
		}
		try {
			Blueprint bp = HearthServices.blueprints().blueprint(plot.spec(), world.terrain());
			int to = plot.state() == PlotState.BUILT || plot.state() == PlotState.ABANDONED ? bp.stageCount() : Math.min(bp.stageCount(), plot.stage() + 1);
			boolean[] hit = {false};
			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();
			bp.forEachInChunk(x >> 4, z >> 4, 0, to, (sx, sy, sz, state, tag) -> {
				if (sx == x && sy == y && sz == z) {
					hit[0] = !state.isAir();
				}
			});
			return hit[0];
		} catch (RuntimeException e) {
			Hearth.LOG.debug("law: no blueprint for {}", plot.spec().archetype(), e);
			return false;
		}
	}

	/** Marks the items that just dropped at {@code pos} as stolen from {@code owner}; returns what they are worth. */
	private static long markDrops(ServerLevel level, BlockPos pos, ServerPlayer thief, Settlement s, Owner owner) {
		long value = 0;
		String name = Ownership.name(owner, level.getServer()).getString();
		long day = Law.day(level);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5), e -> e.tickCount <= 1)) {
			ItemStack stack = item.getItem();
			if (stack.has(dev.voidmc.hearth.law.item.LawComponents.STOLEN)) {
				continue;
			}
			stack.remove(dev.voidmc.hearth.law.item.LawComponents.OWNED);
			StolenGoods.mark(stack, owner, name, s.name(), day, thief.getUUID());
			item.setItem(stack);
			value += Law.value(s, stack);
		}
		return value;
	}

	/** Marks drops queued by uses (berries picked by hand) once they have spawned. Every tick, usually nothing. */
	public static void tick(MinecraftServer server) {
		if (DROPS.isEmpty()) {
			return;
		}
		for (Iterator<DropCheck> it = DROPS.iterator(); it.hasNext(); ) {
			DropCheck d = it.next();
			ServerLevel level = d.player.level();
			if (level.getGameTime() - d.tick < 1) {
				continue;
			}
			it.remove();
			Settlement s = Ownership.settlement(d.settlementId, server);
			if (s == null || d.player.isRemoved()) {
				continue;
			}
			long value = markDrops(level, d.pos, d.player, s, d.owner);
			if (value > 0) {
				CrimeDesk.commit(level, d.player, s, Offence.THEFT, d.pos, d.owner.resident(), value, 0.1, null);
			}
		}
	}

	private static InteractionResult useBlock(Player p, Level world, InteractionHand hand, BlockHitResult hit) {
		if (!(world instanceof ServerLevel level) || !(p instanceof ServerPlayer player) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		boolean exempt = Law.exempt(player);
		if (Locks.lockable(state) && !exempt) {
			Locks.Lock lock = Locks.at(level, pos, state);
			if (lock != null && lock.locked() && !Locks.hasKey(player, lock) && !(player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()
				&& !player.getMainHandItem().is(dev.voidmc.hearth.law.item.LawItems.LOCKPICK))) {
				if (hand == InteractionHand.MAIN_HAND) {
					if (player.getMainHandItem().is(dev.voidmc.hearth.law.item.LawItems.LOCKPICK)) {
						if (!LockpickSessions.active(player.getUUID())) {
							LockpickSessions.start(player, lock, hit);
						}
					} else {
						level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
						player.sendOverlayMessage(Component.translatable("hearth.law.locked", Component.translatable("hearth.law.lock.tier." + lock.tier()))
							.withStyle(ChatFormatting.GOLD));
					}
				}
				// the client already swung the door: put both halves back as the server has them
				player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, lock.pos()));
				if (lock.door()) {
					player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, lock.pos().above()));
				}
				return InteractionResult.FAIL;
			}
		}
		if (exempt || !Law.config().enabled() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		boolean container = state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container;
		boolean berries = state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) > 1;
		if (!container && !berries) {
			return InteractionResult.PASS;
		}
		Ownership.Place place = Ownership.place(level, pos);
		if (place == null || place.owner() == null || Ownership.isOwner(place.owner(), player.getUUID())) {
			return InteractionResult.PASS;
		}
		if (place.owner().kind() == Owner.Kind.PLAYER && !Law.config().protectPlayerProperty()) {
			return InteractionResult.PASS;
		}
		if (container) {
			TheftWatch.expect(player, pos, place.owner(), place.settlement());
		} else if (Law.config().crimes().crops()) {
			DROPS.add(new DropCheck(player, pos.immutable(), place.owner(), place.settlement().id(), level.getGameTime()));
		}
		return InteractionResult.PASS;
	}

	// ---------------------------------------------------------------- entities

	private static InteractionResult useEntity(Player p, Level world, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(world instanceof ServerLevel level) || !(p instanceof ServerPlayer player) || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (Law.exempt(player) || !Law.config().enabled()) {
			return InteractionResult.PASS;
		}
		if (isFolk(entity)) {
			FolkHandle folk = handle(level, entity);
			if (folk != null && !PickpocketSessions.active(player.getUUID()) && PickpocketSessions.canTry(player, folk)) {
				PickpocketSessions.start(player, folk);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (entity instanceof Animal animal && player.getMainHandItem().is(Items.LEAD) && Law.config().crimes().animals()) {
			Owner owner = Ownership.owner(animal);
			if (owner != null && !Ownership.isOwner(owner, player.getUUID()) && !animal.isLeashed()) {
				Settlement s = Ownership.settlement(owner.settlementId(), level.getServer());
				if (s != null) {
					CrimeDesk.commit(level, player, s, Offence.THEFT, animal.blockPosition(), owner.resident(), animalValue(), 0.2, null);
				}
			}
		}
		return InteractionResult.PASS;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		if (entity instanceof ServerPlayer victim && victim != attacker && !Law.config().pvpInTowns()) {
			// no fighting in the streets
			HearthWorld world = HearthWorld.of(level);
			if (world != null && (world.settlementAt(victim.blockPosition()) != null || world.settlementAt(attacker.blockPosition()) != null)) {
				return false;
			}
		}
		if (isFolk(entity) && !Law.exempt(attacker)) {
			FolkHandle folk = handle(level, entity);
			if (folk != null) {
				Victim before = VICTIMS.get(entity.getId());
				long last = before == null ? Long.MIN_VALUE / 2 : before.lastAssault;
				VICTIMS.put(entity.getId(), new Victim(folk.resident().id, folk.settlement().id(), attacker.getUUID(), last, level.getGameTime() + 400));
			}
		}
		return true;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || !(entity.level() instanceof ServerLevel level) || !entity.isAlive()) {
			return;
		}
		Victim v = VICTIMS.get(entity.getId());
		if (v == null || !v.attacker.equals(attacker.getUUID()) || level.getGameTime() - v.lastAssault < ASSAULT_TICKS) {
			return;
		}
		Settlement s = Ownership.settlement(v.settlementId, level.getServer());
		if (s == null) {
			return;
		}
		VICTIMS.put(entity.getId(), new Victim(v.resident, v.settlementId, v.attacker, level.getGameTime(), v.until));
		FolkHandle folk = handle(level, entity);
		Offence offence = isArresting(attacker, v.resident) ? Offence.RESISTING_ARREST : Offence.ASSAULT;
		CrimeDesk.commit(level, attacker, s, offence, entity.blockPosition(), v.resident, 0, 0.9, folk);
	}

	/** Hitting a guard who is arresting or chasing you is resisting arrest. */
	private static boolean isArresting(ServerPlayer player, UUID guard) {
		var talk = dev.voidmc.hearth.law.justice.Confrontations.of(player.getUUID());
		return talk != null && talk.folk.equals(guard);
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Victim v = VICTIMS.remove(entity.getId());
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		if (v != null && v.attacker.equals(killer.getUUID())) {
			Settlement s = Ownership.settlement(v.settlementId, level.getServer());
			if (s != null) {
				// a death is final: witnesses or not, the body is found
				CrimeDesk.commit(level, killer, s, Offence.MURDER, entity.blockPosition(), v.resident, 0, 1.0, null);
				// the dead tell no tales: what they were on their way to report dies with them
				CrimeDesk.silence(s, v.resident, killer.getUUID());
			}
			return;
		}
		if (entity instanceof Animal animal && Law.config().crimes().animals() && !Law.exempt(killer)) {
			Owner owner = Ownership.owner(animal);
			if (owner != null && !Ownership.isOwner(owner, killer.getUUID())) {
				Settlement s = Ownership.settlement(owner.settlementId(), level.getServer());
				if (s != null) {
					CrimeDesk.commit(level, killer, s, Offence.THEFT, animal.blockPosition(), owner.resident(), animalValue(), 0.5, null);
				}
			}
		}
	}

	/** What a penned animal is worth to its owners. */
	static long animalValue() {
		return 15;
	}

	static boolean isFolk(Entity entity) {
		return FOLK.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}

	/** The npc module's handle on a folk entity, or null. */
	public static @Nullable FolkHandle handle(ServerLevel level, Entity entity) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return null;
		}
		for (FolkHandle f : npc.folkNear(level, entity.position(), 2.0)) {
			if (f.entity() == entity) {
				return f;
			}
		}
		return null;
	}

	/** Forgets victims whose attackers are long gone. Once a second. */
	public static void sweep(long now) {
		VICTIMS.values().removeIf(v -> v.until < now);
	}

	public static void clear() {
		VICTIMS.clear();
		DROPS.clear();
	}
}
