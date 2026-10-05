package dev.voidmc.hearth.quest.service;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The quest module's view of the law (docs/LIVING_WORLD.md §4), through the law service when it is present: who is
 * wanted, what bounty they carry, and crimes committed inside quests (robbing a caravan) that the law cannot see for
 * itself. Without the law module nobody is wanted and nothing is reported.
 */
public final class LawBridge {
	/** How far folk see a crime done in a quest. */
	private static final double WITNESS_RANGE = 24;

	private LawBridge() {
	}

	/**
	 * Marks what a robbed caravan's animal dropped as stolen from its town (requests/p3-quest.md §2), so that town's
	 * merchants refuse it and a fence takes it. The drops are the fresh item entities round the body.
	 */
	public static void markLoot(ServerLevel level, net.minecraft.world.entity.Entity victim, Settlement s, ServerPlayer thief) {
		LawService law = HearthServices.law();
		if (law == null) {
			return;
		}
		LawService.Owner owner = new LawService.Owner(s.id(), null, null, LawService.Owner.Kind.SETTLEMENT);
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			victim.getBoundingBox().inflate(2.5), e -> e.tickCount < 5)) {
			net.minecraft.world.item.ItemStack stack = item.getItem().copy();
			law.markStolen(stack, owner, thief.getUUID());
			item.setItem(stack);
		}
	}

	/** Whether the law wants the player in the settlement (a crime unpaid for, or an outlaw of its region). */
	public static boolean wanted(UUID player, Settlement s) {
		LawService law = HearthServices.law();
		return law != null && (law.wanted(player, s.id()) || law.outlaw(player, s.id()));
	}

	public static boolean outlaw(UUID player, Settlement s) {
		LawService law = HearthServices.law();
		return law != null && law.outlaw(player, s.id());
	}

	/** Fines and bounty the player owes the settlement, in bits. */
	public static long bounty(UUID player, Settlement s) {
		LawService law = HearthServices.law();
		return law == null ? 0 : law.bounty(player, s.id());
	}

	/**
	 * Reports a crime done in a quest. It counts as witnessed when an embodied folk near can see the player, or when
	 * {@code seen} (a drover of the robbed caravan lived to tell); otherwise it is a suspicion for the law to prove.
	 *
	 * @return whether it was reported (false without the law module)
	 */
	public static boolean report(LawService.Offence offence, ServerPlayer player, Settlement s, BlockPos pos, @Nullable UUID victim, long value,
		boolean seen) {
		LawService law = HearthServices.law();
		if (law == null || player.isCreative() || player.isSpectator()) {
			return false;
		}
		boolean witnessed = seen || witnessed(player.level(), player, pos);
		law.report(new LawService.Crime(offence, player.getUUID(), s.id(), pos, victim, Math.max(0, value), witnessed,
			player.level().getGameTime()));
		return true;
	}

	private static boolean witnessed(ServerLevel level, ServerPlayer player, BlockPos pos) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return false;
		}
		for (NpcService.FolkHandle folk : npc.folkNear(level, Vec3.atCenterOf(pos), WITNESS_RANGE)) {
			if (folk.awake() && folk.canSee(player)) {
				return true;
			}
		}
		return false;
	}

	/** An offence by its name, or null. */
	public static LawService.@Nullable Offence offence(String name) {
		for (LawService.Offence o : LawService.Offence.values()) {
			if (o.getSerializedName().equals(name)) {
				return o;
			}
		}
		return null;
	}
}
