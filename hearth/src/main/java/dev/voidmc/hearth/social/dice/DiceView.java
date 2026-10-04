package dev.voidmc.hearth.social.dice;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything the dice screen shows, decided on the server: the table (dice, which are set aside, which are picked),
 * the scores, what can be done, what the folk says. The client draws it and sends clicks back; it never rolls.
 *
 * @param phase      {@link DiceGame.Phase} ordinal
 * @param stakes     the stakes the player may choose (STAKE phase); a single 0 means playing for honour
 * @param rollSerial changes with every roll, so the client tumbles the dice that were rolled
 * @param line       the folk's last words
 * @param status     what just happened ("Edda banks 450.")
 * @param loaded     0 = no loaded dice carried, 1 = carried and put away, 2 = in play
 */
public record DiceView(int phase, String folkName, String personality, long stake, int target, int playerScore, int folkScore, int turnPoints,
	int turn, int[] dice, int keptMask, int selectedMask, int scoringMask, int selectionScore, int rolledMask, int rollSerial, List<Integer> stakes,
	Component line, Component status, int winner, boolean cheatCaught, int loaded, long balance, boolean canRoll, boolean canBank) {

	public static final StreamCodec<RegistryFriendlyByteBuf, DiceView> STREAM_CODEC = StreamCodec.of(DiceView::write, DiceView::read);

	public DiceGame.Phase phaseEnum() {
		return DiceGame.Phase.values()[this.phase];
	}

	private static void write(RegistryFriendlyByteBuf buf, DiceView v) {
		buf.writeVarInt(v.phase);
		buf.writeUtf(v.folkName);
		buf.writeUtf(v.personality);
		buf.writeVarLong(v.stake);
		buf.writeVarInt(v.target);
		buf.writeVarInt(v.playerScore);
		buf.writeVarInt(v.folkScore);
		buf.writeVarInt(v.turnPoints);
		buf.writeVarInt(v.turn);
		for (int i = 0; i < Farkle.DICE; i++) {
			buf.writeByte(v.dice[i]);
		}
		buf.writeByte(v.keptMask);
		buf.writeByte(v.selectedMask);
		buf.writeByte(v.scoringMask);
		buf.writeVarInt(v.selectionScore + 1);
		buf.writeByte(v.rolledMask);
		buf.writeVarInt(v.rollSerial);
		buf.writeVarInt(v.stakes.size());
		for (int s : v.stakes) {
			buf.writeVarInt(s);
		}
		ComponentSerialization.STREAM_CODEC.encode(buf, v.line);
		ComponentSerialization.STREAM_CODEC.encode(buf, v.status);
		buf.writeVarInt(v.winner + 1);
		buf.writeBoolean(v.cheatCaught);
		buf.writeByte(v.loaded);
		buf.writeVarLong(v.balance);
		buf.writeBoolean(v.canRoll);
		buf.writeBoolean(v.canBank);
	}

	private static DiceView read(RegistryFriendlyByteBuf buf) {
		int phase = buf.readVarInt();
		String name = buf.readUtf();
		String personality = buf.readUtf();
		long stake = buf.readVarLong();
		int target = buf.readVarInt();
		int ps = buf.readVarInt();
		int fs = buf.readVarInt();
		int tp = buf.readVarInt();
		int turn = buf.readVarInt();
		int[] dice = new int[Farkle.DICE];
		for (int i = 0; i < Farkle.DICE; i++) {
			dice[i] = buf.readByte();
		}
		int kept = buf.readByte();
		int sel = buf.readByte();
		int scoring = buf.readByte();
		int selScore = buf.readVarInt() - 1;
		int rolled = buf.readByte();
		int serial = buf.readVarInt();
		int n = buf.readVarInt();
		List<Integer> stakes = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			stakes.add(buf.readVarInt());
		}
		Component line = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component status = ComponentSerialization.STREAM_CODEC.decode(buf);
		int winner = buf.readVarInt() - 1;
		boolean cheat = buf.readBoolean();
		int loaded = buf.readByte();
		long balance = buf.readVarLong();
		boolean canRoll = buf.readBoolean();
		boolean canBank = buf.readBoolean();
		return new DiceView(phase, name, personality, stake, target, ps, fs, tp, turn, dice, kept, sel, scoring, selScore, rolled, serial, stakes, line,
			status, winner, cheat, loaded, balance, canRoll, canBank);
	}
}
