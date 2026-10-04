package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A player's deeded house is mended only if its owner pays (docs/LIVING_WORLD.md §2): damage on a plot a player owns
 * waits in the queue until the owner settles the bill — the goods at market price times {@code markup}, plus a labour
 * fee per block — paid into the settlement's treasury. Then the builders mend it like any other.
 */
public final class OwnerRepairs {
	private OwnerRepairs() {
	}

	/** What mending a player's property in a settlement would cost now. */
	public record Quote(int blocks, long bits, List<Long> positions) {
		public boolean empty() {
			return this.blocks == 0;
		}
	}

	public static Quote quote(RepairRuntime rt, HearthWorld world, Settlement s, UUID owner) {
		RepairConfig.Owners cfg = RepairModule.config().owners();
		RepairQueue q = s.data(RepairModule.QUEUE);
		PlotLocator.Source source = PlotLocator.services(world);
		int blocks = 0;
		double bits = 0;
		List<Long> positions = new ArrayList<>();
		for (Damage d : q.all()) {
			if (!d.awaitingPayment()) {
				continue;
			}
			PlotView plot = s.plot(d.plot);
			if (plot == null || !owner.equals(plot.owner())) {
				continue;
			}
			BlockState expected = Restorer.expected(rt, s, source, d);
			if (expected == null) {
				continue;
			}
			blocks++;
			bits += RepairBill.worth(s, expected) * cfg.markup() + cfg.labourFee();
			positions.add(d.pos);
		}
		return new Quote(blocks, Math.round(Math.ceil(bits)), positions);
	}

	/** Charges the owner and releases their damage to the builders. Returns false if they cannot pay (or owe nothing). */
	public static boolean pay(RepairRuntime rt, HearthWorld world, Settlement s, ServerPlayer player) {
		Quote quote = quote(rt, world, s, player.getUUID());
		if (quote.empty()) {
			return false;
		}
		EconomyService economy = HearthServices.economy();
		if (economy != null) {
			if (!economy.charge(player, quote.bits())) {
				return false;
			}
			s.deposit(quote.bits());
		}
		RepairQueue q = s.data(RepairModule.QUEUE);
		for (long pos : quote.positions()) {
			Damage d = q.get(pos);
			if (d != null) {
				d.set(Damage.PAID, true);
			}
		}
		s.markDirty();
		return true;
	}
}
