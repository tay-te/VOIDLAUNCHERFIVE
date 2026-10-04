package dev.voidmc.hearth.standing.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What one kind of deed does (data: {@code data/<ns>/hearth_standing/deeds/*.json}). Every factor multiplies the deed's
 * signed weight as reported ({@code StandingService.Deed.weight}).
 *
 * @param settlement how much it moves the town's standing
 * @param victim     how much it moves the opinion of the resident most affected (the victim, the quest giver)
 * @param witness    how much it moves the opinion of each folk who saw it
 * @param family     how much it moves the opinion of the affected resident's spouse, children and parents
 * @param gossip     how much of it travels to other towns as news (0: it never does)
 * @param fame       how much it makes the player known around the place
 * @param violent    it adds to the player's reputation for violence (menace)
 * @param news       folk in the town talk about it
 * @param action     something it does besides standing: {@code wash} (a bath: the player is clean and freshly bathed)
 */
public record DeedRule(double settlement, double victim, double witness, double family, double gossip, double fame, boolean violent, boolean news,
	String action) {

	public static final DeedRule DEFAULT = new DeedRule(1.0, 1.0, 0.5, 0.0, 1.0, 1.0, false, true, "");
	/** For deeds that only adjust the town's standing (legacy calls, admin commands). */
	public static final DeedRule ADJUST = new DeedRule(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, false, "");

	public static final Codec<DeedRule> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.doubleRange(-10, 10).optionalFieldOf("settlement", DEFAULT.settlement).forGetter(DeedRule::settlement),
		Codec.doubleRange(-10, 10).optionalFieldOf("victim", DEFAULT.victim).forGetter(DeedRule::victim),
		Codec.doubleRange(-10, 10).optionalFieldOf("witness", DEFAULT.witness).forGetter(DeedRule::witness),
		Codec.doubleRange(-10, 10).optionalFieldOf("family", DEFAULT.family).forGetter(DeedRule::family),
		Codec.doubleRange(0, 10).optionalFieldOf("gossip", DEFAULT.gossip).forGetter(DeedRule::gossip),
		Codec.doubleRange(0, 10).optionalFieldOf("fame", DEFAULT.fame).forGetter(DeedRule::fame),
		Codec.BOOL.optionalFieldOf("violent", DEFAULT.violent).forGetter(DeedRule::violent),
		Codec.BOOL.optionalFieldOf("news", DEFAULT.news).forGetter(DeedRule::news),
		Codec.STRING.optionalFieldOf("action", DEFAULT.action).forGetter(DeedRule::action)
	).apply(i, DeedRule::new));

	public boolean washes() {
		return "wash".equals(this.action);
	}
}
