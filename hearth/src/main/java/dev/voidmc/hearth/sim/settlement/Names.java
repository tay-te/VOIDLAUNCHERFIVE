package dev.voidmc.hearth.sim.settlement;

import dev.voidmc.hearth.api.Culture;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * Settlement names that sound like their land, and the plain personal names of the default resident factory (the NPC
 * module brings richer ones). Names are built from a stem and an ending chosen per culture, so the same random gives the
 * same name: histories are deterministic down to what the town is called.
 */
public final class Names {
	private record Parts(String[] stems, String[] endings, String[] given, String[] givenF, String[] family) {
	}

	private static final Map<Culture, Parts> PARTS = new EnumMap<>(Culture.class);

	static {
		Parts temperate = new Parts(
			new String[]{"Ash", "Oak", "Bram", "Elm", "Wil", "Hart", "Thorn", "Mill", "Kings", "Bel", "Cran", "Hol", "Alder", "Mar", "Wick", "Fen",
				"Stan", "Lang", "Brad", "Whit"},
			new String[]{"brook", "ford", "ley", "ton", "wick", "field", "bury", "stead", "combe", "dale", "worth", "well", "ham", "hurst"},
			new String[]{"Tom", "Will", "Hugh", "Walter", "Robin", "Giles", "Roger", "Edmund", "Piers", "Simon", "Adam", "Osric"},
			new String[]{"Alys", "Maud", "Joan", "Edith", "Agnes", "Isobel", "Rose", "Mabel", "Cecily", "Emma", "Matilda", "Elena"},
			new String[]{"Miller", "Cooper", "Thatcher", "Fletcher", "Brook", "Ashdown", "Hale", "Ward", "Carter", "Wheeler", "Mason", "Fairweather"});
		PARTS.put(Culture.TEMPERATE, temperate);
		PARTS.put(Culture.WISTERIA, new Parts(
			new String[]{"Lilac", "Violet", "Blossom", "Petal", "Mauve", "Silk", "Lantern", "Willow", "Plum", "Iris"},
			new String[]{"vale", "mere", "bridge", "garden", "bower", "lea", "brook", "canal"},
			new String[]{"Laurent", "Felix", "Oren", "Silas", "Julian", "Cyril", "Ambrose", "Lucan"},
			new String[]{"Wisteria", "Lilac", "Violet", "Iris", "Clara", "Celeste", "Delphine", "Rosalind"},
			new String[]{"Vane", "Lisle", "Arbor", "Bellamy", "Morrow", "Sable", "Hollis", "Dewer"}));
		Parts northern = new Parts(
			new String[]{"Bjorn", "Ulf", "Rav", "Ask", "Grim", "Hrafn", "Sig", "Tor", "Frey", "Eik", "Stav", "Vald"},
			new String[]{"stad", "heim", "vik", "holt", "by", "garth", "fell", "skog", "hall", "dal"},
			new String[]{"Bjorn", "Leif", "Erik", "Ivar", "Torvald", "Ulf", "Halvard", "Ragnar", "Sven", "Arne"},
			new String[]{"Astrid", "Sigrid", "Ingrid", "Freya", "Gunnhild", "Solveig", "Ylva", "Runa", "Helga", "Tove"},
			new String[]{"Eriksen", "Halvorsen", "Stav", "Holt", "Ravn", "Lund", "Berg", "Dahl", "Strand", "Moe"});
		PARTS.put(Culture.NORTHERN, northern);
		PARTS.put(Culture.TUNDRA, new Parts(
			new String[]{"Frost", "Ice", "Snow", "Winter", "Rime", "White", "Hoar", "Glacier", "Pale", "Storm"},
			new String[]{"hold", "steading", "fast", "hearth", "hall", "watch", "fell", "reach"},
			northern.given, northern.givenF, northern.family));
		PARTS.put(Culture.ALPINE, new Parts(
			new String[]{"Edel", "Grun", "Hoch", "Alp", "Stein", "Wald", "Sonn", "Berg", "Matt", "Zir"},
			new String[]{"matt", "egg", "alp", "berg", "dorf", "horn", "wald", "tal", "bach"},
			new String[]{"Anselm", "Jakob", "Matthias", "Lukas", "Florian", "Gregor", "Kaspar", "Urs"},
			new String[]{"Anna", "Heidi", "Greta", "Liesl", "Vreni", "Ursula", "Frieda", "Marta"},
			new String[]{"Huber", "Steiner", "Berger", "Gruber", "Moser", "Brunner", "Zimmer", "Ammann"}));
		PARTS.put(Culture.MOORLAND, new Parts(
			new String[]{"Heather", "Cairn", "Black", "Grey", "Tor", "Peat", "Rook", "Crag", "Gorse", "Bracken"},
			new String[]{"moor", "tor", "cairn", "fold", "gill", "thwaite", "beck", "howe"},
			new String[]{"Ewan", "Angus", "Duncan", "Callum", "Rory", "Fergus", "Iain", "Malcolm"},
			new String[]{"Morag", "Isla", "Fiona", "Ailsa", "Kirsty", "Mhairi", "Elspeth", "Shona"},
			new String[]{"Moor", "Cairns", "Heath", "Craig", "Rook", "Shaw", "Fell", "Lindsay"}));
		PARTS.put(Culture.MARITIME, new Parts(
			new String[]{"Gull", "Salt", "Anchor", "Tide", "Brine", "Shell", "Skerry", "Wave", "Herring", "Fog"},
			new String[]{"haven", "port", "quay", "harbour", "cove", "strand", "ness", "mouth"},
			new String[]{"Fitz", "Morgan", "Bran", "Tristan", "Corin", "Jago", "Petroc", "Kenan"},
			new String[]{"Morwenna", "Tamsin", "Demelza", "Kerensa", "Elowen", "Senara", "Wenna", "Lowenna"},
			new String[]{"Tregar", "Penrose", "Polglaze", "Nancarrow", "Trelawny", "Rowe", "Hocking", "Pascoe"}));
		PARTS.put(Culture.TROPICAL, new Parts(
			new String[]{"Palm", "Coral", "Lagoon", "Reef", "Mango", "Coco", "Turtle", "Pearl", "Kapok", "Orchid"},
			new String[]{" Bay", " Cay", " Point", "landing", " Reach", "shore", " Sands", "water"},
			new String[]{"Kai", "Tane", "Rafael", "Mateo", "Ari", "Koa", "Teo", "Nalu"},
			new String[]{"Leilani", "Maile", "Ana", "Isla", "Moana", "Lani", "Marisol", "Kalea"},
			new String[]{"Reyes", "Palma", "Costa", "Marino", "Kealoha", "Santos", "Mar", "Coral"}));
		PARTS.put(Culture.DESERT, new Parts(
			new String[]{"Zar", "Qas", "Al-Rim", "Bahr", "Tamar", "Sahel", "Ras", "Wadi", "Nakh", "Saf"},
			new String[]{"ad", "ir", "an", "abad", "ara", "ia", "un", "at"},
			new String[]{"Tariq", "Amir", "Rashid", "Farid", "Karim", "Idris", "Nadir", "Samir"},
			new String[]{"Layla", "Yasmin", "Samira", "Nadia", "Amira", "Zahra", "Leila", "Rania"},
			new String[]{"al-Rashid", "Hamdan", "Qasim", "Safar", "Najjar", "Haddad", "Khoury", "Mansour"}));
		PARTS.put(Culture.MEDITERRANEAN, new Parts(
			new String[]{"Monte", "Santa ", "Castel", "Rocca", "Olive", "Villa", "Porto", "Serra", "Cala", "Bella"},
			new String[]{"rosa", "verde", "nova", "fiore", "chiara", "alta", "sole", "mare"},
			new String[]{"Marco", "Luca", "Paolo", "Matteo", "Nico", "Dario", "Enzo", "Pietro"},
			new String[]{"Sofia", "Giulia", "Chiara", "Lucia", "Elena", "Marta", "Rosa", "Alba"},
			new String[]{"Rossi", "Oliveri", "Serra", "Monti", "Russo", "Ferrante", "Costa", "Marino"}));
		PARTS.put(Culture.STEPPE, new Parts(
			new String[]{"Kara", "Aksu", "Altyn", "Sary", "Tal", "Bay", "Ordu", "Kok", "Ulan", "Tash"},
			new String[]{"bulak", "kent", "tau", "kol", "su", "yurt", "bek", "ata"},
			new String[]{"Timur", "Arslan", "Batu", "Erlan", "Nurlan", "Kairat", "Bolat", "Daur"},
			new String[]{"Aigerim", "Saule", "Aruzhan", "Dana", "Madina", "Zarina", "Altyn", "Gulnar"},
			new String[]{"Batyrov", "Seitkali", "Omarov", "Tashenov", "Kassym", "Altyn", "Ermek", "Saryn"}));
		PARTS.put(Culture.KARST, new Parts(
			new String[]{"Jade", "Lotus", "Cloud", "Mist", "Bamboo", "Pine", "Crane", "Moon", "Stone", "River"},
			new String[]{" Terrace", " Gorge", " Peak", " Hollow", " Spring", "-on-Water", " Sill", " Bridge"},
			new String[]{"Wen", "Jun", "Lei", "Shan", "Hao", "Ming", "Tao", "Bo"},
			new String[]{"Lian", "Mei", "Xiu", "Yan", "Hua", "Lan", "Jing", "Ning"},
			new String[]{"Lin", "Shen", "Zhou", "Han", "Qiao", "Song", "Yun", "Lu"}));
		PARTS.put(Culture.BAYOU, new Parts(
			new String[]{"Cypress", "Heron", "Moss", "Bayou ", "Willow", "Gator", "Reed", "Mud", "Cane", "Lily"},
			new String[]{" Landing", "wade", " Bend", " Crossing", "water", " Pointe", " Bottom", " Pass"},
			new String[]{"Beau", "Remy", "Luc", "Andre", "Jules", "Theo", "Felix", "Gus"},
			new String[]{"Delphine", "Josette", "Celine", "Marguerite", "Odile", "Lisette", "Babette", "Noemie"},
			new String[]{"Boudreaux", "Thibodeaux", "Arceneaux", "LeBlanc", "Guidry", "Fontenot", "Broussard", "Landry"}));
		PARTS.put(Culture.MESA, new Parts(
			new String[]{"Red", "Sun", "Ochre", "Coyote", "Sage", "Juniper", "Canyon", "Eagle", "Turquoise", "Dust"},
			new String[]{" Mesa", " Rock", " Springs", " Wash", " Butte", " Bluff", " Pueblo", " Gap"},
			new String[]{"Tomas", "Esteban", "Ramon", "Diego", "Joaquin", "Rafael", "Ignacio", "Lorenzo"},
			new String[]{"Rosa", "Inez", "Lucia", "Paloma", "Marisol", "Adelita", "Pilar", "Esperanza"},
			new String[]{"Vigil", "Montoya", "Archuleta", "Baca", "Trujillo", "Lucero", "Sandoval", "Romero"}));
	}

	private Names() {
	}

	private static Parts parts(Culture culture) {
		return PARTS.getOrDefault(culture, PARTS.get(Culture.TEMPERATE));
	}

	public static String settlement(Culture culture, RandomSource random) {
		Parts p = parts(culture);
		String stem = p.stems[random.nextInt(p.stems.length)];
		String end = p.endings[random.nextInt(p.endings.length)];
		if (!stem.endsWith(" ") && !end.startsWith(" ") && !end.startsWith("-") && Character.isUpperCase(end.charAt(0))) {
			end = end.toLowerCase(java.util.Locale.ROOT);
		}
		// avoid "Ashhurst"-style doubled letters at the seam
		if (!stem.isEmpty() && !end.isEmpty() && Character.toLowerCase(stem.charAt(stem.length() - 1)) == end.charAt(0)) {
			end = end.substring(1);
		}
		return stem + end;
	}

	public static String given(Culture culture, boolean female, RandomSource random) {
		Parts p = parts(culture);
		String[] list = female ? p.givenF : p.given;
		return list[random.nextInt(list.length)];
	}

	public static String family(Culture culture, RandomSource random) {
		Parts p = parts(culture);
		return p.family[random.nextInt(p.family.length)];
	}
}
