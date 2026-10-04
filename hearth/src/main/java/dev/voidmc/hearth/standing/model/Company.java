package dev.voidmc.hearth.standing.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * A company: a sworn band of players who share a fraction of each other's standing (docs/LIVING_WORLD.md §3). Immutable;
 * changes make a new value.
 *
 * @param founder the player who formed it (leads it while a member; the oldest member leads after)
 * @param members in the order they swore in, founder first
 * @param invited players a member has invited and who have not yet joined
 * @param formed  the sim day it was formed
 */
public record Company(String name, UUID founder, List<UUID> members, List<UUID> invited, long formed) {
	public static final int MAX_NAME = 32;
	public static final Codec<Company> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(Company::name),
		UUIDUtil.CODEC.fieldOf("founder").forGetter(Company::founder),
		UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(Company::members),
		UUIDUtil.CODEC.listOf().optionalFieldOf("invited", List.of()).forGetter(Company::invited),
		Codec.LONG.optionalFieldOf("formed", 0L).forGetter(Company::formed)
	).apply(i, Company::new));

	public Company {
		members = List.copyOf(members);
		invited = List.copyOf(invited);
	}

	/** Names compare without case or surrounding space. */
	public static String key(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	/** Letters, digits, spaces, apostrophes and hyphens, 2–32 characters. */
	public static boolean validName(String name) {
		String n = name.trim();
		return n.length() >= 2 && n.length() <= MAX_NAME && n.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '\'' || c == '-');
	}

	public UUID leader() {
		return this.members.contains(this.founder) || this.members.isEmpty() ? this.founder : this.members.getFirst();
	}

	public Company with(UUID member) {
		List<UUID> m = new ArrayList<>(this.members);
		if (!m.contains(member)) {
			m.add(member);
		}
		List<UUID> inv = new ArrayList<>(this.invited);
		inv.remove(member);
		return new Company(this.name, this.founder, m, inv, this.formed);
	}

	public Company without(UUID member) {
		List<UUID> m = new ArrayList<>(this.members);
		m.remove(member);
		return new Company(this.name, this.founder, m, this.invited, this.formed);
	}

	public Company invite(UUID player) {
		List<UUID> inv = new ArrayList<>(this.invited);
		if (!inv.contains(player)) {
			inv.add(player);
		}
		// An invitation list is a courtesy, not a ledger: keep the newest few.
		while (inv.size() > 16) {
			inv.removeFirst();
		}
		return new Company(this.name, this.founder, this.members, inv, this.formed);
	}
}
