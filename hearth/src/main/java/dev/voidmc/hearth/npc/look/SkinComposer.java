package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.look.Palette.Dyes;
import dev.voidmc.hearth.npc.look.Palette.Headwear;
import dev.voidmc.hearth.npc.look.Palette.Pattern;
import dev.voidmc.hearth.npc.look.Palette.Sleeves;
import dev.voidmc.hearth.npc.look.SkinCanvas.Face;
import dev.voidmc.hearth.npc.look.SkinCanvas.Part;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.Office;

/**
 * Draws a folk's skin, pixel by pixel, on the 64×64 player layout (both layers): skin tone, a face, hair in one of
 * many styles, facial hair, clothes cut and dyed after their culture, the marks of their trade, their station in life (the
 * poor in faded, patched clothes; merchants with buttons and trim; the gentry in deep colours and fine coats; the lord
 * in royal colours with an ermine-trimmed mantle and a circlet), any office they hold, and the years on them.
 *
 * <p>A pure function of {@link FolkLook}: no randomness but a hash of the look, no game classes, so the same folk
 * looks the same on every client and the composition is tested without a GPU. The client wraps the pixels in a
 * texture; nothing here needs assets.
 *
 * <p>Drawing goes base layer first (skin, face, hair, clothes), then the outer layer (hair volume, overgarments,
 * aprons, armour, hats). Shading is light: the game already shades faces by direction, so the texture only carries
 * folds, seams and the weave of cloth.
 */
public final class SkinComposer {
	private static final int OPAQUE = 0xFF000000;

	private final FolkLook look;
	private final SkinCanvas c = new SkinCanvas();
	private final Rng rng;
	private final long noiseSeed;
	private final boolean slim;
	private final Part rArm;
	private final Part lArm;
	private final Part rSleeve;
	private final Part lSleeve;
	private final Dyes dyes;

	// Resolved features.
	private final int skin;
	private final int hair;
	private final int eye;
	private final Hair style;
	private final int facial;

	private SkinComposer(FolkLook look) {
		this.look = look;
		this.rng = new Rng(look.baseKey() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L);
		this.noiseSeed = this.rng.nextLong();
		this.slim = look.slim();
		this.rArm = SkinCanvas.rightArm(this.slim);
		this.lArm = SkinCanvas.leftArm(this.slim);
		this.rSleeve = SkinCanvas.rightSleeve(this.slim);
		this.lSleeve = SkinCanvas.leftSleeve(this.slim);
		this.dyes = Palette.dyes(look.culture());
		int g = look.appearance();
		this.skin = Palette.SKIN[Genome.skinTone(g)];
		this.eye = Palette.EYES[Genome.eyeColour(g)];
		int baseHair = Palette.HAIR[Genome.hairColour(g)];
		if (look.age() == AgeStage.ELDER) {
			// Greying follows the detail seed: some elders are salted, some silver, some white.
			int grey = (Genome.detail(g) & 3);
			baseHair = switch (grey) {
				case 0 -> mix(baseHair, Palette.GREY_HAIR, 0.55F);
				case 1 -> mix(baseHair, Palette.GREY_HAIR, 0.8F);
				case 2 -> Palette.GREY_HAIR;
				default -> Palette.WHITE_HAIR;
			};
		} else if (look.age() == AgeStage.CHILD) {
			// Children's hair is a shade lighter than it will be.
			baseHair = mix(baseHair, 0xD8B880, 0.12F);
		}
		this.hair = baseHair;
		this.style = Hair.choose(look, Genome.hairStyle(g), Genome.detail(g));
		this.facial = facialHair(look, Genome.facialHair(g));
	}

	/** The skin of a folk with this look: 64×64 ARGB pixels, row-major. */
	public static int[] compose(FolkLook look) {
		SkinComposer composer = new SkinComposer(look);
		composer.paint();
		return composer.c.px;
	}

	private void paint() {
		paintSkin();
		paintFace();
		paintHair();
		Outfit outfit = Outfit.choose(this.look, this.dyes, this.rng);
		dressForStation(outfit);
		paintClothes(outfit);
		if (this.look.station() == Station.POOR && this.look.age() != AgeStage.CHILD) {
			patches(outfit);
		}
		paintJob(outfit);
		paintStation(outfit);
		paintHeadwear(outfit);
		paintAge(outfit);
	}

	// ---------------------------------------------------------------- skin and face

