package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.look.SkinCanvas.Part;

/**
 * What the people of the roads and the new trades wear (docs/LIVING_WORLD_2.md §1.3, the ten trades lane 0 added): a
 * pilgrim's undyed habit with a scallop shell, a bandit's hood and the scarf over his face, a messenger's riding coat
 * with the town's badge and his satchel, a carter's linen smock grey with the road's dust, a pedlar's pack straps and
 * bright neckerchief, a tollkeeper's and a bailiff's official coats, a servant's livery and apron, a hermit's rags and a
 * monk's brown habit and cord. Drawn by the skin composer through {@link Brush} (its own garment painters), in the
 * culture's dyes like everyone else's clothes.
 */
final class WayfarerDress {
	private WayfarerDress() {
	}

	/** The composer's garment painters and canvas, as the dress needs them. */
	interface Brush {
		SkinCanvas canvas();

		int skin();

		/** One of the options, by the person's own seed. */
		int pick(int... options);

		/** A colour with the cloth's weave in it at a pixel. */
		int weave(int colour, int x, int y);

		int[] accents();

		int[] darks();

		void vest(int colour, int trim, boolean closed);

		void coat(int colour, int trim, int length, boolean buttons);

		void robe(int colour, int stole);

		void apron(int colour, boolean full, boolean straps);

		void belt(int leather, boolean tools);

		void dust(int colour, float threshold, boolean legsOnly);
	}

	static boolean dresses(JobType job) {
		return switch (job) {
			case BANDIT, PILGRIM, MESSENGER, CARTER, PEDLAR, TOLLKEEPER, SERVANT, HERMIT, MONK, BAILIFF -> true;
			default -> false;
		};
	}

	static void paint(JobType job, Brush b) {
		SkinCanvas c = b.canvas();
		Part j = SkinCanvas.JACKET;
		switch (job) {
			case PILGRIM -> {
				b.robe(b.pick(0x8A7E6A, 0x7A6E5E, 0x9A8C74), 0x6A5E4E);
				cord(c, 0xD8CCA8);
				// the scallop shell on the breast
				c.front(j, 2, 2, SkinComposer.argb(0xF4EEE0));
				c.front(j, 3, 2, SkinComposer.argb(0xE8DFC8));
				c.front(j, 2, 3, SkinComposer.argb(0xE8DFC8));
				c.front(j, 3, 3, SkinComposer.argb(0xF4EEE0));
				b.dust(0x8A7A60, 0.8F, true);
			}
			case MONK -> {
				int habit = b.pick(0x5A4232, 0x4A3A2E, 0x6A5040);
				b.robe(habit, SkinComposer.shade(habit, 0.8F));
				cord(c, 0xE0D8C0);
				hood(c, b, habit, false);
			}
			case HERMIT -> {
				int rags = b.pick(0x7A7262, 0x6A6456, 0x84786A);
				b.robe(rags, SkinComposer.shade(rags, 0.85F));
				cord(c, 0x9A8A6A);
				b.dust(0x6A5E4A, 0.7F, false);
				// patches
				c.front(SkinCanvas.BODY, 1, 7, SkinComposer.argb(SkinComposer.shade(rags, 1.2F)));
				c.front(SkinCanvas.BODY, 6, 4, SkinComposer.argb(SkinComposer.shade(rags, 0.7F)));
			}
			case BANDIT -> {
				int leather = b.pick(0x4A3626, 0x3E3024, 0x55402C);
				b.vest(leather, 0x2A2018, true);
				b.belt(0x2A2018, false);
				int cloth = b.pick(0x3A3A36, 0x4A3E34, 0x34403A);
				hood(c, b, cloth, true);
				// the scarf over the mouth and nose
				for (int x = 0; x < 8; x++) {
					for (int y = 5; y < 8; y++) {
						c.front(SkinCanvas.HAT, x, y, SkinComposer.argb(b.weave(cloth, x, y + 40)));
					}
				}
				b.dust(0x5A4A36, 0.75F, false);
			}
			case MESSENGER -> {
				int coat = b.darks()[0];
				b.coat(coat, b.accents()[0], 8, true);
				// the badge of the town that sent him
				c.front(j, 2, 3, SkinComposer.argb(b.accents()[0]));
				c.front(j, 3, 3, SkinComposer.argb(0xE8D070));
				c.front(j, 2, 4, SkinComposer.argb(0xE8D070));
				c.front(j, 3, 4, SkinComposer.argb(b.accents()[0]));
				strap(c, 0x5A3E24, true);
			}
			case CARTER -> {
				int smock = b.pick(0xC8BC9E, 0xBDB296, 0xD0C6AA);
				b.coat(smock, SkinComposer.shade(smock, 0.85F), 7, false);
				b.belt(0x5A4030, false);
				b.dust(0x8A7458, 0.72F, false);
			}
			case PEDLAR -> {
				b.vest(b.pick(0x6A5A44, 0x5E5040, 0x725E46), 0xC8B070, false);
				// a bright neckerchief
				int scarf = b.accents()[b.accents().length > 1 ? 1 : 0];
				for (int x = 0; x < 8; x++) {
					c.front(j, x, 0, SkinComposer.argb(scarf));
				}
				c.front(j, 3, 1, SkinComposer.argb(scarf));
				c.front(j, 4, 1, SkinComposer.argb(scarf));
				strap(c, 0x6A4A2E, false);
				strap(c, 0x6A4A2E, true);
				b.belt(0x5A4030, true);
				b.dust(0x8A7458, 0.85F, true);
			}
			case TOLLKEEPER, BAILIFF -> {
				int coat = job == JobType.BAILIFF ? b.pick(0x3A3A44, 0x2E3440) : b.pick(0x3E4A36, 0x4A3A30);
				b.coat(coat, 0xC8B070, 9, true);
				b.belt(0x2A2018, false);
				// the badge of office
				c.front(j, 5, 3, SkinComposer.argb(0xD8B840));
			}
			case SERVANT -> {
				int livery = b.accents()[0];
				b.vest(livery, 0xC8B070, true);
				b.apron(0xF0ECE2, false, false);
			}
			default -> {
			}
		}
	}

