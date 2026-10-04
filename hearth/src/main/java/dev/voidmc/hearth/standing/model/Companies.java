package dev.voidmc.hearth.standing.model;

import dev.voidmc.hearth.standing.StandingConfig;
import java.util.List;
import java.util.UUID;

/**
 * Forming, joining and leaving companies, as rules over {@link RegionData} (the commands call these and report the
 * result). A player belongs to at most one company; a company with no members is disbanded.
 */
public final class Companies {
	private Companies() {
	}

	/** What happened, as a key under {@code hearth.standing.company.} (and whether it worked). */
	public enum Result {
		FORMED(true), JOINED(true), LEFT(true), DISBANDED(true), INVITED(true),
		DISABLED(false), BAD_NAME(false), NAME_TAKEN(false), ALREADY_MEMBER(false), NOT_MEMBER(false), NO_SUCH_COMPANY(false), NOT_INVITED(false),
		FULL(false), ALREADY_IN_THIS(false);

		public final boolean ok;

		Result(boolean ok) {
			this.ok = ok;
		}

		public String key() {
			return "hearth.standing.company." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static Result form(RegionData r, UUID founder, String name, long day, StandingConfig.Companies cfg) {
		if (!cfg.enabled()) {
			return Result.DISABLED;
		}
		if (!Company.validName(name)) {
			return Result.BAD_NAME;
		}
		if (r.companyOf(founder) != null) {
			return Result.ALREADY_MEMBER;
		}
		if (r.company(name) != null) {
			return Result.NAME_TAKEN;
		}
		r.putCompany(new Company(name.trim(), founder, List.of(founder), List.of(), day));
		return Result.FORMED;
	}

	public static Result invite(RegionData r, UUID member, UUID guest, StandingConfig.Companies cfg) {
		if (!cfg.enabled()) {
			return Result.DISABLED;
		}
		Company c = r.companyOf(member);
		if (c == null) {
			return Result.NOT_MEMBER;
		}
		if (c.members().contains(guest)) {
			return Result.ALREADY_IN_THIS;
		}
		r.putCompany(c.invite(guest));
		return Result.INVITED;
	}

	public static Result join(RegionData r, UUID player, String name, StandingConfig.Companies cfg) {
		if (!cfg.enabled()) {
			return Result.DISABLED;
		}
		Company c = r.company(name);
		if (c == null) {
			return Result.NO_SUCH_COMPANY;
		}
		if (c.members().contains(player)) {
			return Result.ALREADY_IN_THIS;
		}
		if (r.companyOf(player) != null) {
			return Result.ALREADY_MEMBER;
		}
		if (cfg.inviteOnly() && !c.invited().contains(player)) {
			return Result.NOT_INVITED;
		}
		if (c.members().size() >= cfg.maxMembers()) {
			return Result.FULL;
		}
		r.putCompany(c.with(player));
		return Result.JOINED;
	}

	public static Result leave(RegionData r, UUID player) {
		Company c = r.companyOf(player);
		if (c == null) {
			return Result.NOT_MEMBER;
		}
		Company left = c.without(player);
		if (left.members().isEmpty()) {
			r.removeCompany(c.name());
			return Result.DISBANDED;
		}
		r.putCompany(left);
		return Result.LEFT;
	}
}
