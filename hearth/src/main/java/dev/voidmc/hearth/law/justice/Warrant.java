package dev.voidmc.hearth.law.justice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService.Offence;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * A player's account with one settlement's law: what they owe, for what, and how the guards will treat them. Lives in
 * the settlement's {@link dev.voidmc.hearth.law.state.LawState}; changed in place on the server thread.
 */
public final class Warrant {
	private static final Codec<Map<Offence, Integer>> COUNTS = Codec.unboundedMap(Offence.CODEC, Codec.INT)
		.xmap(m -> m.isEmpty() ? new EnumMap<>(Offence.class) : new EnumMap<>(m), m -> new LinkedHashMap<>(m));

	public static final Codec<Warrant> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("player").forGetter(w -> w.player),
		Codec.LONG.optionalFieldOf("bounty", 0L).forGetter(w -> w.bounty),
		COUNTS.optionalFieldOf("offences", new EnumMap<>(Offence.class)).forGetter(w -> w.offences),
		Codec.LONG.optionalFieldOf("since", 0L).forGetter(w -> w.sinceDay),
		Codec.LONG.optionalFieldOf("last", 0L).forGetter(w -> w.lastDay),
		Codec.BOOL.optionalFieldOf("murder", false).forGetter(w -> w.murder),
		Codec.BOOL.optionalFieldOf("outlaw", false).forGetter(w -> w.outlaw),
		Codec.BOOL.optionalFieldOf("spread", false).forGetter(w -> w.spread),
		Codec.LONG.optionalFieldOf("origin", 0L).forGetter(w -> w.origin),
		Codec.BOOL.optionalFieldOf("persuaded", false).forGetter(w -> w.persuadeTried),
		Codec.BOOL.optionalFieldOf("bribed", false).forGetter(w -> w.bribeTried),
		Codec.INT.optionalFieldOf("evaded", 0).forGetter(w -> w.evaded)
	).apply(i, (p, b, o, s, l, m, out, sp, or, per, bri, ev) -> {
		Warrant w = new Warrant(p, s);
		w.bounty = b;
		w.offences.putAll(o);
		w.lastDay = l;
		w.murder = m;
		w.outlaw = out;
		w.spread = sp;
		w.origin = or;
		w.persuadeTried = per;
		w.bribeTried = bri;
		w.evaded = ev;
		return w;
	}));

	public final UUID player;
	private long bounty;
	private final EnumMap<Offence, Integer> offences = new EnumMap<>(Offence.class);
	private long sinceDay;
	private long lastDay;
	private boolean murder;
	private boolean outlaw;
	/** Word of it has been sent to the neighbouring towns. */
	private boolean spread;
	/** The settlement it came from by word of mouth (0: committed here). */
	private long origin;
	private boolean persuadeTried;
	private boolean bribeTried;
	/** Times the player got away from the guards. */
	private int evaded;

	public Warrant(UUID player, long day) {
		this.player = player;
		this.sinceDay = day;
		this.lastDay = day;
	}

	/** Adds a crime's fine to the bounty. */
	public void charge(Offence offence, long fine, long day) {
		if (this.bounty <= 0) {
			this.sinceDay = day;
		}
		this.bounty += Math.max(0, fine);
		this.offences.merge(offence, 1, Integer::sum);
		this.lastDay = day;
		if (offence == Offence.MURDER) {
			this.murder = true;
		}
		// a fresh crime is fresh news for the neighbours, and a fresh chance to talk one's way out
		this.spread = false;
		this.persuadeTried = false;
		this.bribeTried = false;
	}

	/** Grows the bounty by a fraction (resisting, a failed bribe), at least {@code minimum} bits. */
	public void grow(double fraction, long minimum, long day) {
		this.bounty += Math.max(minimum, Math.round(this.bounty * fraction));
		this.lastDay = day;
	}

	/** Reduces what is owed (a fine paid in part, a persuasive word); clears the warrant when nothing is left. */
	public void reduce(long bits, long day) {
		this.bounty = Math.max(0, this.bounty - Math.max(0, bits));
		this.lastDay = day;
		if (this.bounty == 0) {
			clear();
		}
	}

	/** Settled: paid, served or pardoned. */
	public void clear() {
		this.bounty = 0;
		this.offences.clear();
		this.murder = false;
		this.outlaw = false;
		this.persuadeTried = false;
		this.bribeTried = false;
		this.evaded = 0;
	}

	/** Whether there is anything to answer for. */
	public boolean active() {
		return this.bounty > 0 || this.outlaw || this.murder;
	}

	/** The gravest offence on the warrant, or trespass if there is none. */
	public Offence worst() {
		Offence worst = Offence.TRESPASS;
		for (Offence o : this.offences.keySet()) {
			if (Offences.severity(o) > Offences.severity(worst)) {
				worst = o;
			}
		}
		return worst;
	}

	public long bounty() {
		return this.bounty;
	}

	public Map<Offence, Integer> offences() {
		return this.offences;
	}

	public long sinceDay() {
		return this.sinceDay;
	}

	public long lastDay() {
		return this.lastDay;
	}

	public boolean murder() {
		return this.murder;
	}

	public boolean outlaw() {
		return this.outlaw;
	}

	public void setOutlaw(boolean outlaw) {
		this.outlaw = outlaw;
	}

	public boolean spread() {
		return this.spread;
	}

	public void setSpread(boolean spread) {
		this.spread = spread;
	}

	public long origin() {
		return this.origin;
	}

	public void setOrigin(long origin) {
		this.origin = origin;
	}

	public boolean persuadeTried() {
		return this.persuadeTried;
	}

	public void setPersuadeTried(boolean tried) {
		this.persuadeTried = tried;
	}

	public boolean bribeTried() {
		return this.bribeTried;
	}

	public void setBribeTried(boolean tried) {
		this.bribeTried = tried;
	}

	public int evaded() {
		return this.evaded;
	}

	public void addEvaded() {
		this.evaded++;
	}

	/** Takes on word of a warrant from another town: a share of its bounty and its gravest marks. */
	public void absorb(Warrant from, long share, long day) {
		if (this.bounty <= 0) {
			this.sinceDay = day;
		}
		this.bounty += Math.max(0, share);
		from.offences.forEach((o, n) -> this.offences.merge(o, n, Math::max));
		this.murder |= from.murder;
		this.outlaw |= from.outlaw;
		this.lastDay = day;
	}
}
