package dev.voidmc.hearth.law;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.item.StolenGoods;
import dev.voidmc.hearth.law.item.StolenMark;
import dev.voidmc.hearth.law.justice.Offences;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** {@link LawService}, as the other modules use it. Server thread only. */
public final class LawServiceImpl implements LawService {
	private static volatile @Nullable MinecraftServer server;

	static void setServer(@Nullable MinecraftServer s) {
		server = s;
	}

	@Override
	public @Nullable Owner owner(ServerLevel level, BlockPos pos) {
		return Ownership.owner(level, pos);
	}

	@Override
	public @Nullable Owner owner(Entity entity) {
		return Ownership.owner(entity);
	}

	@Override
	public boolean isStolen(ItemStack stack) {
		return stolenFrom(stack) != null;
	}

	@Override
	public @Nullable Owner stolenFrom(ItemStack stack) {
		StolenMark mark = StolenGoods.mark(stack, today(), Law.config().stealth().stolenFadeDays());
		return mark == null ? null : mark.owner();
	}

	@Override
	public boolean wanted(UUID player, long settlementId) {
		Warrant w = warrant(player, settlementId);
		return w != null && w.active();
	}

	@Override
	public long bounty(UUID player, long settlementId) {
		Warrant w = warrant(player, settlementId);
		return w == null ? 0 : w.bounty();
	}

	@Override
	public boolean outlaw(UUID player, long settlementId) {
		Warrant w = warrant(player, settlementId);
		return w != null && w.outlaw();
	}

	@Override
	public void report(Crime crime) {
		MinecraftServer s = server;
		if (s != null) {
			CrimeDesk.report(s, crime);
		}
	}

	/**
	 * The wanted posters a settlement's notice board shows (the quest module may draw them; docs/requests/p3-law.md):
	 * every player wanted there, worst first.
	 */
	public static List<Poster> posters(Settlement settlement) {
		List<Poster> out = new ArrayList<>();
		MinecraftServer s = server;
		for (Map.Entry<UUID, Warrant> e : Law.state(settlement).warrants().entrySet()) {
			Warrant w = e.getValue();
			if (!w.active()) {
				continue;
			}
			String name = e.getKey().toString();
			if (s != null) {
				var cached = s.services().nameToIdCache().get(e.getKey());
				if (cached.isPresent()) {
					name = cached.get().name();
				}
			}
			out.add(new Poster(e.getKey(), name, w.bounty(), w.worst(), w.outlaw(), w.origin()));
		}
		out.sort(Comparator.comparingLong(Poster::bounty).reversed().thenComparing(p -> -Offences.severity(p.worst())));
		return out;
	}

	/**
	 * A wanted poster.
	 *
	 * @param origin the town the crime was committed in, when word came from elsewhere (0: here)
	 */
	public record Poster(UUID player, String name, long bounty, Offence worst, boolean outlaw, long origin) {
	}

	private static @Nullable Warrant warrant(UUID player, long settlementId) {
		MinecraftServer s = server;
		HearthWorld world = s == null ? null : HearthWorld.of(s.overworld());
		Settlement settlement = world == null ? null : world.settlement(settlementId);
		return settlement == null ? null : Law.state(settlement).warrant(player);
	}

	private static long today() {
		MinecraftServer s = server;
		return s == null ? 0 : Law.day(s.overworld());
	}
}