	/** A cord or rope belt round the waist. */
	private static void cord(SkinCanvas c, int colour) {
		Part body = SkinCanvas.JACKET;
		for (int s = 0; s < body.around(); s++) {
			c.around(body, s, 7, SkinComposer.argb(colour));
		}
		c.front(body, 5, 8, SkinComposer.argb(colour));
		c.front(body, 5, 9, SkinComposer.argb(colour));
	}

	/** A hood: over the crown and down the back of the head and the sides of the face. */
	private static void hood(SkinCanvas c, Brush b, int colour, boolean low) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				c.top(hat, x, z, SkinComposer.argb(b.weave(colour, x, z + 70)));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			SkinCanvas.Face side = SkinCanvas.sideOf(hat, s);
			int depth = side == SkinCanvas.Face.BACK ? 8 : side == SkinCanvas.Face.FRONT ? (low ? 2 : 1) : 7;
			for (int y = 0; y < depth; y++) {
				c.around(hat, s, y, SkinComposer.argb(b.weave(colour, s, y + 80)));
			}
		}
		// the opening frames the face
		c.front(hat, 0, 2, SkinComposer.argb(SkinComposer.shade(colour, 0.8F)));
		c.front(hat, 7, 2, SkinComposer.argb(SkinComposer.shade(colour, 0.8F)));
	}

	/** A strap across the chest from a shoulder to the other hip (a satchel's, a pack's). */
	private static void strap(SkinCanvas c, int leather, boolean right) {
		Part j = SkinCanvas.JACKET;
		for (int y = 0; y < 9; y++) {
			int x = right ? 1 + y * 6 / 9 : 6 - y * 6 / 9;
			c.front(j, x, y, SkinComposer.argb(leather));
			c.back(j, 7 - x, y, SkinComposer.argb(leather));
		}
	}
}