	private void paintSkin() {
		int sk = this.skin;
		for (Part p : new Part[]{SkinCanvas.HEAD, SkinCanvas.BODY, this.rArm, this.lArm, SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
			this.c.fill(p, (a, b, face) -> argb(jitter(face == Face.BOTTOM ? shade(sk, 0.86F) : sk, p.u() + a, p.v() + b, 0.015F)));
		}
		// Ears, a little shadow under the jaw.
		Part h = SkinCanvas.HEAD;
		for (int y = 4; y <= 5; y++) {
			this.c.right(h, 4, y, argb(shade(sk, 0.9F)));
			this.c.left(h, 3, y, argb(shade(sk, 0.9F)));
		}
		this.c.right(h, 3, 4, argb(shade(sk, 0.82F)));
		this.c.left(h, 4, 4, argb(shade(sk, 0.82F)));
		for (int x = 0; x < 8; x++) {
			this.c.right(h, x, 7, argb(shade(sk, 0.94F)));
			this.c.left(h, x, 7, argb(shade(sk, 0.94F)));
		}
		// Hands a touch warmer than arms; knuckles.
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				this.c.around(arm, s, 11, argb(shade(sk, 0.93F)));
			}
		}
	}

	private void paintFace() {
		Part h = SkinCanvas.HEAD;
		int sk = this.skin;
		boolean child = this.look.age() == AgeStage.CHILD;
		boolean fem = this.look.female();
		int white = mix(0xF4F2EC, sk, 0.08F);
		int brow = browColour();
		// Eyes on row 4: whites outside, irises inside, a darker top pixel for lids on the iris side.
		this.c.front(h, 1, 4, argb(white));
		this.c.front(h, 2, 4, argb(this.eye));
		this.c.front(h, 5, 4, argb(this.eye));
		this.c.front(h, 6, 4, argb(white));
		if (child) {
			// Bigger, rounder eyes: the iris reaches down a row.
			this.c.front(h, 2, 5, argb(shade(this.eye, 0.8F)));
			this.c.front(h, 5, 5, argb(shade(this.eye, 0.8F)));
		}
		// Brows on row 3.
		if (fem || child) {
			this.c.front(h, 1, 3, argb(mix(brow, sk, 0.45F)));
			this.c.front(h, 2, 3, argb(mix(brow, sk, 0.2F)));
			this.c.front(h, 5, 3, argb(mix(brow, sk, 0.2F)));
			this.c.front(h, 6, 3, argb(mix(brow, sk, 0.45F)));
		} else {
			for (int x : new int[]{1, 2, 5, 6}) {
				this.c.front(h, x, 3, argb(brow));
			}
		}
		// Nose: a soft shadow; mouth below it.
		this.c.front(h, 3, 5, argb(shade(sk, 0.93F)));
		this.c.front(h, 4, 5, argb(shade(sk, 0.86F)));
		int lip = fem ? mix(shade(sk, 0.78F), 0xB04A4A, 0.32F) : mix(shade(sk, 0.74F), 0x8A4A40, 0.18F);
		if (child) {
			lip = mix(shade(sk, 0.85F), 0xC06060, 0.25F);
		}
		this.c.front(h, 3, 6, argb(lip));
		this.c.front(h, 4, 6, argb(lip));
		// Cheeks.
		if (fem || child) {
			int blush = mix(sk, 0xE07868, 0.16F);
			this.c.front(h, 1, 5, argb(blush));
			this.c.front(h, 6, 5, argb(blush));
		}
		// Freckles on fair or red-haired folk, by the detail seed.
		int detail = Genome.detail(this.look.appearance());
		boolean redhead = Genome.hairColour(this.look.appearance()) == 6 || Genome.hairColour(this.look.appearance()) == 11;
		if ((Genome.skinTone(this.look.appearance()) <= 2 && (detail & 12) == 12) || (redhead && (detail & 4) != 0)) {
			int freckle = shade(sk, 0.84F);
			this.c.front(h, 1, 5, argb(freckle));
			this.c.front(h, 6, 5, argb(freckle));
			this.c.front(h, 2, 6, argb(mix(sk, freckle, 0.6F)));
			this.c.front(h, 5, 6, argb(mix(sk, freckle, 0.6F)));
		}
		// Chin shadow.
		for (int x = 2; x <= 5; x++) {
			this.c.front(h, x, 7, argb(shade(sk, 0.96F)));
		}
		paintFacialHair();
	}

	private int browColour() {
		int b = shade(this.hair, 0.8F);
		// Fair and grey hair still has darker brows.
		if (luma(this.hair) > 0.55F) {
			b = mix(this.hair, 0x6A5038, 0.5F);
		}
		return b;
	}

	private static int facialHair(FolkLook look, int gene) {
		if (look.female() || look.age() == AgeStage.CHILD) {
			return 0;
		}
		int beardy = switch (look.culture()) {
			case NORTHERN, TUNDRA, STEPPE, DESERT, ALPINE, MOORLAND -> 1;
			case WISTERIA, KARST, TROPICAL -> -2;
			default -> 0;
		};
		if (look.age() == AgeStage.ELDER) {
			beardy += 1;
		}
		// 0 none, 1 stubble, 2 moustache, 3 short beard, 4 full beard.
		int v = gene + beardy;
		if (v <= 3) {
			return 0;
		}
		return switch (v) {
			case 4 -> 1;
			case 5 -> 2;
			case 6 -> 3;
			default -> 4;
		};
	}

	private void paintFacialHair() {
		if (this.facial == 0) {
			return;
		}
		Part h = SkinCanvas.HEAD;
		int hr = this.hair;
		int dark = shade(hr, 0.82F);
		int light = mix(hr, 0xFFFFFF, 0.08F);
		switch (this.facial) {
			case 1 -> {
				int stub = mix(this.skin, shade(hr, 0.7F), 0.32F);
				for (int x = 0; x < 8; x++) {
					this.c.front(h, x, 7, argb(stub));
				}
				for (int x : new int[]{0, 1, 2, 5, 6, 7}) {
					this.c.front(h, x, 6, argb(stub));
				}
				this.c.front(h, 0, 5, argb(stub));
				this.c.front(h, 7, 5, argb(stub));
				sideBeard(stub, 6);
			}
			case 2 -> {
				for (int x = 2; x <= 5; x++) {
					this.c.front(h, x, 5, argb(x == 2 || x == 5 ? dark : hr));
				}
				this.c.front(h, 2, 6, argb(dark));
				this.c.front(h, 5, 6, argb(dark));
			}
			case 3, 4 -> {
				boolean full = this.facial == 4;
				for (int x = 2; x <= 5; x++) {
					this.c.front(h, x, 5, argb(x == 3 ? light : hr));
				}
				for (int x : new int[]{0, 1, 2, 5, 6, 7}) {
					this.c.front(h, x, 6, argb(noisy(hr, x, 6)));
				}
				for (int x = 0; x < 8; x++) {
					this.c.front(h, x, 7, argb(noisy(x == 3 || x == 4 ? light : hr, x, 7)));
				}
				// Keep the mouth readable: a dark line through the beard.
				this.c.front(h, 3, 6, argb(shade(hr, 0.55F)));
				this.c.front(h, 4, 6, argb(shade(hr, 0.55F)));
				if (full) {
					this.c.front(h, 0, 5, argb(hr));
					this.c.front(h, 7, 5, argb(hr));
					this.c.front(h, 0, 4, argb(dark));
					this.c.front(h, 7, 4, argb(dark));
					// A full beard hangs below the chin onto the hat layer's front.
					for (int x = 1; x <= 6; x++) {
						this.c.front(SkinCanvas.HAT, x, 7, argb(noisy(x % 2 == 0 ? hr : dark, x, 15)));
					}
				}
				sideBeard(hr, full ? 4 : 6);
				// Under the chin.
				for (int x = 0; x < 8; x++) {
					for (int z = 4; z < 8; z++) {
						this.c.bottom(h, x, z, argb(noisy(dark, x, z + 20)));
					}
				}
			}
			default -> {
			}
		}
	}

	private void sideBeard(int colour, int fromRow) {
		Part h = SkinCanvas.HEAD;
		for (int y = fromRow; y < 8; y++) {
			for (int z = 5; z < 8; z++) {
				this.c.right(h, z, y, argb(noisy(colour, z, y + 30)));
				this.c.left(h, 7 - z, y, argb(noisy(colour, z + 8, y + 30)));
			}
		}
	}

	// ---------------------------------------------------------------- hair

	private void paintHair() {
		Part h = SkinCanvas.HEAD;
		Part hat = SkinCanvas.HAT;
		Hair st = this.style;
		if (st == Hair.BALD) {
			// A fringe of hair round the back and sides above the ears.
			for (int z = 0; z < 8; z++) {
				for (int y = 2; y <= 4; y++) {
					if (z < 4 || y == 2) {
						this.c.right(h, z, y, hairPx(z, y));
						this.c.left(h, 7 - z, y, hairPx(z + 8, y));
					}
				}
			}
			for (int x = 0; x < 8; x++) {
				for (int y = 2; y <= 5; y++) {
					this.c.back(h, x, y, hairPx(x + 16, y));
				}
			}
			// Scalp shine.
			this.c.top(h, 3, 3, argb(mix(this.skin, 0xFFFFFF, 0.12F)));
			return;
		}
		// Crown: the top is hair (a receding scalp shows skin at the front).
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				boolean scalp = st == Hair.RECEDING && z >= 4 && x >= 2 && x <= 5;
				if (!scalp) {
					this.c.top(h, x, z, argb(noisy(mix(this.hair, 0xFFFFFF, 0.06F), x + 40, z)));
				}
			}
		}
		// The rows each face's hair reaches down to, per style.
		int frontRows = switch (st) {
			case BUZZ, RECEDING, BALD -> 0;
			case SHORT, SIDE_PART, TOUSLED, CURLY, TOPKNOT, PONYTAIL, BUN, BRAID, TWO_BRAIDS, LONG -> 1;
			case FRINGE, BOB -> 2;
		};
		int sideRows = switch (st) {
			case BUZZ, RECEDING -> 2;
			case SHORT, SIDE_PART, TOUSLED, CURLY, TOPKNOT -> 3;
			case BOB -> 7;
			case PONYTAIL, BUN, BRAID, TWO_BRAIDS -> 4;
			case LONG, FRINGE -> 8;
			default -> 3;
		};
		int backRows = switch (st) {
			case BUZZ, RECEDING -> 5;
			case SHORT, SIDE_PART, TOUSLED, CURLY, TOPKNOT -> 6;
			case BOB -> 7;
			default -> 8;
		};
		boolean longHair = st == Hair.LONG || st == Hair.FRINGE && this.look.female();

		// Front hairline.
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < frontRows; y++) {
				this.c.front(h, x, y, hairPx(x, y));
			}
		}
		switch (st) {
			case BUZZ -> {
				for (int x = 0; x < 8; x++) {
					this.c.front(h, x, 0, argb(mix(this.skin, shade(this.hair, 0.8F), 0.55F)));
				}
			}
			case RECEDING -> {
				for (int x : new int[]{0, 1, 6, 7}) {
					this.c.front(h, x, 0, hairPx(x, 0));
				}
			}
			case SHORT, CURLY -> {
				this.c.front(h, 0, 1, hairPx(0, 1));
				this.c.front(h, 7, 1, hairPx(7, 1));
				this.c.front(h, 2 + (int) (this.noiseSeed & 3), 1, hairPx(3, 1));
			}
			case SIDE_PART -> {
				for (int x = 0; x <= 3; x++) {
					this.c.front(h, x, 1, hairPx(x, 1));
				}
				this.c.front(h, 0, 2, hairPx(0, 2));
				// The parting line.
				this.c.front(h, 5, 0, argb(shade(this.hair, 0.7F)));
				this.c.top(h, 5, 7, argb(shade(this.hair, 0.7F)));
				this.c.top(h, 5, 6, argb(shade(this.hair, 0.75F)));
			}
			case TOUSLED -> {
				for (int x = 0; x < 8; x += 2) {
					this.c.front(h, x + (int) (this.noiseSeed >>> 3 & 1), 1, hairPx(x, 1));
				}
			}
			case FRINGE, BOB -> {
				this.c.front(h, 0, 2, hairPx(0, 2));
				this.c.front(h, 7, 2, hairPx(7, 2));
				// A jagged fringe edge.
				this.c.front(h, 3, 1, argb(shade(this.hair, 0.85F)));
				this.c.front(h, 2 + (int) (this.noiseSeed & 3), 2, hairPx(4, 2));
			}
			case LONG, PONYTAIL, BUN, BRAID, TWO_BRAIDS, TOPKNOT -> {
				// Parted in the middle and drawn back.
				this.c.front(h, 0, 1, hairPx(0, 1));
				this.c.front(h, 1, 1, hairPx(1, 1));
				this.c.front(h, 6, 1, hairPx(6, 1));
				this.c.front(h, 7, 1, hairPx(7, 1));
				this.c.front(h, 3, 0, argb(shade(this.hair, 0.72F)));
				this.c.top(h, 3, 7, argb(shade(this.hair, 0.72F)));
				this.c.top(h, 3, 6, argb(shade(this.hair, 0.78F)));
				this.c.top(h, 3, 5, argb(shade(this.hair, 0.82F)));
			}
		}
		if (longHair) {
			// Hair falls past the face: framing columns.
			for (int y = 1; y < 8; y++) {
				this.c.front(h, 0, y, hairPx(0, y));
				this.c.front(h, 7, y, hairPx(7, y));
			}
		}
		// Sides: hair down to sideRows, ears showing for short styles; front columns keep a sideburn.
		for (int z = 0; z < 8; z++) {
			for (int y = 0; y < 8; y++) {
				boolean covered = y < sideRows;
				if (!covered && y < 6 && z < 3 && sideRows <= 4) {
					covered = true;
				}
				if (!covered && (st == Hair.SHORT || st == Hair.SIDE_PART || st == Hair.CURLY || st == Hair.TOUSLED) && z == 7 && y <= 4) {
					covered = true;
				}
				if (covered) {
					this.c.right(h, z, y, hairPx(z + 8, y));
					this.c.left(h, 7 - z, y, hairPx(z + 24, y));
				}
			}
		}
		// Back.
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < backRows; y++) {
				this.c.back(h, x, y, hairPx(x + 48, y));
			}
			if (backRows < 8) {
				// A ragged nape.
				if ((x + (int) this.noiseSeed) % 3 == 0) {
					this.c.back(h, x, backRows, hairPx(x + 48, backRows));
				}
			}
		}

		// Volume and shape on the outer layer.
		switch (st) {
			case CURLY, TOUSLED -> {
				for (int x = 0; x < 8; x++) {
					for (int z = 0; z < 8; z++) {
						if (hash(x, z, 7) > 0.35F) {
							this.c.top(hat, x, z, hairPx(x + 40, z));
						}
					}
					if (hash(x, 0, 9) > 0.2F) {
						this.c.front(hat, x, 0, hairPx(x, 0));
					}
				}
				for (int z = 0; z < 8; z++) {
					for (int y = 0; y < 2; y++) {
						if (hash(z, y, 11) > 0.1F) {
							this.c.right(hat, z, y, hairPx(z + 8, y));
							this.c.left(hat, z, y, hairPx(z + 24, y));
						}
					}
				}
				for (int x = 0; x < 8; x++) {
					for (int y = 0; y < 4; y++) {
						if (hash(x, y, 13) > -0.2F) {
							this.c.back(hat, x, y, hairPx(x + 48, y));
						}
					}
				}
			}
			case LONG, FRINGE, BOB -> {
				if (longHair || st == Hair.BOB) {
					int rows = st == Hair.BOB ? 7 : 8;
					for (int x = 0; x < 8; x++) {
						for (int y = 1; y < rows; y++) {
							this.c.back(hat, x, y, hairPx(x + 48, y + 1));
						}
					}
					for (int z = 0; z < 6; z++) {
						for (int y = 1; y < rows; y++) {
							this.c.right(hat, z, y, hairPx(z + 8, y + 1));
							this.c.left(hat, 7 - z, y, hairPx(z + 24, y + 1));
						}
					}
				}
				if (st == Hair.FRINGE || st == Hair.BOB) {
					for (int x = 0; x < 8; x++) {
						this.c.front(hat, x, 0, hairPx(x, 0));
						if (x % 3 != 1) {
							this.c.front(hat, x, 1, hairPx(x, 1));
						}
					}
				}
			}
			case BUN, TOPKNOT -> {
				// A knot of hair: on the back of the head, or high on the crown.
				boolean top = st == Hair.TOPKNOT || (Genome.detail(this.look.appearance()) & 1) == 0;
				int knot = mix(this.hair, 0xFFFFFF, 0.05F);
				if (top) {
					for (int x = 2; x <= 5; x++) {
						for (int z = 1; z <= 4; z++) {
							this.c.top(hat, x, z, argb(noisy(knot, x, z + 50)));
						}
						this.c.back(hat, x, 0, argb(noisy(shade(knot, 0.9F), x, 51)));
					}
				} else {
					for (int x = 2; x <= 5; x++) {
						for (int y = 2; y <= 5; y++) {
							this.c.back(hat, x, y, argb(noisy(knot, x, y + 52)));
						}
					}
					this.c.top(hat, 3, 0, argb(knot));
					this.c.top(hat, 4, 0, argb(knot));
				}
				// A ribbon or pin.
				int tie = this.dyes.accent()[0];
				this.c.back(hat, 2, top ? 0 : 4, argb(tie));
			}
			case PONYTAIL -> {
				int tie = this.dyes.accent()[0];
				for (int y = 3; y < 8; y++) {
					this.c.back(hat, 3, y, hairPx(51, y));
					this.c.back(hat, 4, y, hairPx(52, y));
				}
				this.c.back(hat, 3, 3, argb(tie));
				this.c.back(hat, 4, 3, argb(tie));
				// The tail falls onto the shoulders.
				Part jacket = SkinCanvas.JACKET;
				for (int y = 0; y < 3; y++) {
					this.c.back(jacket, 3, y, hairPx(51, y + 8));
					this.c.back(jacket, 4, y, hairPx(52, y + 8));
				}
			}
			case BRAID -> {
				Part jacket = SkinCanvas.JACKET;
				int tie = this.dyes.accent()[0];
				for (int y = 0; y < 8; y++) {
					int col = (y % 2 == 0) ? this.hair : shade(this.hair, 0.78F);
					this.c.back(jacket, 3, y, argb(y % 2 == 0 ? col : mix(col, 0xFFFFFF, 0.06F)));
					this.c.back(jacket, 4, y, argb(y % 2 == 0 ? shade(col, 0.82F) : col));
				}
				this.c.back(jacket, 3, 8, argb(tie));
				this.c.back(jacket, 4, 8, argb(tie));
				for (int x = 2; x <= 5; x++) {
					this.c.back(hat, x, 6, hairPx(x + 48, 6));
					this.c.back(hat, x, 7, hairPx(x + 48, 7));
				}
			}
			case TWO_BRAIDS -> {
				Part jacket = SkinCanvas.JACKET;
				int tie = this.dyes.accent()[0];
				for (int y = 0; y < 6; y++) {
					int col = y % 2 == 0 ? this.hair : shade(this.hair, 0.8F);
					this.c.front(jacket, 0, y, argb(col));
					this.c.front(jacket, 7, y, argb(col));
				}
				this.c.front(jacket, 0, 6, argb(tie));
				this.c.front(jacket, 7, 6, argb(tie));
				for (int z = 4; z < 8; z++) {
					for (int y = 4; y < 8; y++) {
						this.c.right(hat, z, y, hairPx(z + 8, y));
						this.c.left(hat, 7 - z, y, hairPx(z + 24, y));
					}
				}
			}
			default -> {
			}
		}
	}

	/** One hair pixel with strand shading: light on top, darker toward the ends, streaks by column. */
	private int hairPx(int x, int y) {
		int base = this.hair;
		float streak = hash(x, 0, 101);
		float f = 1.0F + streak * 0.07F - y * 0.012F;
		int col = shade(base, f);
		if (hash(x, y, 103) > 0.72F) {
			col = mix(col, 0xFFFFFF, 0.07F);
		} else if (hash(x, y, 107) > 0.72F) {
			col = shade(col, 0.88F);
		}
		return argb(col);
	}

	// ---------------------------------------------------------------- clothes

	private void paintClothes(Outfit o) {
		Part body = SkinCanvas.BODY;
		// Shirt or tunic on the body and arms.
		for (int s = 0; s < body.around(); s++) {
			for (int y = 0; y < 12; y++) {
				this.c.around(body, s, y, argb(cloth(o.top, body, s, y, o.topPattern)));
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 4; z++) {
				this.c.top(body, x, z, argb(cloth(o.top, body, x, z + 20, o.topPattern)));
				this.c.bottom(body, x, z, argb(shade(o.legs, 0.9F)));
			}
		}
		// Neckline.
		switch (o.neck) {
			case V -> {
				this.c.front(body, 3, 0, argb(this.skin));
				this.c.front(body, 4, 0, argb(this.skin));
				this.c.front(body, 3, 1, argb(shade(this.skin, 0.92F)));
				this.c.front(body, 4, 1, argb(shade(this.skin, 0.92F)));
				this.c.front(body, 2, 0, argb(shade(o.top, 0.85F)));
				this.c.front(body, 5, 0, argb(shade(o.top, 0.85F)));
			}
			case CROSS -> {
				// A wrap collar, the wearer's left panel over the right: a white under-collar at the throat and the
				// top panel's edge running down to the sash at the wearer's right.
				int under = o.underCollar;
				int edge = shade(o.top, 0.78F);
				this.c.front(body, 3, 0, argb(this.skin));
				this.c.front(body, 4, 0, argb(this.skin));
				this.c.front(body, 2, 0, argb(under));
				this.c.front(body, 5, 0, argb(under));
				this.c.front(body, 3, 1, argb(under));
				for (int y = 1; y <= 6; y++) {
					this.c.front(body, Math.max(0, 5 - y), y, argb(edge));
				}
			}
			case SIDE -> {
				// A robe closing at the wearer's right: a curved edge from the throat to the side.
				this.c.front(body, 3, 0, argb(this.skin));
				this.c.front(body, 4, 0, argb(this.skin));
				int edge = o.trimColour;
				this.c.front(body, 2, 0, argb(edge));
				this.c.front(body, 5, 0, argb(edge));
				this.c.front(body, 2, 1, argb(edge));
				this.c.front(body, 1, 2, argb(edge));
				this.c.front(body, 0, 3, argb(edge));
				this.c.front(body, 0, 2, argb(shade(o.top, 0.88F)));
			}
			case ROUND -> {
				this.c.front(body, 3, 0, argb(this.skin));
				this.c.front(body, 4, 0, argb(this.skin));
				this.c.front(body, 2, 0, argb(shade(o.top, 0.82F)));
				this.c.front(body, 5, 0, argb(shade(o.top, 0.82F)));
			}
			case HIGH -> {
				for (int x = 2; x <= 5; x++) {
					this.c.front(body, x, 0, argb(shade(o.top, 0.86F)));
				}
			}
		}
		// Buttons down a shirt front.
		if (o.buttons) {
			int btn = shade(o.top, 0.7F);
			for (int y = 2; y <= 7; y += 2) {
				this.c.front(body, 4, y, argb(btn));
			}
			for (int y = 1; y <= 10; y++) {
				if (this.c.getFront(body, 4, y) != argb(btn)) {
					this.c.front(body, 4, y, argb(shade(cloth(o.top, body, 12, y, o.topPattern) & 0xFFFFFF, 0.94F)));
				}
			}
		}
		// Sleeves.
		int sleeveEnd = switch (o.sleeves) {
			case LONG -> 10;
			case ROLLED -> 6;
			case SHORT -> 4;
		};
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				for (int y = 0; y < sleeveEnd; y++) {
					this.c.around(arm, s, y, argb(cloth(o.top, arm, s, y, o.topPattern)));
				}
				// Cuff or rolled fold.
				int cuff = o.sleeves == Sleeves.ROLLED ? mix(o.top, 0xFFFFFF, 0.12F) : shade(o.top, 0.84F);
				this.c.around(arm, s, sleeveEnd - 1, argb(cuff));
			}
			for (int x = 0; x < arm.w(); x++) {
				for (int z = 0; z < 4; z++) {
					this.c.top(arm, x, z, argb(cloth(o.top, arm, x, z + 30, o.topPattern)));
				}
			}
		}
		// Legs: trousers, shorts or a skirt.
		for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
			int legEnd = switch (o.legKind) {
				case TROUSERS -> 12;
				case SHORTS -> 6;
				case SKIRT, ROBE -> o.skirtLength;
			};
			for (int s = 0; s < leg.around(); s++) {
				for (int y = 0; y < legEnd; y++) {
					this.c.around(leg, s, y, argb(cloth(o.legs, leg, s, y, o.legPattern)));
				}
				if (o.legKind == LegKind.SKIRT || o.legKind == LegKind.ROBE) {
					if (o.hem != 0) {
						this.c.around(leg, s, legEnd - 1, argb(o.hem));
					}
				}
				// Knee socks or stockings below shorts and skirts.
				if (legEnd < 12 && o.stockings != 0) {
					for (int y = Math.max(legEnd, 7); y < 12; y++) {
						this.c.around(leg, s, y, argb(noisy(o.stockings, s, y + 60)));
					}
				}
			}
			// Inner seam shadow.
			Face inner = leg == SkinCanvas.RIGHT_LEG ? Face.LEFT : Face.RIGHT;
			for (int y = 0; y < Math.min(legEnd, 10); y++) {
				for (int z = 0; z < 4; z++) {
					int px = inner == Face.LEFT ? this.c.get(leg.u() + 8 + z, leg.v() + 4 + y) : this.c.get(leg.u() + z, leg.v() + 4 + y);
					int darker = argb(shade(px & 0xFFFFFF, 0.9F));
					if (inner == Face.LEFT) {
						this.c.left(leg, z, y, darker);
					} else {
						this.c.right(leg, z, y, darker);
					}
				}
			}
		}
		// The skirt or robe flares on the outer layer.
		if (o.legKind == LegKind.SKIRT || o.legKind == LegKind.ROBE) {
			for (Part pants : new Part[]{SkinCanvas.RIGHT_PANTS, SkinCanvas.LEFT_PANTS}) {
				for (int s = 0; s < pants.around(); s++) {
					// Leave the inner faces open so the two legs' skirts meet without a seam of doubled cloth.
					Face side = SkinCanvas.sideOf(pants, s);
					boolean inner = pants == SkinCanvas.RIGHT_PANTS ? side == Face.LEFT : side == Face.RIGHT;
					if (inner) {
						continue;
					}
					int flareEnd = Math.min(o.skirtLength, 11);
					for (int y = 0; y < flareEnd; y++) {
						this.c.around(pants, s, y, argb(cloth(o.legs, pants, s, y + 3, o.legPattern)));
					}
					if (o.hem != 0) {
						this.c.around(pants, s, flareEnd - 1, argb(o.hem));
					}
				}
			}
		}
		// A dress: the skirt's cloth carries up over the body as a bodice, the blouse showing at the shoulders.
		if (o.bodice != 0) {
			for (int s = 0; s < body.around(); s++) {
				Face side = SkinCanvas.sideOf(body, s);
				int from = side == Face.FRONT || side == Face.BACK ? 2 : 3;
				for (int y = from; y < 12; y++) {
					this.c.around(body, s, y, argb(cloth(o.bodice, body, s, y, o.legPattern == Pattern.WOVEN ? Pattern.PLAIN : o.legPattern)));
				}
			}
			for (int x : new int[]{2, 5}) {
				this.c.front(body, x, 2, argb(o.top));
			}
			// Laces up the front.
			for (int y = 3; y <= 7; y += 2) {
				this.c.front(body, 3, y, argb(shade(o.bodice, 0.7F)));
				this.c.front(body, 4, y + 1, argb(shade(o.bodice, 0.7F)));
			}
			for (int s = 0; s < body.around(); s++) {
				this.c.around(body, s, 8, argb(shade(o.bodice, 0.85F)));
			}
		}
		// Belt or sash.
		if (o.sash != 0) {
			for (int s = 0; s < body.around(); s++) {
				this.c.around(body, s, 7, argb(shade(o.sash, 0.9F)));
				this.c.around(body, s, 8, argb(o.sash));
				this.c.around(body, s, 9, argb(shade(o.sash, 0.92F)));
			}
			if (this.look.culture() == Culture.WISTERIA || this.look.culture() == Culture.KARST) {
				// A cord over the sash and its knot at the back.
				for (int s = 0; s < body.around(); s++) {
					this.c.around(body, s, 8, argb(s % 3 == 0 ? o.trimColour : o.sash));
				}
				this.c.back(body, 3, 9, argb(shade(o.sash, 0.8F)));
				this.c.back(body, 4, 9, argb(shade(o.sash, 0.8F)));
				this.c.back(body, 3, 10, argb(shade(o.sash, 0.75F)));
			}
		} else if (o.belt != 0) {
			for (int s = 0; s < body.around(); s++) {
				this.c.around(body, s, 8, argb(o.belt));
			}
			int buckle = this.look.culture() == Culture.TROPICAL ? o.belt : 0xC8B070;
			this.c.front(body, 3, 8, argb(buckle));
			this.c.front(body, 4, 8, argb(shade(buckle, 0.8F)));
		}
		// Woven or embroidered bands at hems and cuffs.
		if (o.topPattern == Pattern.EMBROIDERY || o.topPattern == Pattern.WOVEN) {
			int a = o.trimColour;
			int b = this.dyes.accent()[0];
			for (int s = 0; s < body.around(); s++) {
				this.c.around(body, s, 11, argb(s % 2 == 0 ? a : b));
				if (o.topPattern == Pattern.WOVEN) {
					this.c.around(body, s, 10, argb(s % 4 < 2 ? b : shade(o.top, 0.9F)));
				}
			}
			for (Part arm : new Part[]{this.rArm, this.lArm}) {
				for (int s = 0; s < arm.around(); s++) {
					this.c.around(arm, s, sleeveEnd - 1, argb(s % 2 == 0 ? a : b));
				}
			}
			for (int x = 1; x <= 6; x++) {
				if (this.c.getFront(body, x, 0) != argb(this.skin)) {
					this.c.front(body, x, 0, argb(x % 2 == 0 ? a : b));
				}
			}
		}
		// Shoes and boots.
		for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
			int from = o.boots ? 8 : 10;
			if (o.sandals) {
				// Straps over bare feet.
				for (int s = 0; s < leg.around(); s++) {
					this.c.around(leg, s, 11, argb(o.shoes));
				}
				this.c.front(leg, 1, 10, argb(o.shoes));
				this.c.front(leg, 2, 10, argb(o.shoes));
				continue;
			}
			for (int s = 0; s < leg.around(); s++) {
				for (int y = from; y < 12; y++) {
					int col = y == from && o.boots ? mix(o.shoes, 0xFFFFFF, 0.08F) : o.shoes;
					this.c.around(leg, s, y, argb(noisy(y == 11 ? shade(col, 0.8F) : col, s, y + 70)));
				}
			}
			for (int x = 0; x < 4; x++) {
				for (int z = 0; z < 4; z++) {
					this.c.bottom(leg, x, z, argb(shade(o.shoes, 0.6F)));
				}
			}
			if (o.furBoots) {
				for (int s = 0; s < leg.around(); s++) {
					this.c.around(leg, s, from, argb(noisy(0xE2D8C4, s, 90)));
				}
			}
		}
		// Outer garments that are part of the culture's dress (not the job's).
		if (o.vest != 0) {
			paintVest(o.vest, o.trimColour, false);
		}
		if (o.shawl != 0) {
			paintShawl(o.shawl, o.trimColour);
		}
		if (o.apron != 0) {
			paintApron(o.apron, false, o.apronStraps);
		}
		if (o.suspenders != 0) {
			for (int y = 0; y < 8; y++) {
				this.c.front(body, 1, y, argb(o.suspenders));
				this.c.front(body, 6, y, argb(o.suspenders));
				this.c.back(body, 2 + (y < 4 ? 0 : (y - 4) / 2), y, argb(o.suspenders));
				this.c.back(body, 5 - (y < 4 ? 0 : (y - 4) / 2), y, argb(o.suspenders));
			}
			if (this.look.culture() == Culture.ALPINE) {
				// The breast bar of lederhosen braces.
				for (int x = 1; x <= 6; x++) {
					this.c.front(body, x, 4, argb(o.suspenders));
				}
			}
		}
		if (o.furCollar) {
			int fur = this.look.culture() == Culture.TUNDRA ? 0xE6DED0 : 0x8A6A4A;
			Part j = SkinCanvas.JACKET;
			for (int s = 0; s < j.around(); s++) {
				this.c.around(j, s, 0, argb(noisy(fur, s, 95)));
			}
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 4; z++) {
					this.c.top(j, x, z, argb(noisy(fur, x, z + 96)));
				}
			}
			this.c.front(j, 3, 0, 0);
			this.c.front(j, 4, 0, 0);
		}
	}

	/** A cloth pixel: the dye with weave noise, folds at the sides, and the culture's pattern. */
	private int cloth(int dye, Part p, int s, int y, Pattern pattern) {
		int col = dye;
		switch (pattern) {
			case STRIPES -> {
				if (y % 2 == 1 && p != SkinCanvas.RIGHT_LEG && p != SkinCanvas.LEFT_LEG) {
					col = this.dyes.dark()[0];
				}
			}
			case CHECK -> {
				boolean a = (s / 2 + y / 2) % 2 == 0;
				boolean line = s % 4 == 0 || y % 4 == 0;
				col = a ? dye : shade(dye, 0.84F);
				if (line) {
					col = mix(col, this.dyes.accent()[0], 0.45F);
				}
			}
			case FLORAL -> {
				// Small flowers on a staggered grid: a centre and four petals.
				int row = Math.floorDiv(y, 4);
				int cx = Math.floorMod(s - row * 2, 4);
				int cy = Math.floorMod(y, 4);
				int petal = this.dyes.accent()[Math.floorMod(row + Math.floorDiv(s, 4), this.dyes.accent().length)];
				if (petal == dye) {
					petal = 0xFFF6E8;
				}
				if (cx == 1 && cy == 1) {
					col = 0xF8E070;
				} else if (cx == 1 && (cy == 0 || cy == 2) || cy == 1 && (cx == 0 || cx == 2)) {
					col = petal;
				}
			}
			default -> {
			}
		}
		return jitter(col, p.u() + s, p.v() + y, 0.035F);
	}

	private void paintVest(int colour, int trim, boolean closed) {
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			Face side = SkinCanvas.sideOf(j, s);
			for (int y = 0; y < 10; y++) {
				if (side == Face.FRONT) {
					int x = s - j.d();
					// Open front: two panels with a gap that narrows to the waist.
					boolean gap = !closed && (x == 3 || x == 4) && y < 9;
					if (gap || y == 0 && (x >= 2 && x <= 5)) {
						continue;
					}
				}
				this.c.around(j, s, y, argb(jitter(colour, s, y + 300, 0.03F)));
			}
		}
		if (closed) {
			for (int y = 2; y < 9; y += 2) {
				this.c.front(j, 3, y, argb(trim));
			}
		} else {
			for (int y = 1; y < 9; y++) {
				this.c.front(j, 2, y, argb(shade(colour, 0.82F)));
				this.c.front(j, 5, y, argb(shade(colour, 0.82F)));
			}
			this.c.front(j, 2, 4, argb(trim));
			this.c.front(j, 2, 6, argb(trim));
		}
	}

	private void paintShawl(int colour, int fringe) {
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			Face side = SkinCanvas.sideOf(j, s);
			int depth = side == Face.BACK ? 6 : side == Face.FRONT ? 3 : 4;
			for (int y = 0; y < depth; y++) {
				if (side == Face.FRONT) {
					int x = s - j.d();
					if (y >= 1 && x >= 2 + y - 1 && x <= 5 - y + 1) {
						continue;
					}
					if (y == 0 && (x == 3 || x == 4)) {
						continue;
					}
				}
				this.c.around(j, s, y, argb(cloth(colour, j, s, y, this.dyes.pattern() == Pattern.CHECK ? Pattern.CHECK : Pattern.PLAIN)));
			}
			this.c.around(j, s, depth, argb(s % 2 == 0 ? fringe : 0));
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 4; z++) {
				this.c.top(j, x, z, argb(colour));
			}
		}
		this.c.top(j, 3, 3, 0);
		this.c.top(j, 4, 3, 0);
	}

	private void paintApron(int colour, boolean full, boolean straps) {
		Part j = SkinCanvas.JACKET;
		int from = full ? 1 : 8;
		for (int x = 1; x <= 6; x++) {
			for (int y = from; y < 12; y++) {
				this.c.front(j, x, y, argb(jitter(colour, x, y + 400, 0.03F)));
			}
		}
		if (full) {
			// Bib edges and the neck strap.
			for (int y = 1; y < 8; y++) {
				this.c.front(j, 1, y, argb(shade(colour, 0.9F)));
				this.c.front(j, 6, y, argb(shade(colour, 0.9F)));
			}
			if (straps) {
				this.c.front(j, 2, 0, argb(shade(colour, 0.85F)));
				this.c.front(j, 5, 0, argb(shade(colour, 0.85F)));
				for (int z = 0; z < 4; z++) {
					this.c.top(j, 2, z, argb(shade(colour, 0.85F)));
					this.c.top(j, 5, z, argb(shade(colour, 0.85F)));
				}
			}
		}
		// Waist ties round to the back.
		for (int s = 0; s < j.around(); s++) {
			if (SkinCanvas.sideOf(j, s) != Face.FRONT) {
				this.c.around(j, s, 8, argb(shade(colour, 0.85F)));
			}
		}
		this.c.back(j, 3, 9, argb(shade(colour, 0.85F)));
		this.c.back(j, 4, 9, argb(shade(colour, 0.8F)));
		// The apron continues down the legs' fronts.
		for (Part pants : new Part[]{SkinCanvas.RIGHT_PANTS, SkinCanvas.LEFT_PANTS}) {
			int len = full ? 7 : 6;
			for (int x = 0; x < 4; x++) {
				for (int y = 0; y < len; y++) {
					boolean edge = pants == SkinCanvas.RIGHT_PANTS ? x == 0 : x == 3;
					if (edge && y > 2) {
						continue;
					}
					this.c.front(pants, x, y, argb(jitter(y == len - 1 ? shade(colour, 0.9F) : colour, x + pants.u(), y + 410, 0.03F)));
				}
			}
		}
	}

	// ---------------------------------------------------------------- station

	private static final int GOLD = 0xD8B848;
	/** Deep, costly dyes: what the gentry wear. */
	private static final int[] GENTRY = {0x6E1C28, 0x1C4636, 0x22325A, 0x44285A, 0x5A3A1C, 0x2A2A30};
	/** Royal colours: purple, crimson, the deepest blue. */
	private static final int[] ROYAL = {0x5E1A6E, 0x8E1616, 0x1A2A6E};

	/** Faded, greyed-down cloth: what is left of a colour after years of wear and cheap dye. */
	static int drab(int colour) {
		int grey = Math.round(luma(colour) * 255);
		int g = grey << 16 | grey << 8 | grey;
		return shade(mix(mix(colour, g, 0.55F), 0x7A6A58, 0.3F), 0.88F);
	}

	/** A colour brought up: cleaner and a little richer, for those who can pay for good dye. */
	static int rich(int colour) {
		int r = colour >> 16 & 0xFF;
		int g = colour >> 8 & 0xFF;
		int b = colour & 0xFF;
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if (max - min < 24) {
			return colour;
		}
		// push each channel away from the mean
		int mean = (r + g + b) / 3;
		r = Math.clamp(mean + Math.round((r - mean) * 1.35F), 0, 255);
		g = Math.clamp(mean + Math.round((g - mean) * 1.35F), 0, 255);
		b = Math.clamp(mean + Math.round((b - mean) * 1.35F), 0, 255);
		return r << 16 | g << 8 | b;
	}

	/** The outfit before it is painted, adjusted to the household's station. */
	private void dressForStation(Outfit o) {
		boolean child = this.look.age() == AgeStage.CHILD;
		switch (this.look.station()) {
			case POOR -> {
				o.top = drab(o.top);
				o.legs = drab(o.legs);
				o.buttons = false;
				if (o.topPattern == Pattern.EMBROIDERY || o.topPattern == Pattern.WOVEN) {
					o.topPattern = Pattern.PLAIN;
				}
				o.vest = o.vest != 0 ? drab(o.vest) : 0;
				o.shawl = o.shawl != 0 ? drab(o.shawl) : 0;
				o.sash = o.sash != 0 ? drab(o.sash) : 0;
				o.apron = o.apron != 0 ? drab(o.apron) : 0;
				o.hem = 0;
				o.furCollar = false;
				o.boots = false;
				o.furBoots = false;
				o.shoes = shade(drab(o.shoes), 0.8F);
				if (!child && (this.noiseSeed & 6) == 0) {
					// worn through: bare feet in rag wraps
					o.sandals = true;
					o.shoes = 0x6A5A48;
				}
			}
			case MERCHANT -> {
				o.top = rich(o.top);
				o.legs = rich(o.legs);
				o.buttons = !child;
				o.trimColour = GOLD;
				o.boots = true;
				o.sandals = false;
			}
			case GENTRY -> {
				// fine linen under a coat (painted after the trade), dark hose, good boots
				o.top = o.legKind == LegKind.ROBE ? GENTRY[(int) (this.noiseSeed >>> 7 & 0xFF) % GENTRY.length] : 0xF2EEE6;
				o.topPattern = o.legKind == LegKind.ROBE ? Pattern.EMBROIDERY : Pattern.PLAIN;
				o.legs = o.legKind == LegKind.ROBE ? o.top : o.legKind == LegKind.SKIRT ? GENTRY[(int) (this.noiseSeed >>> 9 & 0xFF) % GENTRY.length] : 0x22201E;
				o.trimColour = GOLD;
				o.buttons = !child;
				o.boots = !o.sandals || o.legKind != LegKind.ROBE;
				o.shoes = 0x2A2018;
				o.hem = o.legKind == LegKind.TROUSERS ? 0 : GOLD;
				o.apron = 0;
			}
			case LORD -> {
				int royal = ROYAL[(int) (this.noiseSeed >>> 11 & 0xFF) % ROYAL.length];
				o.top = royal;
				o.topPattern = Pattern.EMBROIDERY;
				o.legs = o.legKind == LegKind.TROUSERS || o.legKind == LegKind.SHORTS ? 0x1E1A1A : royal;
				if (o.legKind == LegKind.SHORTS) {
					o.legKind = LegKind.TROUSERS;
				}
				o.trimColour = GOLD;
				o.buttons = false;
				o.boots = true;
				o.sandals = false;
				o.shoes = 0x2A1A14;
				o.hem = o.legKind == LegKind.TROUSERS ? 0 : GOLD;
				o.apron = 0;
				o.vest = 0;
				o.shawl = 0;
				o.belt = GOLD;
				o.sash = 0;
			}
			default -> {
			}
		}
		if (o.legKind == LegKind.ROBE) {
			o.legs = o.top;
		}
	}

	/** Patches sewn over the worn places, a frayed hem, the grime of hard living. */
	private void patches(Outfit o) {
		Part body = SkinCanvas.BODY;
		int n = 2 + (int) (this.noiseSeed >>> 3 & 3);
		for (int i = 0; i < n; i++) {
			Part p = i % 2 == 0 ? body : (i % 4 == 1 ? SkinCanvas.RIGHT_LEG : SkinCanvas.LEFT_LEG);
			int colour = mix(i % 2 == 0 ? o.top : o.legs, (this.noiseSeed >>> (i * 5) & 1) == 0 ? 0x8A7458 : 0x5A5A4E, 0.6F);
			int s0 = (int) ((this.noiseSeed >>> (i * 7 + 2)) & 0xFF) % Math.max(1, p.around() - 2);
			int y0 = 2 + (int) ((this.noiseSeed >>> (i * 7 + 9)) & 0xFF) % 7;
			for (int ds = 0; ds < 2; ds++) {
				for (int dy = 0; dy < 2; dy++) {
					this.c.around(p, s0 + ds, y0 + dy, argb(jitter(colour, s0 + ds, y0 + dy + 1600, 0.05F)));
				}
			}
			// the stitching round it
			this.c.around(p, s0 + 2, y0, argb(shade(colour, 0.7F)));
		}
		// a ragged hem
		for (int s = 0; s < body.around(); s++) {
			if (hash(s, 11, 1610) > 0.3F) {
				this.c.around(body, s, 11, argb(shade(this.c.getAround(body, s, 11) & 0xFFFFFF, 0.75F)));
			}
		}
		dust(0x5A4A38, 0.78F, false);
	}

	/** What station adds over the trade's dress: the gentry's coat, the lord's mantle and chain. */
	private void paintStation(Outfit o) {
		if (this.look.age() == AgeStage.CHILD) {
			return;
		}
		Part j = SkinCanvas.JACKET;
		boolean bareJacket = this.c.getFront(j, 3, 5) == 0 && this.c.getFront(j, 1, 6) == 0;
		switch (this.look.station()) {
			case GENTRY -> {
				if (bareJacket) {
					int coat = GENTRY[(int) (this.noiseSeed >>> 7 & 0xFF) % GENTRY.length];
					paintCoat(coat, GOLD, this.look.female() ? 9 : 11, true);
				}
				lace();
			}
			case LORD -> {
				mantle(ROYAL[(int) (this.noiseSeed >>> 11 & 0xFF) % ROYAL.length]);
				chainOfOffice(GOLD, true);
			}
			case MERCHANT -> {
				if (bareJacket && this.look.job() != JobType.NONE && (this.noiseSeed & 1) == 0) {
					paintVest(rich(this.dyes.accent()[0]), GOLD, true);
				}
			}
			default -> {
			}
		}
		if (this.look.office() == Office.CAPTAIN) {
			// the captain's sash, shoulder to hip, over the tabard
			for (int y = 0; y < 9; y++) {
				this.c.front(j, 1 + y * 6 / 9, y, argb(GOLD));
				this.c.back(j, 6 - y * 6 / 9, y, argb(GOLD));
			}
		} else if (this.look.office() == Office.MASTER_BUILDER) {
			// the plans rolled at the belt
			for (int y = 7; y <= 10; y++) {
				this.c.left(j, 1, y, argb(y % 2 == 0 ? 0xEDE6D2 : 0x4A6A9A));
			}
		}
	}

	/** A white lace collar at the throat. */
	private void lace() {
		Part j = SkinCanvas.JACKET;
		this.c.front(j, 3, 0, argb(0xF6F4EE));
		this.c.front(j, 4, 0, argb(0xF6F4EE));
		this.c.front(j, 3, 1, argb(0xE8E4DA));
		this.c.front(j, 4, 1, argb(0xF6F4EE));
	}

	/** A chain of office: links over the shoulders and a medallion on the breast. */
	private void chainOfOffice(int metal, boolean gem) {
		Part j = SkinCanvas.JACKET;
		this.c.front(j, 1, 0, argb(metal));
		this.c.front(j, 6, 0, argb(metal));
		this.c.front(j, 2, 1, argb(shade(metal, 0.85F)));
		this.c.front(j, 5, 1, argb(shade(metal, 0.85F)));
		this.c.front(j, 2, 2, argb(metal));
		this.c.front(j, 5, 2, argb(metal));
		this.c.front(j, 3, 3, argb(metal));
		this.c.front(j, 4, 3, argb(shade(metal, 0.8F)));
		this.c.front(j, 3, 4, argb(gem ? 0xB01A2A : shade(metal, 0.7F)));
		this.c.front(j, 4, 4, argb(gem ? 0x80101A : shade(metal, 0.7F)));
	}

	/** The lord's mantle: royal cloth falling from the shoulders down the back, trimmed with ermine. */
	private void mantle(int colour) {
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			Face side = SkinCanvas.sideOf(j, s);
			if (side == Face.FRONT) {
				// open in front: only the edges show
				this.c.around(j, s, 0, argb(ermine(s, 0)));
				continue;
			}
			for (int y = 0; y < 12; y++) {
				this.c.around(j, s, y, argb(y == 0 ? ermine(s, y) : jitter(colour, s, y + 1700, 0.03F)));
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 4; z++) {
				this.c.top(j, x, z, argb(ermine(x, z + 20)));
			}
		}
		this.c.front(j, 3, 0, 0);
		this.c.front(j, 4, 0, 0);
		for (int y = 1; y < 12; y++) {
			this.c.front(j, 0, y, argb(ermine(0, y)));
			this.c.front(j, 7, y, argb(ermine(7, y)));
		}
		// the hem of the mantle down the legs at the back
		for (Part pants : new Part[]{SkinCanvas.RIGHT_PANTS, SkinCanvas.LEFT_PANTS}) {
			for (int x = 0; x < 4; x++) {
				for (int y = 0; y < 10; y++) {
					this.c.back(pants, x, y, argb(y == 9 ? ermine(x, y) : jitter(colour, x + pants.u(), y + 1710, 0.03F)));
				}
			}
		}
	}

	/** White fur with its black tails. */
	private int ermine(int x, int y) {
		return (x * 3 + y * 5) % 7 == 0 ? 0x1A1A1A : jitter(0xF2F0EA, x, y + 1720, 0.03F);
	}

	/** Hands and forearms stained by the trade (dye, ink, tannin, soot). */
	private void stain(int colour, float amount, int fromRow) {
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				for (int y = fromRow; y < 12; y++) {
					int px = this.c.getAround(arm, s, y);
					if (px != 0) {
						this.c.around(arm, s, y, argb(mix(px & 0xFFFFFF, colour, amount * (0.7F + 0.3F * hash(s, y, 1730)))));
					}
				}
			}
		}
	}

	/** Specks of something on an apron or the clothes' front: blood at the block, wax at the vat, dye at the tub. */
	private void spatter(int colour, float threshold) {
		Part j = SkinCanvas.JACKET;
		for (int x = 1; x <= 6; x++) {
			for (int y = 2; y < 12; y++) {
				if (this.c.getFront(j, x, y) != 0 && hash(x, y, 1740) > threshold) {
					this.c.front(j, x, y, argb(colour));
				}
			}
		}
	}

	// ---------------------------------------------------------------- trades

	private void paintJob(Outfit o) {
		Part j = SkinCanvas.JACKET;
		Part body = SkinCanvas.BODY;
		switch (this.look.job()) {
			case BUILDER -> {
				toolBelt(0x6A4A2E, true);
				rollSleeves(o);
			}
			case FARMER, HERDER -> {
				// Dungarees: a bib and braces over the shirt, the trousers the same cloth.
				int denim = this.look.job() == JobType.HERDER ? 0x7A6448 : pick(0x4A5E7A, 0x6A5236, 0x5E6A3E);
				if (this.look.job() == JobType.HERDER) {
					paintVest(pick(0xE2D8C2, 0xCFC2A6), 0xB0A080, false);
					for (int s = 0; s < j.around(); s++) {
						for (int y = 0; y < 10; y++) {
							if (this.c.getAround(j, s, y) != 0 && hash(s, y, 333) > 0.55F) {
								this.c.around(j, s, y, argb(0xF2ECDE));
							}
						}
					}
				} else if (!this.look.female() || (this.noiseSeed & 1) == 0) {
					for (int x = 1; x <= 6; x++) {
						for (int y = 3; y < 12; y++) {
							this.c.front(j, x, y, argb(jitter(denim, x, y + 500, 0.04F)));
						}
					}
					for (int y = 0; y < 3; y++) {
						this.c.front(j, 1, y, argb(denim));
						this.c.front(j, 6, y, argb(denim));
					}
					for (int y = 0; y < 9; y++) {
						this.c.back(j, 1 + y / 3, y, argb(denim));
						this.c.back(j, 6 - y / 3, y, argb(denim));
					}
					this.c.front(j, 1, 3, argb(0xC8B070));
					this.c.front(j, 6, 3, argb(0xC8B070));
					for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
						if (o.legKind == LegKind.TROUSERS) {
							for (int s = 0; s < leg.around(); s++) {
								for (int y = 0; y < (o.boots ? 8 : 10); y++) {
									this.c.around(leg, s, y, argb(jitter(denim, s + leg.u(), y + 510, 0.04F)));
								}
							}
						}
					}
				}
				rollSleeves(o);
				dust(0x8A6A44, 0.85F, true);
			}
			case FORESTER -> {
				// Check flannel and a leather jerkin.
				int a = pick(0xA0302A, 0x3E6A3A, 0x2E4E7A);
				for (int s = 0; s < body.around(); s++) {
					for (int y = 0; y < 12; y++) {
						boolean dark = (s / 2 + y / 2) % 2 == 0;
						this.c.around(body, s, y, argb(jitter(dark ? 0x2A2624 : a, s, y + 520, 0.04F)));
					}
				}
				for (Part arm : new Part[]{this.rArm, this.lArm}) {
					for (int s = 0; s < arm.around(); s++) {
						for (int y = 0; y < 10; y++) {
							boolean dark = (s / 2 + y / 2) % 2 == 0;
							this.c.around(arm, s, y, argb(jitter(dark ? 0x2A2624 : a, s, y + 530, 0.04F)));
						}
					}
				}
				paintVest(0x6A4A30, 0x4A3220, false);
				toolBelt(0x4A3220, false);
			}
			case QUARRIER, MASON -> {
				int apron = this.look.job() == JobType.MASON ? 0x9A968C : 0x7A6A58;
				if (this.look.job() == JobType.MASON) {
					paintApron(apron, true, true);
				}
				rollSleeves(o);
				dust(0xC8C4BA, 0.75F, false);
			}
			case FISHER -> {
				if (this.look.culture() == Culture.MARITIME || this.look.culture() == Culture.NORTHERN || this.look.culture() == Culture.TUNDRA) {
					// Oilskins.
					int oil = 0xD9B03A;
					paintCoat(oil, shade(oil, 0.8F), 9, false);
				} else {
					rollSleeves(o);
					for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
						for (int s = 0; s < leg.around(); s++) {
							for (int y = 7; y < 12; y++) {
								this.c.around(leg, s, y, argb(this.skin));
							}
							this.c.around(leg, s, 6, argb(mix(o.legs, 0xFFFFFF, 0.12F)));
						}
					}
				}
			}
			case HUNTER -> {
				paintVest(0x7A5A3A, 0x5A3E26, true);
				// Quiver strap from the right shoulder to the left hip.
				for (int y = 0; y < 9; y++) {
					this.c.front(j, 6 - y * 6 / 9, y, argb(0x4A3220));
				}
				for (int y = 0; y < 9; y++) {
					this.c.back(j, 1 + y * 6 / 9, y, argb(0x4A3220));
				}
				this.c.back(j, 1, 0, argb(0xD8D0C0));
				this.c.back(j, 2, 0, argb(0x8A6A44));
			}
			case CARPENTER -> {
				paintApron(0xB08A5A, true, true);
				dust(0xE0C890, 0.82F, false);
				// A pencil behind the ear.
				this.c.right(SkinCanvas.HAT, 4, 3, argb(0xE0B840));
				this.c.right(SkinCanvas.HAT, 5, 3, argb(0x5A4A3A));
			}
			case SMITH -> {
				// Short sleeves, forearms bare and sooty, a heavy leather apron.
				for (Part arm : new Part[]{this.rArm, this.lArm}) {
					for (int s = 0; s < arm.around(); s++) {
						for (int y = 4; y < 11; y++) {
							this.c.around(arm, s, y, argb(hash(s, y, 610) > 0.6F ? shade(this.skin, 0.8F) : this.skin));
						}
						this.c.around(arm, s, 3, argb(shade(o.top, 0.85F)));
					}
				}
				paintApron(0x4A3424, true, true);
				dust(0x3A3634, 0.8F, false);
				this.c.front(SkinCanvas.HEAD, 6, 6, argb(shade(this.skin, 0.8F)));
			}
			case BAKER -> {
				paintApron(0xF2F0EA, true, true);
				dust(0xFFFFFF, 0.8F, false);
				rollSleeves(o);
			}
			case BREWER -> {
				paintApron(0x5E6A3E, false, false);
				rollSleeves(o);
			}
			case INNKEEPER -> {
				paintVest(this.dyes.accent()[0], this.dyes.trim(), false);
				paintApron(0xEEEAE0, false, false);
				rollSleeves(o);
			}
			case MERCHANT, MINTER -> {
				int coat = this.look.job() == JobType.MINTER ? pick(0x2E2A3A, 0x3A2E2A) : this.dyes.accent()[(int) (this.noiseSeed >>> 5 & 0xFF) % this.dyes.accent().length];
				paintCoat(coat, this.dyes.trim(), 11, true);
				if (this.look.job() == JobType.MINTER) {
					// A chain of office with a medallion.
					int gold = 0xE0C050;
					this.c.front(j, 1, 0, argb(gold));
					this.c.front(j, 6, 0, argb(gold));
					this.c.front(j, 2, 1, argb(gold));
					this.c.front(j, 5, 1, argb(gold));
					this.c.front(j, 3, 2, argb(gold));
					this.c.front(j, 4, 2, argb(gold));
					this.c.front(j, 3, 3, argb(0xF8E080));
					this.c.front(j, 4, 3, argb(0xB89830));
					spectacles(0xC8A848);
				}
				// A purse at the belt.
				this.c.front(j, 6, 9, argb(0x7A5232));
				this.c.front(j, 6, 10, argb(0x5A3A22));
			}
			case GUARD -> paintGuard(o);
			case PRIEST -> paintRobe(pick(0xEEEAE0, 0xE6E0D0), this.dyes.accent()[0], true);
			case SCHOLAR -> {
				paintRobe(this.dyes.dark()[(int) (this.noiseSeed >>> 9 & 0xFF) % this.dyes.dark().length], this.dyes.trim(), false);
				spectacles(0x6A5A4A);
			}
			case COURIER -> {
				for (int y = 0; y < 9; y++) {
					this.c.front(j, 1 + y * 6 / 9, y, argb(0x6A4A2E));
					this.c.back(j, 6 - y * 6 / 9, y, argb(0x6A4A2E));
				}
				// The satchel on the hip.
				for (int z = 0; z < 4; z++) {
					for (int y = 8; y < 11; y++) {
						this.c.left(j, z, y, argb(jitter(0x8A6440, z, y + 620, 0.04F)));
					}
				}
				this.c.left(j, 1, 8, argb(0xC8B070));
				// A short travelling cape.
				int cape = this.dyes.dark()[0];
				for (int s = 0; s < j.around(); s++) {
					Face side = SkinCanvas.sideOf(j, s);
					if (side == Face.BACK) {
						for (int y = 0; y < 7; y++) {
							this.c.around(j, s, y, argb(jitter(cape, s, y + 630, 0.03F)));
						}
					} else if (side != Face.FRONT) {
						this.c.around(j, s, 0, argb(cape));
						this.c.around(j, s, 1, argb(cape));
					}
				}
			}
			case TAILOR -> {
				paintVest(this.dyes.accent()[0], this.dyes.trim(), true);
				// A tape measure round the neck.
				for (int y = 0; y < 7; y++) {
					int tick = y % 2 == 0 ? 0xE8D060 : 0x3A3226;
					this.c.front(j, 2, y, argb(y % 2 == 0 ? 0xE8D060 : 0xD8B840));
					this.c.front(j, 5, y, argb(tick == 0x3A3226 ? 0xD8B840 : 0xE8D060));
				}
				this.c.front(j, 2, 3, argb(0x3A3226));
				this.c.front(j, 5, 5, argb(0x3A3226));
			}
			// ---- phase 3: the trades of the chains, the services, the officials
			case MINER -> {
				rollSleeves(o);
				toolBelt(0x4A3424, false);
				dust(0x3A3634, 0.62F, false);
				this.c.front(SkinCanvas.HEAD, 1, 6, argb(shade(this.skin, 0.75F)));
				this.c.front(SkinCanvas.HEAD, 6, 5, argb(shade(this.skin, 0.78F)));
			}
			case HAULER -> {
				rollSleeves(o);
				// a leather pad on the shoulder for the loads, a rope across the chest
				for (int x = 0; x < this.rSleeve.w(); x++) {
					for (int z = 0; z < 4; z++) {
						this.c.top(this.rSleeve, x, z, argb(0x6A4A30));
					}
				}
				for (int s = 0; s < this.rSleeve.around(); s++) {
					this.c.around(this.rSleeve, s, 0, argb(0x6A4A30));
					this.c.around(this.rSleeve, s, 1, argb(0x5A3E26));
				}
				for (int y = 0; y < 9; y++) {
					this.c.front(j, 6 - y * 6 / 9, y, argb(0xB8A070));
				}
				dust(0x8A7458, 0.8F, false);
			}
			case CHARCOAL_BURNER -> {
				rollSleeves(o);
				dust(0x262220, 0.5F, false);
				stain(0x2A2624, 0.55F, 4);
				this.c.front(SkinCanvas.HEAD, 2, 6, argb(shade(this.skin, 0.6F)));
				this.c.front(SkinCanvas.HEAD, 5, 5, argb(shade(this.skin, 0.65F)));
				this.c.front(SkinCanvas.HEAD, 6, 6, argb(shade(this.skin, 0.62F)));
			}
			case KILN_WORKER -> {
				paintApron(0x6A4A34, false, false);
				rollSleeves(o);
				dust(0xB0583A, 0.72F, false);
			}
			case GLASSMAKER -> {
				paintApron(0x5A4030, true, true);
				rollSleeves(o);
				spectacles(0x3A3A3A);
			}
			case SMELTER -> {
				paintApron(0x3A2A20, true, true);
				rollSleeves(o);
				dust(0x3A3634, 0.7F, false);
				// heavy gloves
				stain(0x4A3424, 0.95F, 9);
			}
			case TANNER -> {
				paintApron(0x6A4A2E, true, true);
				spatter(0x4A3220, 0.55F);
				rollSleeves(o);
				stain(0x6A4A2A, 0.45F, 6);
			}
			case BUTCHER -> {
				paintApron(0xF0EEE8, true, true);
				spatter(0x9A1E1E, 0.6F);
				rollSleeves(o);
			}
			case CHANDLER -> {
				paintApron(0xE8DCC0, true, true);
				spatter(0xFAF6EA, 0.5F);
				rollSleeves(o);
			}
			case BEEKEEPER -> {
				// pale, close-woven clothes the bees leave alone, and gloves
				for (Part p : new Part[]{SkinCanvas.BODY, this.rArm, this.lArm}) {
					for (int s = 0; s < p.around(); s++) {
						for (int y = 0; y < (p == SkinCanvas.BODY ? 12 : 10); y++) {
							this.c.around(p, s, y, argb(jitter(0xEEE8D6, s + p.u(), y + 1750, 0.03F)));
						}
					}
				}
				stain(0xD8C890, 0.9F, 9);
				toolBelt(0x8A6A44, false);
			}
			case PAPERMAKER -> {
				paintApron(0xD8D2C2, true, true);
				rollSleeves(o);
				stain(0x2A3A5A, 0.5F, 10);
			}
			case DYER -> {
				int dye = this.dyes.accent()[(int) (this.noiseSeed >>> 15 & 0xFF) % this.dyes.accent().length];
				paintApron(0x8A7A60, true, true);
				spatter(dye, 0.45F);
				spatter(0x2A3A7A, 0.7F);
				rollSleeves(o);
				stain(mix(dye, 0x2A3A7A, 0.5F), 0.6F, 6);
			}
			case SALTER -> {
				rollSleeves(o);
				dust(0xF4F4F0, 0.6F, true);
			}
			case CLAY_DIGGER -> {
				rollSleeves(o);
				for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
					for (int s = 0; s < leg.around(); s++) {
						for (int y = 6; y < 12; y++) {
							int px = this.c.getAround(leg, s, y);
							this.c.around(leg, s, y, argb(mix(px & 0xFFFFFF, 0x7A6050, 0.55F + 0.2F * hash(s, y, 1760))));
						}
					}
				}
				stain(0x8A7060, 0.4F, 8);
			}
			case BOATWRIGHT -> {
				toolBelt(0x5A4030, true);
				rollSleeves(o);
				dust(0xE0C890, 0.84F, false);
			}
			case TRADER -> {
				paintCoat(pick(0x6A4A30, 0x5A4A3A, 0x3A4A3A), 0xC8A860, 11, true);
				for (int y = 0; y < 9; y++) {
					this.c.front(j, 1 + y * 6 / 9, y, argb(0x5A3A22));
				}
				this.c.left(j, 1, 9, argb(0x8A6440));
				this.c.left(j, 2, 9, argb(0x8A6440));
				this.c.left(j, 1, 10, argb(0x6A4A2E));
				this.c.left(j, 2, 10, argb(0x6A4A2E));
			}
			case HEALER -> {
				paintApron(pick(0x5E7A5A, 0x6A7A62), true, true);
				// a pouch of herbs at the hip
				this.c.front(j, 6, 9, argb(0x7A5A3A));
				this.c.front(j, 6, 10, argb(0x5A8A4A));
			}
			case ENTERTAINER -> {
				// motley: the body halved in two colours, the sleeves crossed
				int a = this.dyes.accent()[0];
				int b = this.dyes.accent().length > 1 ? this.dyes.accent()[1] : 0xE0C050;
				Part body2 = SkinCanvas.BODY;
				for (int s = 0; s < body2.around(); s++) {
					for (int y = 0; y < 12; y++) {
						this.c.around(body2, s, y, argb(jitter(s < body2.around() / 2 ? a : b, s, y + 1770, 0.03F)));
					}
				}
				for (int s = 0; s < this.rArm.around(); s++) {
					for (int y = 0; y < 10; y++) {
						this.c.around(this.rArm, s, y, argb(b));
						this.c.around(this.lArm, s, y, argb(a));
					}
				}
				for (int s = 0; s < SkinCanvas.RIGHT_LEG.around(); s++) {
					for (int y = 0; y < 10; y++) {
						this.c.around(SkinCanvas.RIGHT_LEG, s, y, argb(a));
						this.c.around(SkinCanvas.LEFT_LEG, s, y, argb(b));
					}
				}
			}
			case BATH_KEEPER -> {
				for (Part p : new Part[]{SkinCanvas.BODY, this.rArm, this.lArm}) {
					for (int s = 0; s < p.around(); s++) {
						for (int y = 0; y < (p == SkinCanvas.BODY ? 12 : 4); y++) {
							this.c.around(p, s, y, argb(jitter(0xF4F2EC, s + p.u(), y + 1780, 0.02F)));
						}
					}
				}
				// a towel over the shoulder
				for (int y = 0; y < 6; y++) {
					this.c.front(j, 5, y, argb(0xD8E0E8));
					this.c.back(j, 2, y, argb(0xD8E0E8));
				}
			}
			case STABLEMASTER -> {
				paintVest(0x6A4A30, 0x4A3220, false);
				// riding boots to the knee
				for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
					for (int s = 0; s < leg.around(); s++) {
						for (int y = 5; y < 12; y++) {
							this.c.around(leg, s, y, argb(noisy(y == 5 ? 0x5A3A22 : 0x3A2618, s, y + 1790)));
						}
					}
				}
				dust(0x8A7458, 0.84F, true);
			}
			case REEVE -> {
				paintCoat(pick(0x2E4A3A, 0x4A3A2A, 0x2E3A4A), 0xC8A860, 11, true);
				chainOfOffice(0xC8C8C0, false);
			}
			case JUDGE -> {
				paintRobe(0x1E1C20, 0x1E1C20, false);
				// the white bands at the throat
				this.c.front(j, 3, 0, argb(0xF6F4EE));
				this.c.front(j, 4, 0, argb(0xF6F4EE));
				this.c.front(j, 3, 1, argb(0xF6F4EE));
				this.c.front(j, 4, 1, argb(0xF6F4EE));
				this.c.front(j, 3, 2, argb(0xE8E6E0));
				this.c.front(j, 4, 2, argb(0xE8E6E0));
			}
			case JAILER -> {
				paintVest(0x5A4030, 0x3A2A1C, true);
				toolBelt(0x3A2A1C, false);
				// the keys at the belt
				this.c.front(j, 1, 9, argb(0x9A9A9A));
				this.c.front(j, 1, 10, argb(0x7A7A7A));
				this.c.front(j, 2, 10, argb(0xA8A8A8));
			}
			case FENCE -> {
				// nothing to see here: plain dark clothes and a cap pulled low
				paintVest(0x3A3632, 0x2A2622, true);
			}
			case STEWARD -> {
				paintCoat(pick(0x2A2A30, 0x3A2A2A, 0x2A3030), GOLD, 11, true);
				chainOfOffice(GOLD, false);
				this.c.left(j, 1, 9, argb(0x6A4A2E));
				this.c.left(j, 2, 9, argb(0x6A4A2E));
			}
			case LORD -> {
				// the station paints the mantle and chain; a gold belt clasp here
				this.c.front(j, 3, 8, argb(GOLD));
				this.c.front(j, 4, 8, argb(shade(GOLD, 0.8F)));
			}
			default -> {
			}
		}
	}

	private void toolBelt(int leather, boolean tools) {
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			this.c.around(j, s, 8, argb(leather));
			this.c.around(j, s, 9, argb(shade(leather, 0.85F)));
		}
		if (tools) {
			// A hammer at the right hip, a pouch at the left, a chisel behind.
			this.c.front(j, 0, 9, argb(0x8A8A8A));
			this.c.front(j, 0, 10, argb(0x6A5038));
			this.c.front(j, 1, 10, argb(0x6A5038));
			this.c.right(j, 3, 9, argb(0xA0A0A0));
			this.c.right(j, 3, 10, argb(0x7A7A7A));
			for (int x = 5; x <= 6; x++) {
				for (int y = 9; y <= 10; y++) {
					this.c.front(j, x, y, argb(shade(leather, y == 9 ? 1.1F : 0.95F)));
				}
			}
			this.c.back(j, 2, 9, argb(0x9A9A9A));
			this.c.front(j, 3, 8, argb(0xC8B070));
		}
	}

	private void rollSleeves(Outfit o) {
		if (o.sleeves != Sleeves.LONG) {
			return;
		}
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				for (int y = 6; y < 11; y++) {
					this.c.around(arm, s, y, argb(this.skin));
				}
				this.c.around(arm, s, 5, argb(mix(o.top, 0xFFFFFF, 0.15F)));
			}
		}
	}

	/** Specks of dust, flour, soot or sawdust on the lower clothes. */
	private void dust(int colour, float threshold, boolean legsOnly) {
		Part[] parts = legsOnly
			? new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}
			: new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG, SkinCanvas.BODY, this.rArm, this.lArm};
		for (Part p : parts) {
			for (int s = 0; s < p.around(); s++) {
				for (int y = 0; y < 12; y++) {
					int px = this.c.getAround(p, s, y);
					if (px != 0 && hash(p.u() + s, p.v() + y, 700) > threshold + 0.08F) {
						this.c.around(p, s, y, argb(mix(px & 0xFFFFFF, colour, 0.3F)));
					}
				}
			}
		}
	}

	private void paintCoat(int colour, int trim, int length, boolean buttons) {
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			Face side = SkinCanvas.sideOf(j, s);
			for (int y = 0; y < length; y++) {
				if (side == Face.FRONT) {
					int x = s - j.d();
					// Lapels open at the throat.
					if (y < 3 && (x == 3 || x == 4) || y == 0 && (x == 2 || x == 5)) {
						continue;
					}
				}
				this.c.around(j, s, y, argb(jitter(colour, s, y + 800, 0.03F)));
			}
			this.c.around(j, s, length - 1, argb(trim));
		}
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 4; z++) {
				if (!(z == 3 && (x == 3 || x == 4))) {
					this.c.top(j, x, z, argb(colour));
				}
			}
		}
		// Lapel edges and buttons.
		this.c.front(j, 2, 1, argb(trim));
		this.c.front(j, 5, 1, argb(trim));
		this.c.front(j, 2, 2, argb(shade(colour, 0.8F)));
		this.c.front(j, 5, 2, argb(shade(colour, 0.8F)));
		for (int y = 3; y < length - 1; y++) {
			this.c.front(j, 3, y, argb(shade(colour, 0.86F)));
		}
		if (buttons) {
			for (int y = 4; y < length - 1; y += 2) {
				this.c.front(j, 4, y, argb(trim));
			}
		}
		for (Part sleeve : new Part[]{this.rSleeve, this.lSleeve}) {
			for (int s = 0; s < sleeve.around(); s++) {
				for (int y = 0; y < 10; y++) {
					this.c.around(sleeve, s, y, argb(jitter(colour, s + sleeve.u(), y + 810, 0.03F)));
				}
				this.c.around(sleeve, s, 9, argb(trim));
			}
			for (int x = 0; x < sleeve.w(); x++) {
				for (int z = 0; z < 4; z++) {
					this.c.top(sleeve, x, z, argb(colour));
				}
			}
		}
	}

	private void paintRobe(int colour, int stole, boolean priest) {
		Part body = SkinCanvas.BODY;
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < body.around(); s++) {
			for (int y = 0; y < 12; y++) {
				this.c.around(body, s, y, argb(jitter(colour, s, y + 900, 0.03F)));
			}
		}
		this.c.front(body, 3, 0, argb(this.skin));
		this.c.front(body, 4, 0, argb(this.skin));
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				for (int y = 0; y < 10; y++) {
					this.c.around(arm, s, y, argb(jitter(colour, s + arm.u(), y + 910, 0.03F)));
				}
			}
		}
		// Wide sleeves on the outer layer.
		for (Part sleeve : new Part[]{this.rSleeve, this.lSleeve}) {
			for (int s = 0; s < sleeve.around(); s++) {
				for (int y = 5; y < 10; y++) {
					this.c.around(sleeve, s, y, argb(jitter(shade(colour, 0.95F), s + sleeve.u(), y + 920, 0.03F)));
				}
				this.c.around(sleeve, s, 9, argb(shade(colour, 0.85F)));
			}
		}
		for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
			for (int s = 0; s < leg.around(); s++) {
				for (int y = 0; y < 11; y++) {
					this.c.around(leg, s, y, argb(jitter(colour, s + leg.u(), y + 930, 0.03F)));
				}
			}
		}
		for (Part pants : new Part[]{SkinCanvas.RIGHT_PANTS, SkinCanvas.LEFT_PANTS}) {
			for (int s = 0; s < pants.around(); s++) {
				Face side = SkinCanvas.sideOf(pants, s);
				boolean inner = pants == SkinCanvas.RIGHT_PANTS ? side == Face.LEFT : side == Face.RIGHT;
				if (!inner) {
					for (int y = 0; y < 11; y++) {
						this.c.around(pants, s, y, argb(jitter(colour, s + pants.u(), y + 940, 0.03F)));
					}
					this.c.around(pants, s, 10, argb(shade(colour, 0.85F)));
				}
			}
		}
		// The rope belt.
		int rope = priest ? 0xC8B488 : this.dyes.trim();
		for (int s = 0; s < body.around(); s++) {
			this.c.around(body, s, 8, argb(s % 2 == 0 ? rope : shade(rope, 0.85F)));
		}
		if (priest) {
			// A stole hanging from the neck down the front, over robe and legs.
			int t = this.dyes.trim();
			for (int y = 0; y < 12; y++) {
				this.c.front(j, 2, y, argb(y == 11 ? t : stole));
				this.c.front(j, 5, y, argb(y == 11 ? t : stole));
			}
			this.c.front(j, 2, 5, argb(t));
			this.c.front(j, 5, 5, argb(t));
			for (int y = 0; y < 6; y++) {
				this.c.front(SkinCanvas.RIGHT_PANTS, 2, y, argb(y == 5 ? t : stole));
				this.c.front(SkinCanvas.LEFT_PANTS, 1, y, argb(y == 5 ? t : stole));
			}
			for (int z = 0; z < 4; z++) {
				this.c.top(j, 2, z, argb(stole));
				this.c.top(j, 5, z, argb(stole));
			}
		} else {
			// A scholar's satchel strap and book bag.
			for (int y = 0; y < 9; y++) {
				this.c.front(j, 1 + y * 6 / 9, y, argb(0x5A4030));
			}
			this.c.left(j, 1, 9, argb(0x7A5A3A));
			this.c.left(j, 2, 9, argb(0x7A5A3A));
			this.c.left(j, 1, 10, argb(0x6A4A2E));
			this.c.left(j, 2, 10, argb(0x6A4A2E));
		}
	}

	private void paintGuard(Outfit o) {
		Part j = SkinCanvas.JACKET;
		int mailA = 0x8E9094;
		int mailB = 0x6A6C70;
		// Mail under everything: body sides, upper arms.
		for (Part arm : new Part[]{this.rArm, this.lArm}) {
			for (int s = 0; s < arm.around(); s++) {
				for (int y = 0; y < 10; y++) {
					this.c.around(arm, s, y, argb((s + y) % 2 == 0 ? mailA : mailB));
				}
			}
		}
		for (Part sleeve : new Part[]{this.rSleeve, this.lSleeve}) {
			for (int s = 0; s < sleeve.around(); s++) {
				for (int y = 0; y < 5; y++) {
					this.c.around(sleeve, s, y, argb((s + y) % 2 == 0 ? mailA : mailB));
				}
				this.c.around(sleeve, s, 4, argb(0x5A5C60));
				this.c.around(sleeve, s, 9, argb(0x5A4030));
				this.c.around(sleeve, s, 8, argb(0x6A4A34));
			}
			for (int x = 0; x < sleeve.w(); x++) {
				for (int z = 0; z < 4; z++) {
					this.c.top(sleeve, x, z, argb(0xA0A2A6));
				}
			}
		}
		for (int s = 0; s < j.around(); s++) {
			for (int y = 0; y < 11; y++) {
				this.c.around(j, s, y, argb((s + y) % 2 == 0 ? mailA : mailB));
			}
		}
		// The tabard in the town's colour, front and back, with a device.
		int tab = this.dyes.accent()[0];
		int trim = this.dyes.trim();
		for (int x = 1; x <= 6; x++) {
			for (int y = 0; y < 12; y++) {
				int col = (x == 1 || x == 6 || y == 11) ? trim : jitter(tab, x, y + 1000, 0.03F);
				this.c.front(j, x, y, argb(col));
				this.c.back(j, x, y, argb(col));
			}
		}
		this.c.front(j, 3, 0, argb(this.skin));
		this.c.front(j, 4, 0, argb(this.skin));
		// The town's device: a chevron in the trim colour.
		for (int i = 0; i < 3; i++) {
			this.c.front(j, 2 + i, 5 - i, argb(trim));
			this.c.front(j, 5 - i, 5 - i, argb(trim));
			this.c.back(j, 2 + i, 5 - i, argb(trim));
			this.c.back(j, 5 - i, 5 - i, argb(trim));
		}
		for (int s = 0; s < j.around(); s++) {
			this.c.around(j, s, 8, argb(0x4A3424));
		}
		this.c.front(j, 3, 8, argb(0xB8B8B0));
		for (Part leg : new Part[]{SkinCanvas.RIGHT_LEG, SkinCanvas.LEFT_LEG}) {
			for (int s = 0; s < leg.around(); s++) {
				for (int y = 7; y < 12; y++) {
					this.c.around(leg, s, y, argb(y == 7 ? 0x6A4A34 : noisy(0x4A3424, s, y + 1010)));
				}
			}
		}
	}

	private void spectacles(int frame) {
		Part hat = SkinCanvas.HAT;
		this.c.front(hat, 0, 4, argb(frame));
		this.c.front(hat, 3, 4, argb(frame));
		this.c.front(hat, 4, 4, argb(frame));
		this.c.front(hat, 7, 4, argb(frame));
		this.c.front(hat, 1, 4, 0x40DDEEFF);
		this.c.front(hat, 2, 4, 0x40DDEEFF);
		this.c.front(hat, 5, 4, 0x40DDEEFF);
		this.c.front(hat, 6, 4, 0x40DDEEFF);
		this.c.right(hat, 6, 4, argb(frame));
		this.c.right(hat, 7, 4, argb(frame));
		this.c.left(hat, 0, 4, argb(frame));
		this.c.left(hat, 1, 4, argb(frame));
	}

	// ---------------------------------------------------------------- hats

	private void paintHeadwear(Outfit o) {
		Headwear hw = o.headwear;
		JobType job = this.look.job();
		if (this.look.station() == Station.LORD && this.look.age() != AgeStage.CHILD) {
			circlet();
			return;
		}
		switch (job) {
			case MINER -> {
				cap(0x5A4430, 0x3A2A1A, 1);
				// a candle on the brim to see by
				this.c.front(SkinCanvas.HAT, 3, 0, argb(0xF4ECD8));
				this.c.top(SkinCanvas.HAT, 3, 7, argb(0xF8C840));
				return;
			}
			case BEEKEEPER -> {
				veil();
				return;
			}
			case JUDGE -> {
				cap(0x1A1A1E, 0x1A1A1E, 2);
				return;
			}
			case SALTER -> {
				straw();
				return;
			}
			case TRADER -> {
				feltHat(pick(0x4A3A2A, 0x3A3230, 0x5A4632), 0xC8A860);
				return;
			}
			case ENTERTAINER -> {
				jesterHood();
				return;
			}
			case FENCE, CHARCOAL_BURNER -> {
				woolCap(0x3A3632);
				return;
			}
			case BUTCHER, CHANDLER -> {
				cap(0xF0EEE8, 0xE0DCD2, 1);
				return;
			}
			default -> {
			}
		}
		if (job == JobType.BUILDER && this.look.office() == Office.MASTER_BUILDER) {
			feltHat(0x5A4632, 0xC8A860);
			return;
		}
		if (job == JobType.GUARD && this.look.office() == Office.CAPTAIN) {
			helmet();
			plume(0xB01A2A);
			return;
		}
		if (this.look.station() == Station.GENTRY && this.look.age() != AgeStage.CHILD && (this.noiseSeed & 8) == 0) {
			feltHat(GENTRY[(int) (this.noiseSeed >>> 7 & 0xFF) % GENTRY.length], GOLD);
			this.c.left(SkinCanvas.HAT, 5, 0, argb(0xF2F0EA));
			this.c.left(SkinCanvas.HAT, 6, 0, argb(0xE0DCD0));
			return;
		}
		if (job == JobType.FARMER && this.look.age() != AgeStage.CHILD) {
			hw = Headwear.STRAW;
		} else if (job == JobType.BAKER) {
			cap(0xF4F2EC, 0xE0DCD2, 2);
			return;
		} else if (job == JobType.GUARD) {
			helmet();
			return;
		} else if (job == JobType.BUILDER || job == JobType.QUARRIER) {
			cap(pick(0x8A6A44, 0x6A5236, 0xA87A3A), 0x5A4430, 1);
			return;
		} else if (job == JobType.FISHER && (this.look.culture() == Culture.MARITIME || this.look.culture() == Culture.NORTHERN)) {
			cap(0xD9B03A, 0xB89020, 2);
			return;
		} else if (job == JobType.FORESTER && (this.noiseSeed & 2) == 0) {
			hood(pick(0x3E5B3A, 0x5A4632));
			return;
		}
		switch (hw) {
			case NONE -> {
			}
			case STRAW -> straw();
			case KERCHIEF -> kerchief(this.dyes.accent()[(int) (this.noiseSeed >>> 13 & 0xFF) % this.dyes.accent().length]);
			case FUR_HAT -> furHat();
			case WOOL_CAP -> woolCap(this.dyes.accent()[(int) (this.noiseSeed >>> 17 & 0xFF) % this.dyes.accent().length]);
			case WRAP -> wrap(pick(0xF0EBE0, this.dyes.accent()[0], 0xE8DCC2));
			case SCARF -> scarf(this.dyes.accent()[(int) (this.noiseSeed >>> 21 & 0xFF) % this.dyes.accent().length]);
			case BAND -> band(this.dyes.accent()[0]);
			case FELT_HAT -> feltHat(pick(0x3A3230, 0x5A4632, 0x2E3A3A), this.dyes.accent()[0]);
		}
	}

	private void straw() {
		Part hat = SkinCanvas.HAT;
		int straw = 0xD8C07A;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb((x + z) % 2 == 0 ? straw : shade(straw, 0.88F)));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 0, argb(s % 2 == 0 ? straw : shade(straw, 0.9F)));
			this.c.around(hat, s, 1, argb(0x8A3A2A));
			this.c.around(hat, s, 2, argb(s % 2 == 0 ? shade(straw, 0.95F) : mix(straw, 0xFFFFFF, 0.1F)));
		}
	}

	private void cap(int colour, int band, int rows) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(jitter(colour, x, z + 1100, 0.03F)));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			for (int y = 0; y < rows; y++) {
				this.c.around(hat, s, y, argb(jitter(colour, s, y + 1110, 0.03F)));
			}
			this.c.around(hat, s, rows, argb(band));
		}
	}

	private void helmet() {
		Part hat = SkinCanvas.HAT;
		int iron = 0xA4A6AA;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(x == 3 || x == 4 ? mix(iron, 0xFFFFFF, 0.25F) : iron));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 0, argb(iron));
			this.c.around(hat, s, 1, argb(shade(iron, 0.92F)));
			this.c.around(hat, s, 2, argb(shade(iron, 0.75F)));
		}
		// Rivets on the brim.
		for (int s = 1; s < hat.around(); s += 3) {
			this.c.around(hat, s, 2, argb(0xD0D0D0));
		}
		// Mail coif at the back and sides.
		for (int z = 0; z < 5; z++) {
			for (int y = 3; y < 8; y++) {
				this.c.right(hat, z, y, argb((z + y) % 2 == 0 ? 0x8E9094 : 0x6A6C70));
				this.c.left(hat, 7 - z, y, argb((z + y) % 2 == 0 ? 0x8E9094 : 0x6A6C70));
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int y = 3; y < 8; y++) {
				this.c.back(hat, x, y, argb((x + y) % 2 == 0 ? 0x8E9094 : 0x6A6C70));
			}
		}
	}

	private void kerchief(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(cloth(colour, hat, x, z, this.dyes.pattern() == Pattern.FLORAL ? Pattern.FLORAL : Pattern.PLAIN)));
			}
			this.c.front(hat, x, 0, argb(colour));
			if (x != 3 && x != 4) {
				this.c.front(hat, x, 1, argb(shade(colour, 0.9F)));
			}
		}
		for (int z = 0; z < 8; z++) {
			for (int y = 0; y < 3; y++) {
				this.c.right(hat, z, y, argb(colour));
				this.c.left(hat, z, y, argb(colour));
			}
		}
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 4; y++) {
				this.c.back(hat, x, y, argb(colour));
			}
		}
		// The knot at the nape.
		this.c.back(hat, 3, 4, argb(shade(colour, 0.85F)));
		this.c.back(hat, 4, 4, argb(shade(colour, 0.85F)));
		this.c.back(hat, 3, 5, argb(shade(colour, 0.78F)));
	}

	private void scarf(int colour) {
		kerchief(colour);
		Part hat = SkinCanvas.HAT;
		// A headscarf covers the hair entirely and frames the face.
		for (int y = 0; y < 8; y++) {
			this.c.front(hat, 0, y, argb(colour));
			this.c.front(hat, 7, y, argb(colour));
			for (int z = 0; z < 8; z++) {
				this.c.right(hat, z, y, argb(jitter(colour, z, y + 1200, 0.03F)));
				this.c.left(hat, z, y, argb(jitter(colour, z, y + 1210, 0.03F)));
			}
			for (int x = 0; x < 8; x++) {
				this.c.back(hat, x, y, argb(jitter(colour, x, y + 1220, 0.03F)));
			}
		}
		for (int x = 1; x < 7; x++) {
			this.c.front(hat, x, 1, argb(colour));
		}
		this.c.front(hat, 0, 7, argb(shade(colour, 0.85F)));
		this.c.front(hat, 7, 7, argb(shade(colour, 0.85F)));
	}

	private void furHat() {
		Part hat = SkinCanvas.HAT;
		int fur = this.look.culture() == Culture.TUNDRA ? pick(0xE6DED0, 0x8A6A4A) : pick(0x7A5A3C, 0x5A4632, 0xCDBFA8);
		int crown = this.look.culture() == Culture.TUNDRA ? this.dyes.accent()[0] : shade(fur, 0.8F);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(noisy(crown, x, z + 1300)));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			for (int y = 0; y < 3; y++) {
				this.c.around(hat, s, y, argb(noisy(y == 0 ? crown : fur, s, y + 1310)));
			}
			if (hash(s, 3, 1320) > 0.2F) {
				this.c.around(hat, s, 3, argb(noisy(shade(fur, 0.9F), s, 1330)));
			}
		}
		// Ear flaps.
		for (int y = 3; y < 7; y++) {
			for (int z = 2; z < 6; z++) {
				this.c.right(hat, z, y, argb(noisy(fur, z, y + 1340)));
				this.c.left(hat, z, y, argb(noisy(fur, z, y + 1350)));
			}
		}
	}

	private void woolCap(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(x % 2 == 0 ? colour : shade(colour, 0.88F)));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 0, argb(s % 2 == 0 ? colour : shade(colour, 0.88F)));
			this.c.around(hat, s, 1, argb(shade(colour, 0.8F)));
			this.c.around(hat, s, 2, argb(s % 2 == 0 ? shade(colour, 0.85F) : shade(colour, 0.75F)));
		}
		this.c.top(hat, 3, 3, argb(mix(colour, 0xFFFFFF, 0.3F)));
		this.c.top(hat, 4, 4, argb(mix(colour, 0xFFFFFF, 0.3F)));
	}

	private void wrap(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb((x + z) % 3 == 0 ? shade(colour, 0.88F) : colour));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			for (int y = 0; y < 3; y++) {
				// Diagonal folds of wound cloth.
				int col = (s + y * 2) % 4 == 0 ? shade(colour, 0.84F) : colour;
				this.c.around(hat, s, y, argb(jitter(col, s, y + 1400, 0.02F)));
			}
		}
		// A tail of cloth hanging behind.
		for (int y = 3; y < 8; y++) {
			this.c.back(hat, 5, y, argb(shade(colour, 0.9F)));
			this.c.back(hat, 6, y, argb(colour));
		}
	}

	private void band(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 1, argb(colour));
		}
		this.c.back(hat, 3, 2, argb(colour));
		this.c.back(hat, 3, 3, argb(shade(colour, 0.85F)));
	}

	private void feltHat(int colour, int ribbon) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				boolean dent = x >= 3 && x <= 4 && z >= 2 && z <= 5;
				this.c.top(hat, x, z, argb(dent ? shade(colour, 0.8F) : colour));
			}
		}
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 0, argb(colour));
			this.c.around(hat, s, 1, argb(ribbon));
			this.c.around(hat, s, 2, argb(shade(colour, 0.88F)));
		}
		if (this.look.culture() == Culture.ALPINE) {
			// A feather in the band.
			this.c.left(hat, 5, 0, argb(0x4A6A3A));
			this.c.left(hat, 6, 0, argb(0x3A3A3A));
			this.c.left(hat, 6, 1, argb(0xEAE6DA));
		}
	}

	/** The lord's gold circlet, set with a stone at the brow; the hair shows beneath. */
	private void circlet() {
		Part hat = SkinCanvas.HAT;
		for (int s = 0; s < hat.around(); s++) {
			this.c.around(hat, s, 1, argb(s % 2 == 0 ? GOLD : shade(GOLD, 0.82F)));
		}
		for (int x = 1; x < 7; x += 2) {
			this.c.front(hat, x, 0, argb(GOLD));
		}
		this.c.front(hat, 3, 1, argb(0xB01A2A));
		this.c.front(hat, 4, 1, argb(0x80101A));
	}

	/** A wide hat with a veil of fine mesh before the face. */
	private void veil() {
		Part hat = SkinCanvas.HAT;
		straw();
		for (int y = 3; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				this.c.front(hat, x, y, 0x50303030);
				this.c.back(hat, x, y, 0x50303030);
			}
			for (int z = 0; z < 8; z++) {
				this.c.right(hat, z, y, 0x50303030);
				this.c.left(hat, z, y, 0x50303030);
			}
		}
	}

	/** A plume rising from the helmet's crown. */
	private void plume(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int z = 2; z < 6; z++) {
			this.c.top(hat, 3, z, argb(colour));
			this.c.top(hat, 4, z, argb(shade(colour, 0.8F)));
		}
		this.c.back(hat, 3, 0, argb(colour));
		this.c.back(hat, 4, 0, argb(shade(colour, 0.8F)));
	}

	/** A two-coloured hood with points. */
	private void jesterHood() {
		int a = this.dyes.accent()[0];
		int b = this.dyes.accent().length > 1 ? this.dyes.accent()[1] : 0xE0C050;
		hood(a);
		Part hat = SkinCanvas.HAT;
		for (int z = 0; z < 8; z++) {
			for (int y = 0; y < 8; y++) {
				this.c.left(hat, z, y, argb(jitter(b, z, y + 1800, 0.03F)));
			}
		}
		// the bells
		this.c.right(hat, 0, 0, argb(0xE8D060));
		this.c.left(hat, 7, 0, argb(0xE8D060));
	}

	private void hood(int colour) {
		Part hat = SkinCanvas.HAT;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				this.c.top(hat, x, z, argb(jitter(colour, x, z + 1500, 0.03F)));
			}
			for (int y = 0; y < 8; y++) {
				this.c.back(hat, x, y, argb(jitter(colour, x, y + 1510, 0.03F)));
			}
			this.c.front(hat, x, 0, argb(shade(colour, 0.85F)));
		}
		for (int z = 0; z < 8; z++) {
			for (int y = 0; y < 8; y++) {
				this.c.right(hat, z, y, argb(jitter(colour, z, y + 1520, 0.03F)));
				this.c.left(hat, z, y, argb(jitter(colour, z, y + 1530, 0.03F)));
			}
		}
		for (int y = 1; y < 8; y++) {
			this.c.front(hat, 0, y, argb(shade(colour, 0.85F)));
			this.c.front(hat, 7, y, argb(shade(colour, 0.85F)));
		}
		// The hood drapes onto the shoulders.
		Part j = SkinCanvas.JACKET;
		for (int s = 0; s < j.around(); s++) {
			if (SkinCanvas.sideOf(j, s) != Face.FRONT) {
				this.c.around(j, s, 0, argb(colour));
				if (SkinCanvas.sideOf(j, s) == Face.BACK) {
					this.c.around(j, s, 1, argb(shade(colour, 0.92F)));
					this.c.around(j, s, 2, argb(shade(colour, 0.88F)));
				}
			}
		}
	}

	// ---------------------------------------------------------------- age

	private void paintAge(Outfit o) {
		if (this.look.age() != AgeStage.ELDER) {
			return;
		}
		Part h = SkinCanvas.HEAD;
		int line = shade(this.skin, 0.88F);
		// Crow's feet, smile lines, a furrowed brow.
		this.c.front(h, 0, 4, argb(line));
		this.c.front(h, 7, 4, argb(line));
		if (this.facial < 3) {
			this.c.front(h, 2, 6, argb(line));
			this.c.front(h, 5, 6, argb(line));
		}
		if (this.c.getFront(h, 3, 2) == 0 || (this.c.getFront(h, 3, 2) & 0xFFFFFF) == (jitter(this.skin, 11, 10, 0.015F))) {
			this.c.front(h, 3, 2, argb(shade(this.skin, 0.93F)));
			this.c.front(h, 4, 2, argb(shade(this.skin, 0.93F)));
		}
		// Shawls for the women and cardigans for the men keep the cold off, unless a trade dresses them.
		if (this.look.job() == JobType.NONE) {
			if (this.look.female()) {
				paintShawl(pick(0x8A6A7A, 0x6A7A8A, 0x8A7A5A, this.dyes.accent()[0]), 0xD8D0C0);
			} else if ((this.noiseSeed & 4) == 0) {
				paintVest(pick(0x6A5A4A, 0x4E5A4A, 0x5A4E5E), 0xC8B070, true);
			}
		}
	}

	// ---------------------------------------------------------------- colour helpers

	private int pick(int... options) {
		return options[(int) Long.remainderUnsigned(this.noiseSeed >>> 24, options.length)];
	}

	private int noisy(int colour, int x, int y) {
		return jitter(colour, x, y, 0.05F);
	}

	/** Small deterministic brightness variation, the weave of cloth or the grain of skin. */
	private int jitter(int colour, int x, int y, float amount) {
		return shade(colour, 1.0F + hash(x, y, 1) * amount);
	}

	/** Deterministic value in [-1, 1] for a pixel. */
	private float hash(int x, int y, int salt) {
		long h = this.noiseSeed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return (float) ((h >>> 11) * 0x1.0p-53 * 2 - 1);
	}

	static int argb(int rgb) {
		return OPAQUE | (rgb & 0xFFFFFF);
	}

	static int shade(int rgb, float f) {
		int r = Math.clamp(Math.round((rgb >> 16 & 0xFF) * f), 0, 255);
		int g = Math.clamp(Math.round((rgb >> 8 & 0xFF) * f), 0, 255);
		int b = Math.clamp(Math.round((rgb & 0xFF) * f), 0, 255);
		return r << 16 | g << 8 | b;
	}

	static int mix(int a, int b, float t) {
		int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
		int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	static float luma(int rgb) {
		return ((rgb >> 16 & 0xFF) * 0.299F + (rgb >> 8 & 0xFF) * 0.587F + (rgb & 0xFF) * 0.114F) / 255.0F;
	}

	// ---------------------------------------------------------------- choices

	enum Hair {
		SHORT, SIDE_PART, TOUSLED, CURLY, BUZZ, RECEDING, BALD, TOPKNOT, LONG, FRINGE, BOB, PONYTAIL, BUN, BRAID, TWO_BRAIDS;

		private static final Hair[] MEN = {SHORT, SHORT, SIDE_PART, SIDE_PART, TOUSLED, CURLY, BUZZ, PONYTAIL, LONG, SHORT, TOPKNOT,
			SIDE_PART, CURLY, TOUSLED, SHORT, BUZZ};
		private static final Hair[] WOMEN = {LONG, LONG, BUN, PONYTAIL, BRAID, TWO_BRAIDS, BOB, FRINGE, LONG, BUN, LONG, PONYTAIL,
			BRAID, BOB, CURLY, BUN};
		private static final Hair[] BOYS = {SHORT, TOUSLED, CURLY, FRINGE, SHORT, TOUSLED, BUZZ, SIDE_PART};
		private static final Hair[] GIRLS = {TWO_BRAIDS, PONYTAIL, LONG, BOB, FRINGE, TWO_BRAIDS, BRAID, LONG};

		static Hair choose(FolkLook look, int gene, int detail) {
			boolean eastern = look.culture() == Culture.KARST || look.culture() == Culture.WISTERIA;
			if (look.age() == AgeStage.CHILD) {
				return (look.female() ? GIRLS : BOYS)[gene & 7];
			}
			if (look.female()) {
				if (look.age() == AgeStage.ELDER) {
					return (gene & 3) == 0 ? CURLY : (gene & 3) == 1 ? BOB : BUN;
				}
				return WOMEN[gene];
			}
			if (look.age() == AgeStage.ELDER) {
				int r = gene % 5;
				if (r == 0) {
					return BALD;
				}
				if (r == 1) {
					return RECEDING;
				}
			}
			Hair h = MEN[gene];
			// Topknots are a karst and wisteria custom; elsewhere that slot is a plain crop.
			if (h == TOPKNOT && !eastern) {
				return SHORT;
			}
			if (eastern && (detail & 7) == 0) {
				return TOPKNOT;
			}
			return h;
		}
	}

	enum Neck {
		V, ROUND, CROSS, SIDE, HIGH
	}

	enum LegKind {
		TROUSERS, SHORTS, SKIRT, ROBE
	}

	/** The clothes a folk wears before their trade adds to them. */
	static final class Outfit {
		int top;
		int legs;
		Pattern topPattern = Pattern.PLAIN;
		Pattern legPattern = Pattern.PLAIN;
		Neck neck = Neck.ROUND;
		Sleeves sleeves = Sleeves.LONG;
		LegKind legKind = LegKind.TROUSERS;
		int skirtLength = 12;
		int hem;
		int stockings;
		int shoes;
		boolean boots;
		boolean sandals;
		boolean furBoots;
		boolean furCollar;
		boolean buttons;
		int belt;
		int sash;
		int vest;
		int shawl;
		int apron;
		boolean apronStraps;
		int suspenders;
		int bodice;
		int underCollar = 0xEEEAE0;
		int trimColour;
		Headwear headwear = Headwear.NONE;

		static Outfit choose(FolkLook look, Dyes d, Rng r) {
			Outfit o = new Outfit();
			boolean fem = look.female();
			boolean child = look.age() == AgeStage.CHILD;
			o.top = d.cloth()[r.nextInt(d.cloth().length)];
			o.legs = d.dark()[r.nextInt(d.dark().length)];
			o.trimColour = d.trim();
			o.sleeves = d.sleeves();
			o.shoes = shade(d.leather(), 0.75F);
			o.belt = d.leather();
			o.topPattern = d.pattern() == Pattern.SASH ? Pattern.PLAIN : d.pattern();
			if (o.topPattern == Pattern.CHECK && r.nextInt(3) != 0) {
				// Check is for skirts, kilts and shawls more than shirts.
				o.topPattern = Pattern.PLAIN;
			}
			o.neck = r.nextBoolean() ? Neck.V : Neck.ROUND;
			o.buttons = r.nextInt(3) == 0;
			o.headwear = child ? Headwear.NONE : d.headwear()[r.nextInt(d.headwear().length)];
			switch (look.culture()) {
				case TEMPERATE -> {
					if (fem) {
						dress(o, d, r, 11);
						if (r.nextInt(3) == 0) {
							o.apron = 0xEEE8DA;
							o.apronStraps = true;
						}
					} else if (r.nextInt(3) == 0) {
						o.vest = d.dark()[r.nextInt(d.dark().length)];
					}
					o.boots = !fem && r.nextBoolean();
				}
				case WISTERIA, KARST -> {
					o.neck = Neck.CROSS;
					o.sash = d.dark()[r.nextInt(d.dark().length)];
					o.buttons = false;
					if (look.culture() == Culture.WISTERIA) {
						o.top = r.nextBoolean() ? d.accent()[r.nextInt(d.accent().length)] : o.top;
						o.underCollar = 0xF4EEE2;
						if (fem || r.nextInt(3) == 0) {
							// A robe to the ankles.
							o.legs = o.top;
							o.legKind = LegKind.ROBE;
							o.skirtLength = 11;
							o.topPattern = r.nextInt(3) == 0 ? Pattern.FLORAL : Pattern.PLAIN;
							o.legPattern = o.topPattern;
							o.sash = d.accent()[r.nextInt(d.accent().length)];
							if (o.sash == o.top) {
								o.sash = d.dark()[0];
							}
						}
					} else {
						if (fem && r.nextBoolean()) {
							o.legKind = LegKind.SKIRT;
							o.skirtLength = 11;
							o.legs = d.accent()[r.nextInt(d.accent().length)];
						}
					}
					o.sandals = r.nextInt(3) == 0;
				}
				case NORTHERN -> {
					o.belt = d.leather();
					o.boots = true;
					if (fem) {
						dress(o, d, r, 11);
						// An apron dress held by two brooches.
						o.apron = d.accent()[r.nextInt(d.accent().length)];
						o.apronStraps = true;
					}
					o.furCollar = r.nextInt(3) == 0;
				}
				case ALPINE -> {
					o.top = 0xF2EEE4;
					if (fem) {
						// A dirndl: white blouse with short sleeves, a coloured bodice, skirt and apron.
						o.sleeves = Sleeves.SHORT;
						o.vest = d.accent()[r.nextInt(d.accent().length)];
						dress(o, d, r, 9);
						o.apron = d.accent()[(r.nextInt(d.accent().length))];
						if (o.apron == o.vest) {
							o.apron = 0xEEE6D6;
						}
						o.stockings = 0xEDE8DC;
					} else {
						o.legKind = LegKind.SHORTS;
						o.legs = 0x6B4A2E;
						o.suspenders = 0x5A3E26;
						o.stockings = r.nextBoolean() ? 0xD8D2C4 : 0x6E7A5E;
						o.belt = 0;
						if (r.nextInt(3) == 0) {
							o.vest = d.dark()[r.nextInt(d.dark().length)];
							o.suspenders = 0;
						}
					}
					o.boots = false;
				}
				case TUNDRA -> {
					o.topPattern = Pattern.EMBROIDERY;
					o.top = d.cloth()[r.nextInt(2)];
					o.furBoots = true;
					o.boots = true;
					o.shoes = 0x6A4C36;
					o.belt = d.leather();
					o.furCollar = r.nextInt(3) != 0;
					if (fem) {
						dress(o, d, r, 10);
						o.legs = o.top;
						o.hem = d.accent()[0];
					}
				}
				case MOORLAND -> {
					if (fem) {
						dress(o, d, r, 11);
						o.legPattern = Pattern.CHECK;
						if (r.nextBoolean()) {
							o.shawl = d.accent()[r.nextInt(d.accent().length)];
						}
					} else if (r.nextInt(3) == 0) {
						// A kilt of the family check.
						o.legKind = LegKind.SKIRT;
						o.skirtLength = 7;
						o.legs = d.accent()[r.nextInt(d.accent().length)];
						o.legPattern = Pattern.CHECK;
						o.stockings = 0xCFC2A0;
					}
					o.boots = true;
				}
				case MARITIME -> {
					if (r.nextInt(3) != 0) {
						o.top = 0xEDEAE2;
						o.topPattern = Pattern.STRIPES;
					} else {
						// A navy fisherman's jumper.
						o.top = 0x2E3F5E;
						o.topPattern = Pattern.PLAIN;
						o.neck = Neck.HIGH;
					}
					if (fem) {
						dress(o, d, r, 11);
					}
					o.boots = r.nextBoolean();
				}
				case TROPICAL -> {
					o.topPattern = r.nextInt(3) == 0 ? Pattern.PLAIN : Pattern.FLORAL;
					o.sleeves = Sleeves.SHORT;
					o.sandals = true;
					o.shoes = 0x7A5A3A;
					o.belt = 0;
					if (fem) {
						// A wrap skirt.
						o.legKind = LegKind.SKIRT;
						o.skirtLength = 10;
						o.legs = d.accent()[r.nextInt(d.accent().length)];
						o.legPattern = r.nextBoolean() ? Pattern.FLORAL : Pattern.PLAIN;
					} else {
						o.legKind = r.nextBoolean() ? LegKind.SHORTS : LegKind.TROUSERS;
						o.legs = d.dark()[r.nextInt(d.dark().length)];
					}
				}
				case DESERT -> {
					// A long robe over trousers, and a sash.
					o.top = d.cloth()[r.nextInt(d.cloth().length)];
					o.legs = o.top;
					o.legKind = LegKind.ROBE;
					o.skirtLength = fem ? 11 : 10;
					o.neck = r.nextBoolean() ? Neck.V : Neck.HIGH;
					o.sash = d.accent()[r.nextInt(d.accent().length)];
					o.sandals = true;
					o.shoes = 0x8A5E36;
					o.hem = r.nextBoolean() ? d.trim() : 0;
				}
				case MEDITERRANEAN -> {
					if (fem) {
						dress(o, d, r, 11);
						if (r.nextBoolean()) {
							o.apron = 0xF2EEE4;
						}
					} else {
						o.sash = r.nextInt(3) == 0 ? d.accent()[0] : 0;
					}
					o.sandals = r.nextInt(3) == 0;
				}
				case STEPPE -> {
					// A deel: a robe closing at the side, sashed.
					o.neck = Neck.SIDE;
					o.top = d.cloth()[r.nextInt(d.cloth().length)];
					o.legs = o.top;
					o.legKind = LegKind.ROBE;
					o.skirtLength = 9;
					o.sash = d.accent()[r.nextInt(d.accent().length)];
					if (o.sash == o.top) {
						o.sash = d.trim();
					}
					o.boots = true;
					o.buttons = false;
				}
				case BAYOU -> {
					if (fem) {
						dress(o, d, r, 11);
						o.legPattern = r.nextBoolean() ? Pattern.CHECK : Pattern.PLAIN;
						if (r.nextBoolean()) {
							o.apron = 0xEAE2CE;
						}
					} else {
						o.suspenders = r.nextBoolean() ? 0x4A3A2A : 0;
						if (o.suspenders != 0) {
							o.belt = 0;
						}
					}
				}
				case MESA -> {
					o.topPattern = Pattern.PLAIN;
					o.sash = 0;
					o.belt = d.accent()[r.nextInt(d.accent().length)];
					if (fem) {
						dress(o, d, r, 11);
						o.legPattern = Pattern.WOVEN;
						o.hem = d.accent()[r.nextInt(d.accent().length)];
						if (r.nextBoolean()) {
							o.shawl = d.accent()[r.nextInt(d.accent().length)];
						}
					}
					o.boots = r.nextBoolean();
				}
			}
			if (child) {
				o.vest = 0;
				o.apron = 0;
				o.shawl = 0;
				o.furCollar = false;
				o.sleeves = o.sleeves == Sleeves.LONG && r.nextBoolean() ? Sleeves.ROLLED : o.sleeves;
			}
			if (o.legKind == LegKind.TROUSERS && o.legs == o.top) {
				o.legs = d.dark()[0];
			}
			return o;
		}

		private static void dress(Outfit o, Dyes d, Rng r, int length) {
			o.legKind = LegKind.SKIRT;
			o.skirtLength = length;
			int skirt = r.nextBoolean() ? d.dark()[r.nextInt(d.dark().length)] : d.accent()[r.nextInt(d.accent().length)];
			o.legs = skirt;
			o.buttons = false;
			o.hem = shade(skirt, 0.78F);
			if (r.nextInt(3) != 0) {
				o.bodice = skirt;
				o.top = r.nextInt(3) == 0 ? d.cloth()[r.nextInt(d.cloth().length)] : 0xEEE8DA;
			}
			o.neck = r.nextBoolean() ? Neck.ROUND : Neck.V;
			o.belt = 0;
			o.sash = 0;
		}
	}

	/** SplitMix64: tiny, fast, and the same everywhere. */
	static final class Rng {
		private long state;

		Rng(long seed) {
			this.state = seed;
		}

		long nextLong() {
			long z = (this.state += 0x9E3779B97F4A7C15L);
			z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
			z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
			return z ^ (z >>> 31);
		}

		int nextInt(int bound) {
			return (int) Long.remainderUnsigned(nextLong(), bound);
		}

		boolean nextBoolean() {
			return (nextLong() & 1) != 0;
		}
	}
}
