"""Data owned by the small structures: processor lists, loot tables and
journals. Designs pick theirs with data_for(name, procs) and expose it as
DATA, which gen_structures writes alongside the structure (so a run with
--only writes everything a small structure needs).

Loot is modest (abandoned-camp / village-house level): every small
structure has a public container `<name>` and a stash `<name>_hidden`;
digs and ruins add `<name>_archaeology`.
"""
from loot import SHERDS, book, empty, item, pool, table
from processors import (BAYOU, CLOUD, COAST, DUNES, FOREST, HEATHER, KARST, LUMEN, OVERGROWTH, STEPPE, STONE_WEATHER,
                        TUNDRA, aged_wood, archaeology, flowers, plist, rot, rule)

# ------------------------------------------------------------------ flower fragments not in processors.py
WAYSIDE = flowers(("fern", 0.2), ("expanse:heather", 0.1), ("poppy", 0.05), ("dandelion", 0.05), ("air", 0.15))
VALE = flowers(("allium", 0.08), ("pink_tulip", 0.06), ("azure_bluet", 0.08), ("lily_of_the_valley", 0.06),
               ("fern", 0.1), ("air", 0.2))
PEAKS = flowers(("expanse:edelweiss", 0.15), ("azure_bluet", 0.08), ("fern", 0.1), ("air", 0.35))
SNOWY = flowers(("expanse:frostbloom", 0.12), ("air", 0.6))
MUD_WEATHER = [rule("mud_bricks", 0.12, "packed_mud"), rule("packed_mud", 0.05, "mud")]
SAND_WEATHER = [rule("cut_sandstone", 0.12, "sandstone"), rule("smooth_sandstone", 0.1, "sandstone"),
                rule("expanse:smooth_opal_sandstone", 0.1, "expanse:opal_sandstone"),
                rule("expanse:cut_opal_sandstone", 0.08, "expanse:opal_sandstone")]
LIMESTONE = [rule("expanse:polished_limestone", 0.08, "expanse:limestone"),
             rule("expanse:limestone_bricks", 0.12, "expanse:mossy_limestone")]
STONE_ROT = rot(0.9, ["mossy_cobblestone", "cobblestone", "mossy_stone_bricks", "cracked_stone_bricks", "stone_bricks",
                      "expanse:mossy_limestone", "gravel"])

PROCS = {
    "small_wayside": plist(STONE_WEATHER, aged_wood("spruce", "dark_oak", "redwood"), WAYSIDE, OVERGROWTH),
    "small_karst": plist(STONE_WEATHER, LIMESTONE, aged_wood("redwood", "wisteria"), KARST, OVERGROWTH),
    "small_sand": plist(SAND_WEATHER, aged_wood("acacia", "baobab", "palm"), STEPPE),
    "small_snow": plist(STONE_WEATHER, aged_wood("spruce"), SNOWY, [rule("cobweb", 0.4, "air")]),
    "small_moor": plist(STONE_WEATHER, aged_wood("spruce"), HEATHER, OVERGROWTH),
    "small_moor_ruin": plist(STONE_WEATHER, aged_wood("spruce"), HEATHER, OVERGROWTH, STONE_ROT),
    "small_peat": plist(HEATHER, [rule("mud", 0.08, "packed_mud")],
                        archaeology("gravel", "suspicious_gravel", "expanse:chests/shepherd_bothy_archaeology", 3)),
    "small_tundra": plist(STONE_WEATHER, aged_wood("spruce"), TUNDRA),
    "small_forest": plist(aged_wood("redwood", "spruce"), STONE_WEATHER, FOREST, OVERGROWTH),
    "small_lumen": plist(STONE_WEATHER, aged_wood("lumen"), LUMEN, OVERGROWTH),
    "small_bayou": plist(aged_wood("willow", "spruce"), BAYOU, [rule("moss_carpet", 0.3, "air"),
                                                                rule("expanse:willow_planks", 0.06, "mud_bricks")]),
    "small_steppe": plist(aged_wood("acacia", "spruce"), STONE_WEATHER, STEPPE),
    "small_steppe_dig": plist(aged_wood("acacia", "spruce"), STEPPE,
                              archaeology("gravel", "suspicious_gravel", "expanse:chests/windpump_archaeology", 3)),
    "small_dunes": plist(SAND_WEATHER, DUNES, archaeology("expanse:opal_sand", "suspicious_sand",
                                                          "expanse:chests/sand_colossus_archaeology", 5)),
    "small_cloud": plist(STONE_WEATHER, aged_wood("spruce", "jungle", "dark_oak"), CLOUD, OVERGROWTH),
    "small_mine": plist(STONE_WEATHER, aged_wood("spruce"), PEAKS),
    "small_mine_dig": plist(STONE_WEATHER, aged_wood("spruce"), PEAKS,
                            archaeology("gravel", "suspicious_gravel", "expanse:chests/miners_camp_archaeology", 4)),
    "small_coast": plist(SAND_WEATHER, aged_wood("palm", "spruce"), COAST, [rule("cobweb", 0.2, "air")]),
    "small_peaks": plist(STONE_WEATHER, aged_wood("spruce"), PEAKS),
    "small_cistern": plist([rule("cobweb", 0.3, "air")],
                           archaeology("gravel", "suspicious_gravel", "expanse:chests/old_well_archaeology", 2)),
    "small_grave": plist(STONE_WEATHER, aged_wood("spruce"), WAYSIDE, OVERGROWTH,
                         archaeology("gravel", "suspicious_gravel", "expanse:chests/graveyard_archaeology", 3)),
    "small_vale": plist(STONE_WEATHER, aged_wood("wisteria", "redwood"), VALE, OVERGROWTH),
    "small_dig": plist(TUNDRA, aged_wood("spruce"),
                       archaeology("gravel", "suspicious_gravel", "expanse:chests/mammoth_dig_archaeology", 4)),
}


def data_for(name, procs=(), extra_tables=()):
    """The DATA dict of a small design: its processor lists and its loot tables (plus shared ones it uses)."""
    tables = {t: TABLES[t] for t in (name, f"{name}_hidden", f"{name}_archaeology", f"{name}_supplies",
                                     *extra_tables) if t in TABLES}
    return {"worldgen/processor_list": {p: PROCS[p] for p in procs}, "loot_table/chests": tables}


def lore(key, weight=1):
    """Loot entry for one of the journals below (as loot.lore_book does for the big structures)."""
    b = BOOKS[key]
    return item("written_book", weight, extra=[
        {"type": "minecraft:set_written_book_pages", "pages": list(b["pages"]), "mode": "replace_all"},
        {"type": "minecraft:set_book_cover", "title": b["title"], "author": b["author"], "generation": 2}])


def sherd(name, weight=1):
    assert name in SHERDS, name
    return item(f"{name}_pottery_sherd", weight)


# ------------------------------------------------------------------ journals
BOOKS = {}

BOOKS["wayside_shrine"] = {
    "title": "Drover's Notes", "author": "Aled the drover",
    "pages": [
        "Three days to market over the moor. The shrines are a day apart if the weather holds, and the weather "
        "never holds. Light a candle at each one. My father did, and he grew old.",
        "The custom: leave what you can spare on the offering stone. Whatever is given is kept under it for "
        "whoever comes next in real need. Lift the stone only if you are that traveller.",
        "Lost two ewes in the fog below Tarn Cross. Found them at the shrine in the morning, standing by the "
        "candle as if they had been waiting for me. I left my last loaf.",
    ]}
BOOKS["wayside_shrine_karst"] = {
    "title": "Ferryman's Almanac", "author": "Old Qiu of the ferry",
    "pages": [
        "Mist on the river nine mornings in ten. Where the path leaves the water there is always a stone "
        "lantern. Light it and the next boat will find the landing.",
        "Pilgrims leave red candles; farmers leave bluets. The lantern keepers keep a box of rice and coins "
        "beneath the offering stone for travellers who come up empty-handed.",
        "The pillars were here before the river. The river was here before the lanterns. Be patient - the "
        "lanterns will be here after us.",
    ]}
BOOKS["wayside_shrine_sand"] = {
    "title": "Caravan Waybill", "author": "Teller Odun",
    "pages": [
        "Twelve camels, four carts, one cook who will not stop singing. Reached the post shrine at dusk. "
        "Someone had left water in the jar. We left more.",
        "Rule of the road: a full skin is shared at a shrine, never sold. A coin goes in the barrel, a prayer "
        "goes to the sky, and what is under the offering stone is for the one who has nothing.",
        "The wind moved the dunes again. The milestones are the only thing that stay where they are put.",
    ]}

BOOKS["campsite"] = {
    "title": "Trail Diary", "author": "Tamsin Reed",
    "pages": [
        "Made camp under the big trees. Venison on the fire, boots drying, feet a ruin. If the weather holds "
        "I reach the coast in nine days and the ship in ten.",
        "Something has been circling the camp at night. Not a wolf - wolves are honest about it. I sleep on "
        "my savings now, the way grandmother taught me: dig a hole, put the bedroll over it.",
        "Heard voices on the ridge. Going up to look. Leaving the fire banked. Back before the meal is "
        "done.",
    ]}
BOOKS["campsite_snow"] = {
    "title": "Trapline Tally", "author": "Hrodny the trapper",
    "pages": [
        "Nine hares, two foxes, one very offended owl (released). The hides are pinned to dry. Fingers too "
        "cold to write more than numbers.",
        "Snow came sideways all night. The tent held. My strongbox is under the bedroll where the ice cannot "
        "crack it. If you find this and not me, the furs are yours. The box is for my sister.",
        "Mammoth tracks at dawn, big as cauldrons, heading north. Following them for one day. Only one.",
    ]}
BOOKS["campsite_steppe"] = {
    "title": "The Carter's Ledger", "author": "Bayan the carter",
    "pages": [
        "Twelve jars of oil, four bolts of red cloth, one sack of dates, one very old grandmother's "
        "clock. Paid in advance. Due at the coast before the rains.",
        "The axle sang all morning and broke at noon. Unhitched the mule. The strongbox stays slung under the "
        "bed - nobody looks under a cart.",
        "Walking to the well town for a wheel. If you are reading this and you are not a wheelwright, please "
        "put it back.",
    ]}
BOOKS["campsite_bamboo"] = {
    "title": "Notes of a Plant Hunter", "author": "Ilse Marten",
    "pages": [
        "Day 30. Pressed three new orchids and a fern with leaves like feathers. The bamboo here grows "
        "faster than I can walk. I have stopped trying to keep a path.",
        "The ferryman says the karst hides a flower that only opens in fog. I have the fog. I lack the "
        "flower. My specimens and my coin are buried under the bedroll; frogs have no use for either.",
        "Going up the gully at first light with the vasculum. If I am not back, water the cuttings.",
    ]}

BOOKS["shepherd_bothy"] = {
    "title": "Tally of the Fold", "author": "Morag of the fold",
    "pages": [
        "Ewes 41, lambs 38, rams 2 (one sensible). Lost: none. Found: one lamb belonging to the Tarn Cross "
        "flock, returned with a bill for its supper.",
        "Wind from the north four days. Brought the flock down to the fold. Bran the dog has taken the cushion "
        "by the door again and will not be argued with.",
        "Wages for the season are in the cupboard behind the picture, as always. Mind the hinge. If I am out "
        "on the hill, the kettle is on - help yourself.",
    ]}
BOOKS["shepherd_bothy_ruin"] = {
    "title": "Last Entry", "author": "M.",
    "pages": [
        "The roof went in the great storm. Moved the flock to the low pastures and myself to my sister's. "
        "Bran went too, complaining all the way.",
        "I left my savings where I always kept the spare: under the hearthstone, below the fire. A cold "
        "fire guards it as well as a hot one.",
        "Whoever shelters here: you are welcome to the walls. Leave the heather where it grows.",
    ]}
BOOKS["shepherd_bothy_peat"] = {
    "title": "Peat Book", "author": "Calum the cutter",
    "pages": [
        "Cut forty peats a day, stacked in fours to dry. The moor gives slow and gives back slower. Good "
        "brown peat this year, black at the bottom of the bank.",
        "Spade struck wood today, a yard into the bank. A keg, sealed, full of something yellow that smells "
        "older than my grandfather. They say the old ones buried butter in the bog to keep it.",
        "Left it where it lay. Found a bead and a bit of pot in the wet floor of the cut, too. The moor is "
        "full of other people's things.",
    ]}

BOOKS["mammoth_dig"] = {
    "title": "Dig Journal, Season 3", "author": "Dr. Ingrid Solvang",
    "pages": [
        "The skull is out to the brow. The tusks go on and on - the left curls further than the right, as if "
        "the old cow favoured it. Grid squares C2 to F5 cleared to the frost line.",
        "Brushed the gravel in E4 all morning: a bead of amber, a flint blade, and a sherd with a wolf on it. "
        "People were here when the mammoths were. They ate well.",
        "Storm coming and the sledges cannot wait. The best finds are wrapped and buried in the big spoil "
        "heap, north-east corner, where the snow will keep them. We will be back in spring.",
    ]}
BOOKS["mammoth_dig_ribs"] = {
    "title": "Field Notes", "author": "Pell, assistant digger",
    "pages": [
        "Spine is 13 squares long. Ribs like the beams of a hall. Flagged three finds in red. Doctor says the "
        "skull was taken by an earlier party - a hole and some bootprints is all they left us.",
        "The wind took the tent in the night. Slept under the ribs. Felt like sleeping inside a ship.",
        "Strongbox is under the drift on the windward side. If you are the doctor: sorry about the tent.",
    ]}
BOOKS["mammoth_dig_pond"] = {
    "title": "Hut Rules", "author": "the diggers",
    "pages": [
        "1. Close the door. 2. Keep the hole open - break the skin of ice every morning. 3. Whoever catches "
        "nothing cooks. 4. The stove is not a bed.",
        "Fish caught this season: cod 31, salmon 12, boots 1 (left). Somebody threw our spare box onto the "
        "pond in the autumn melt and now it is frozen in. Bring a pick.",
        "Gone to the dig. Back for supper. Do not eat the supper.",
    ]}

BOOKS["tea_garden"] = {
    "title": "On Tea and Patience", "author": "Mistress Hanae",
    "pages": [
        "Water from the spring, not the pond; the pond is for the fish, who have opinions. Let it cool for "
        "the space of one falling petal before you pour.",
        "Guests sit on the cushions, the host sits nearest the steps so she can see who comes up the path. "
        "The good tea is kept under the host's cushion. A host who is robbed has been inattentive.",
        "When the wisteria is out, no business is discussed. When it is over, everything is.",
    ]}
BOOKS["tea_garden_moongate"] = {
    "title": "The Gardener's Rake", "author": "Old Tomo",
    "pages": [
        "Rake the gravel into waves each morning. The rocks are islands. The gate is the moon. You walk "
        "through the moon to reach the islands. Do not walk on the sea.",
        "The mistress asked why the wall is white. Because the wisteria is purple, and someone must be "
        "patient while it shows off.",
        "My wages are under the stone bench. I have been told this is not a secure arrangement.",
    ]}
BOOKS["tea_garden_bridge"] = {
    "title": "Fishing Diary", "author": "Ren, aged eleven",
    "pages": [
        "Caught a cod under the red bridge. Grandfather says cod do not live in streams. I said tell that to "
        "the cod.",
        "The boat is not mine but nobody else uses it. I keep my treasure in a box under the end of the "
        "bridge where only the fish can see it.",
        "Lanterns at the four corners are for the spirits crossing at night. Grandfather lights them. I am "
        "allowed to watch.",
    ]}

BOOKS["treehouse"] = {
    "title": "Lookout Log", "author": "Ranger Wren",
    "pages": [
        "No smoke over the giants today. Two elk at the stream, one bear (unbothered, unbothering). Hauled up "
        "water in the basket; the chain sings when the wind gets in it.",
        "From the crow's nest you can see three ridges and the glint of the sea. I keep my good knife and my "
        "pay in the knot-hole halfway up - nobody climbs that far who isn't meant to.",
        "Rule of the giants: take no living wood, light no fire under a branch, and leave the ladder down "
        "for whoever needs it.",
    ]}
BOOKS["treehouse_cabin"] = {
    "title": "Ranger's Standing Orders", "author": "The Warden of the Giants",
    "pages": [
        "1. Walk the eastern ridge at dawn. 2. Mark every fallen giant on the map. 3. Report fire by beacon, "
        "not by running. 4. Feed the wolf before yourself.",
        "The strongbox is under the floor by the table; the hatch sticks. Rations for two weeks, coin for "
        "the season, the spare bowstring.",
        "If a traveller comes in from the rain, they may have the bed. You may have the floor. The wolf "
        "keeps the cushion.",
    ]}
BOOKS["treehouse_hollow"] = {
    "title": "Hermit's Almanac", "author": "Brother Fen",
    "pages": [
        "Year nine inside the fallen giant. It came down in a gale before my time and left its roots standing "
        "like a door to the underworld. I live in the trunk. The roots keep my secrets.",
        "Mushrooms on the north side, moss on the south, rain on everything. I have stopped counting days "
        "and started counting rings - this tree saw eleven hundred winters.",
        "Visitors: knock on the bark. If I do not answer I am out, or asleep, or a mushroom.",
    ]}

BOOKS["fairy_ring"] = {
    "title": "Things Not To Do", "author": "a worried forester",
    "pages": [
        "Do not step inside the ring of glowcaps after dark. Do not step inside it before dark either. Do not "
        "eat the cake. Nobody knows who bakes the cake.",
        "Travellers throw coins into the spring for luck, and the luck goes somewhere, and so do the coins. "
        "I have seen the glint of a box down there. I have not reached for it. See rule one.",
        "The warden's box in the old stump is for anyone lost in the grove. Take a torch and walk toward the "
        "lumen trees until they stop following you.",
    ]}
BOOKS["fairy_ring_toadstool"] = {
    "title": "Guest Book", "author": "the Toadstool",
    "pages": [
        "Welcome! Please mind your head, your feet, and the ring. The bed is short because we are short. The "
        "berries on the shelf are for guests; the stew is not.",
        "Signed: a tired traveller (slept well, dreamed of singing). A forester (left in a hurry). A fox (left "
        "a feather). Someone very small (left the rug crooked).",
        "Do not lift the rug. There is nothing under the rug. Especially not a hatch.",
    ]}
BOOKS["fairy_ring_moonwell"] = {
    "title": "On the Moonwell", "author": "Alchemist Vey",
    "pages": [
        "The basin glows from beneath though no fire is lit. The water is cold, still, and three fingers "
        "deep; anything dropped in it is seen forever and reached never, or so the grove folk say.",
        "I put my hand in. It is three fingers deep. There is a box. I left it, because my notes on the "
        "lumen trees warn that the grove keeps accounts.",
        "Offer prismite at the altar and the arches hum. Offer nothing and they hum anyway, but less kindly.",
    ]}

BOOKS["bayou_shack"] = {
    "title": "Tide and Catch", "author": "Fen the net-mender",
    "pages": [
        "Crab pots out at dawn, in at dusk. The cat gets the small ones, I get the rest, and the herons get "
        "whatever I am not watching.",
        "Lost the old boat in the spring flood. She went down right beside the stilts with my strongbox "
        "still wedged in the bow. Water is only waist deep. I keep meaning to.",
        "Moss on the eaves keeps the shack cool. Moss on everything else keeps me humble.",
    ]}
BOOKS["bayou_shack_smokehouse"] = {
    "title": "Smoking Fish", "author": "Granny Willa",
    "pages": [
        "Willow chips, never pine. Fish split and salted, hung for a night and a day. If the smoke goes "
        "straight up, add chips. If it goes sideways, add patience.",
        "The good knives and the coin are under the last boards of the jetty, where the floor lifts. The "
        "crabs guard it for nothing.",
        "Ring the lantern post twice if you want to buy. Three times if you want to stay for supper.",
    ]}
BOOKS["bayou_shack_sunk"] = {
    "title": "Water Rising", "author": "Old Abe",
    "pages": [
        "Third flood this year. The corner stilt went in the night and the whole floor tipped. Everything I "
        "own slid toward the water - the box with it.",
        "Moving to my daughter's on the high ground. Leaving the shack to the moss. The moss has wanted it "
        "for years.",
        "If you find my box in the low corner, keep the coin. Send the pocket watch to Willa at the "
        "smokehouse.",
    ]}

BOOKS["windpump"] = {
    "title": "Drovers' Book", "author": "kept at the pump",
    "pages": [
        "Water for any herd that passes. Grease the wheel when it squeals. If the vane sticks, climb up and "
        "kick it gently; it responds to kindness and boots.",
        "Every drover who waters here leaves a coin in the box under the pump head. The coin pays the "
        "wheelwright. The wheelwright has not been seen in two years.",
        "Signed: Bayan, 300 head. Tolga, 120 head and a goat. Anonymous, 1 camel, very thirsty.",
    ]}
BOOKS["windpump_dry"] = {
    "title": "Season of No Rain", "author": "the pump keeper",
    "pages": [
        "The wheel stopped in the spring and came down in the summer gale. The hole went to mud, then to "
        "cracks. The herds go round now, two days east.",
        "Buried my box in the west bank before I left, where the grass still grows. The rest of my life fits "
        "on the cart.",
        "People came here long before the pump. When the mud cracks you find their beads and their bones. "
        "Brush gently. They were thirsty too.",
    ]}
BOOKS["windpump_corral"] = {
    "title": "Herd Tally", "author": "Head drover Tolga",
    "pages": [
        "Penned 140 for the night. Two bulls sulking, one heifer escaped and returned with a friend. Salt "
        "lick nearly gone.",
        "Ring the bell at dawn and they come to the gate. Ring it at dusk and they come to the gate. Ring it "
        "at noon and they look at you.",
        "Wages and the season's purse under the bench, as always. The dog guards it. The dog is asleep.",
    ]}

BOOKS["sand_colossus"] = {
    "title": "Excavation Notes", "author": "Dr. Amara Okafor",
    "pages": [
        "The head is the height of six men and we have only found the chin. Whoever carved it wore a crown "
        "of sun discs and an expression of mild disappointment.",
        "There is a crack in the back of the skull large enough to crawl through. Inside: a chamber, a jar, "
        "a box. We have left the box sealed until the permit arrives.",
        "Brush the sand around the drifts slowly. Every storm uncovers something and buries something else.",
    ]}
BOOKS["sand_colossus_obelisk"] = {
    "title": "The Fallen Needle", "author": "a caravan scribe",
    "pages": [
        "The needle stood when my grandmother crossed these dunes. It fell in the year of the great wind, in "
        "three pieces, as if it had been snapped over a knee.",
        "The glyph bands tell the hours of the pink sun. The top lies apart, still pointing at the sky out of "
        "habit.",
        "At the break the sand piles deep. Something square lies under it. I had no shovel and no courage.",
    ]}
BOOKS["sand_colossus_gate"] = {
    "title": "Sealed Door", "author": "unknown",
    "pages": [
        "We sealed the room with sand and left by the gate, as the old law says: what the sun gave, the sand "
        "keeps.",
        "If you dig through the doorway you will find the sun's portion. Take a part and leave a part. The "
        "dunes are always watching.",
        "The pylons will outlast our names. Brush the sill for what the wind leaves at the door.",
    ]}

BOOKS["terrace_farm"] = {
    "title": "Almanac of the Terraces", "author": "Grandmother Lan",
    "pages": [
        "Flood the top terrace first; the water knows the way down. Plant when the frogs start, harvest when "
        "the egrets leave. The scarecrow is for the egrets. It does not work.",
        "Three walls, three fields, three generations. Your grandfather built the bottom wall crooked and "
        "would not hear a word about it.",
        "What we save goes under the rice sacks by the fire, where thieves would have to lift a winter's "
        "work to find it.",
    ]}
BOOKS["terrace_farm_granary"] = {
    "title": "Granary Rules", "author": "Farmer Hoang",
    "pages": [
        "The granary stands on stone mushrooms so the rats cannot climb. The rats have read this book. Keep "
        "the ladder away from the door at night.",
        "Seed rice for next year is in the box at the heart of the grain. Never eat the seed rice. Not even "
        "in a hard winter. Especially not in a hard winter.",
        "Thresh on the stone floor. Sing while you do it; the flail keeps time better than you do.",
    ]}
BOOKS["terrace_farm_shelter"] = {
    "title": "Buffalo", "author": "a farm boy",
    "pages": [
        "Our buffalo is called Mountain. She pulls the plough, eats the hay, and sleeps in the shelter "
        "unless it is raining, when she sleeps in the paddy.",
        "Mother lights a candle at the field god's shrine every morning. The god's box is under the step. I "
        "am not allowed to touch it. I have touched it.",
        "Mountain is missing. Gone to look on the high terrace. Back for supper.",
    ]}

BOOKS["pilgrim_shrine"] = {
    "title": "The Pilgrim's Way", "author": "Sister Dawa",
    "pages": [
        "Seven shrines between the valley and the cloud line. Ring the bell at each one so the next shrine "
        "knows you are coming. The mist carries sound further than sight.",
        "At the foot of the spur the rock is hollow behind the vines. Pilgrims who can spare it leave food "
        "there for pilgrims who cannot. Take what you need; the bell will forgive you.",
        "The flags are prayers the wind says for you while you climb. Do not count them. It is bad luck and "
        "bad arithmetic.",
    ]}
BOOKS["pilgrim_shrine_cave"] = {
    "title": "Cave Sayings", "author": "the hermit of the outcrop",
    "pages": [
        "The spring speaks once a minute. In forty years I have learned three of its words.",
        "Strike the gong gently: it wakes the valley. Strike it hard: it wakes me.",
        "Visitors bring rice and questions. I keep the rice behind the roots in the west alcove, and answer "
        "the questions with more questions.",
    ]}
BOOKS["pilgrim_shrine_stupa"] = {
    "title": "Turning the Wheels", "author": "a novice",
    "pages": [
        "Walk clockwise round the stupa and turn each wheel as you pass. Each turn says the prayer written "
        "inside. I have said ten thousand prayers this morning and my arm is tired.",
        "The relic is behind the painted panel on the east side, sealed in the plinth when the stupa was "
        "raised. Nobody is to open it. Everybody knows where it is.",
        "Light the butter lamps at dusk; the flags at dawn take care of themselves.",
    ]}

BOOKS["miners_camp"] = {
    "title": "Claim Ledger No. 7", "author": "Ore & Sons",
    "pages": [
        "Drove the adit eight yards into the knoll. Prismite showing at the face like frozen lightning. "
        "Iron with it. The rails run out to the bin; the bin is never full enough.",
        "Lamp went blue in the side gallery and Ned fainted. Boarded it up. Put the strongbox in there "
        "afterwards - nobody will go past that sign, not even us.",
        "Snow to the waist. Pay the crew from the bin. Keep the pick sharp and the fire lit.",
    ]}
BOOKS["miners_camp_sluice"] = {
    "title": "Panning Notes", "author": "Tomas Brack",
    "pages": [
        "Water in the head box, gravel in the riffles, gold in the pan. Sometimes. Mostly gravel in the "
        "pan.",
        "The tailings below the sluice still hold bits - beads, bones, once a whole ring. Anybody with a "
        "brush and patience can find them. I have neither.",
        "Pay dirt is in the box under the rocker. If you are a claim jumper, the box is empty. It is not "
        "empty.",
    ]}
BOOKS["miners_camp_collapse"] = {
    "title": "After the Fall", "author": "the foreman",
    "pages": [
        "The roof came down at the second set in the night. We dug for two days. We found Ned's helmet and "
        "nothing else.",
        "The adit is closed. The crew's wages are buried under the rubble at the mouth so the mountain "
        "keeps them for whoever comes back. I will not be coming back.",
        "Do not reopen. The rock here is tired.",
    ]}

BOOKS["beach_hut"] = {
    "title": "Beachcomber's Log", "author": "Marisol",
    "pages": [
        "Low tide at dawn. Found: a shell like a spiral stair, two bottles (empty), one bottle (not empty - "
        "a love letter, unsigned, very damp), and a boot.",
        "The good finds I bury above the tide line and mark with driftwood. An X, because I have read the "
        "same books as everyone else.",
        "The raft needs a new lashing. The hammock needs a new knot. I need nothing at all.",
    ]}
BOOKS["beach_hut_nets"] = {
    "title": "Net Mender's Tally", "author": "Old Teodoro",
    "pages": [
        "Mended: four nets, one sail, the crab pot the gulls broke. Sold: forty fish, nine crabs, one story "
        "about a sea serpent (true).",
        "Ring the bell when the boats are in and the village comes running. Ring it at any other time and "
        "the village comes running angrily.",
        "The takings are under the old boat on the trestles. The old boat has not been to sea since my "
        "father. It guards the money better than it ever kept out water.",
    ]}
BOOKS["beach_hut_castaway"] = {
    "title": "Message in a Bottle", "author": "a castaway",
    "pages": [
        "To whoever finds this: I am on a beach of palms with no name. My ship was the Dawn Heron. I have "
        "built a sign that says SOS and a fire that says the same thing more urgently.",
        "Day 37. Coconuts are not as nourishing as the songs suggest. Fish are better. The crabs have "
        "begun to recognise me.",
        "If I am gone when you come, I was rescued, or I swam. My savings are buried at the foot of the "
        "signal fire. Spend them on someone who is lost.",
    ]}

BOOKS["summit_cairn"] = {
    "title": "Summit Register", "author": "the mountain",
    "pages": [
        "Reached the top in cloud, saw nothing, very happy. - Ilse. Second attempt, saw everything, cried a "
        "bit. - Ilse again.",
        "Left an edelweiss for the ones who did not come down. Brought one down for my mother. - Tomas. "
        "Wind took my hat. If found, it is a good hat. - anon.",
        "The climbers' stash is under the flat stone by the north-west pole: rope, food, a spare pair of "
        "gloves. Take what you need, bring what you can.",
    ]}
BOOKS["summit_cairn_shelter"] = {
    "title": "Shelter Rules", "author": "the hut keeper",
    "pages": [
        "The wall keeps the north wind off. The bench keeps you off the snow. The fire pit keeps you alive, "
        "if you brought wood. Nobody ever brings wood.",
        "The pillar is a survey mark. Do not move it. Do not climb it. Do not tie your mule to it.",
        "Under the bench: the keeper's box - matches, bread, a flask. Replace what you use. The mountain "
        "keeps the accounts.",
    ]}
BOOKS["summit_cairn_memorial"] = {
    "title": "For Brannagh", "author": "her rope partner",
    "pages": [
        "She went up the east ridge in the last light, faster than I could follow, laughing. The cloud came "
        "down. We found her axe on the top, laid across the stones as if she had stopped to rest.",
        "We built the cairn round it. Her pack is buried at its foot, the way she would have wanted - "
        "useful to whoever comes next.",
        "If you sit on the bench and look south, you see what she saw. That is the whole of the memorial.",
    ]}

BOOKS["old_well"] = {
    "title": "Well Keeper's Notes", "author": "Goodwife Hale",
    "pages": [
        "Bucket rope replaced, roof re-shingled, toad removed from the bucket (twice). The water is sweet "
        "and very cold.",
        "Folk toss coins in for luck. The coins sink into a box my grandfather set on the bottom, and once a "
        "year we fish it up and mend the roof with it. Luck is a well roof that does not leak.",
        "Wash your hands before the bucket, not after. That is all the rule there is.",
    ]}
BOOKS["old_well_limestone"] = {
    "title": "On the Spring Stone", "author": "Abbot Lin's novice",
    "pages": [
        "The well is older than the pagoda. The limestone keeps the water cool and tasting of rain.",
        "Pilgrims drop a coin and make a wish; the coins rest in a box at the bottom. The abbot says the "
        "wishes rest there too, and are counted by fish.",
        "Draw water at dawn, never at noon. The noon water belongs to the dragonflies.",
    ]}
BOOKS["old_well_sandstone"] = {
    "title": "Caravan Well", "author": "the water warden",
    "pages": [
        "Twelve buckets a day for a caravan, three for a traveller, as many as you need for a child. The "
        "well has never been dry while I have kept it.",
        "Coins go in for the warden's wage and settle in the box on the bottom. Do not dive for them. "
        "People have.",
        "Shade under the roof, water in the trough, and nobody may be turned away. That is the law of the "
        "wells.",
    ]}
BOOKS["old_well_mud"] = {
    "title": "Clean Water", "author": "Granny Willa",
    "pages": [
        "In the bayou every pool is brown. This well is the only clear water for a mile. Mud brick, willow "
        "roof, and a lid when it rains.",
        "The young ones throw coins in and wish for boats. The coins sit in a box on the bottom. The boats "
        "have not arrived.",
        "Boil it anyway.",
    ]}
BOOKS["old_well_cistern"] = {
    "title": "Scratched Notes", "author": "someone below",
    "pages": [
        "Climbed down to see why the well went dry. The ladder broke. The cistern is cold and the water is "
        "only a puddle. I have candles for two nights.",
        "There is a box down here and some old pots - the keepers must have stored things below. The silt "
        "has beads in it, bones, a ring.",
        "Shouting has not helped. Writing this in case it does.",
    ]}

BOOKS["graveyard"] = {
    "title": "Sexton's Book", "author": "Old Gideon",
    "pages": [
        "Seven graves, one dog, and a table tomb for the Hales, who could afford a table. I keep the grass "
        "short and the lantern lit. Nobody here complains.",
        "The Hales were buried with their silver under the tomb lid, as was the fashion. The fashion was "
        "foolish. The lid is heavy. I have said nothing.",
        "The older graves are only gravel now. Rain brings up beads and buttons. I put them back.",
    ]}
BOOKS["graveyard_chapel"] = {
    "title": "Chapel Inventory", "author": "the last curate",
    "pages": [
        "One bell (cracked). One altar (cracked). One roof (gone). Parishioners: four, all in the yard.",
        "The communion plate is under the altar stone, where my predecessor hid it from the raiders and "
        "I hid it from the bishop.",
        "Ring the bell for weddings, funerals and fog. Mostly fog.",
    ]}

BOOKS["old_well_ruin"] = {
    "title": "When the Roof Fell", "author": "the last well keeper",
    "pages": [
        "The storm took the roof and half the curb. The water is still good in the trough, if you do not "
        "mind the frogs.",
        "We could not mend it this year. The well fund - every coin the travellers dropped in - is buried "
        "west of the trough, under the flat stone. For the one who rebuilds it.",
        "Leave the barrel full for whoever passes. That much we can still do.",
    ]}

# ------------------------------------------------------------------ loot tables
TABLES = {}


def _shrine_tables(name, flavor, flavor_hidden):
    TABLES[name] = table(
        name,
        pool((2, 4), item("gold_nugget", 10, (1, 5)), item("iron_nugget", 8, (1, 6)), item("candle", 8, (1, 3)),
             item("bread", 8, (1, 2)), item("torch", 5, (1, 4)), item("paper", 4, (1, 3)), *flavor),
        pool(1, empty(8), item("emerald", 2), item("name_tag", 1)))
    TABLES[f"{name}_hidden"] = table(
        f"{name}_hidden",
        pool((2, 4), item("bread", 10, (2, 4)), item("cooked_mutton", 6, (1, 3)), item("iron_ingot", 6, (1, 3)),
             item("gold_ingot", 3, (1, 2)), item("emerald", 6, (1, 3)), item("golden_carrot", 3, (1, 3)),
             item("compass", 2), item("leather_boots", 2, enchant=True), *flavor_hidden),
        pool(1, empty(4), book(2), item("golden_apple", 1), lore(name, 2)))


_shrine_tables("wayside_shrine", [item("expanse:heather", 6, (1, 4)), item("expanse:edelweiss", 3, (1, 2))],
               [item("expanse:cooked_venison", 6, (1, 3)), item("white_wool", 3, (1, 3))])
_shrine_tables("wayside_shrine_karst", [item("bamboo", 6, (2, 6)), item("honey_bottle", 3)],
               [item("lantern", 4), item("paper", 4, (3, 8)), item("expanse:wisteria_blossoms", 3, (1, 3))])
_shrine_tables("wayside_shrine_sand", [item("dead_bush", 4, (1, 2)), item("cactus", 4, (1, 2))],
               [item("water_bucket", 4), item("rabbit_hide", 4, (1, 3)), item("golden_carrot", 2, (2, 4))])


def _camp_tables(name, food, kit, treasure):
    TABLES[name] = table(
        name,
        pool((3, 6), item("bread", 8, (1, 3)), item("torch", 6, (2, 6)), item("string", 6, (1, 4)),
             item("stick", 6, (2, 6)), item("coal", 5, (1, 4)), item("leather", 5, (1, 3)), item("flint", 4, (1, 2)),
             item("bowl", 3), *food, *kit),
        pool(1, empty(6), item("compass", 1), item("lead", 2), item("emerald", 2)))
    TABLES[f"{name}_hidden"] = table(
        f"{name}_hidden",
        pool((2, 5), item("emerald", 8, (1, 4)), item("gold_ingot", 4, (1, 3)), item("iron_ingot", 6, (1, 4)),
             item("gold_nugget", 6, (3, 9)), item("map", 2), item("spyglass", 1), *treasure),
        pool(1, empty(3), book(2), item("golden_apple", 1), item("name_tag", 1), lore(name, 3)))


_camp_tables("campsite", [item("expanse:cooked_venison", 8, (1, 3)), item("apple", 6, (1, 3))],
             [item("arrow", 5, (2, 8)), item("expanse:redwood_sapling", 2)],
             [item("iron_axe", 3, enchant=True), item("bow", 3, enchant=True)])
_camp_tables("campsite_snow", [item("expanse:cooked_venison", 8, (1, 3)), item("cooked_cod", 6, (1, 3))],
             [item("rabbit_hide", 6, (1, 4)), item("snowball", 4, (2, 8))],
             [item("leather_boots", 3, enchant=True), item("powder_snow_bucket", 2), item("rabbit_foot", 2)])
_camp_tables("campsite_steppe", [item("cooked_mutton", 8, (1, 3)), item("baked_potato", 6, (1, 3))],
             [item("red_dye", 4, (1, 3)), item("red_wool", 4, (1, 3))],
             [item("saddle", 3), item("golden_carrot", 3, (2, 5)), item("clock", 2)])
_camp_tables("campsite_bamboo", [item("cooked_salmon", 8, (1, 3)), item("melon_slice", 6, (2, 5))],
             [item("bamboo", 6, (2, 8)), item("paper", 4, (2, 5))],
             [item("fishing_rod", 3, enchant=True), item("glow_berries", 3, (2, 6)), item("cocoa_beans", 3, (2, 6))])

TABLES["shepherd_bothy"] = table(
    "shepherd_bothy",
    pool((3, 6), item("bread", 10, (1, 3)), item("cooked_mutton", 8, (1, 3)), item("mutton", 6, (1, 3)),
         item("white_wool", 8, (1, 4)), item("string", 6, (1, 3)), item("milk_bucket", 2), item("candle", 4, (1, 2)),
         item("wheat", 5, (2, 6)), item("expanse:heather", 5, (1, 4))),
    pool(1, empty(5), item("shears", 2), item("lead", 3), item("emerald", 1)))
TABLES["shepherd_bothy_hidden"] = table(
    "shepherd_bothy_hidden",
    pool((2, 4), item("emerald", 10, (2, 5)), item("gold_nugget", 8, (4, 10)), item("iron_ingot", 5, (1, 3)),
         item("gold_ingot", 3, (1, 2)), item("cooked_mutton", 5, (2, 4)), item("honey_bottle", 4, (1, 2)),
         item("golden_carrot", 3, (1, 3))),
    pool(1, empty(3), book(2), item("name_tag", 2), item("music_disc_far", 1), lore("shepherd_bothy", 2)))
TABLES["shepherd_bothy_archaeology"] = table(
    "shepherd_bothy_archaeology", pool(1, item("gold_nugget", 3), item("bone", 3), item("flint", 2),
                                       item("iron_nugget", 2), sherd("howl", 2), sherd("sheaf", 2), sherd("snort"),
                                       item("emerald"), item("expanse:heather", 2)), kind="archaeology")

TABLES["mammoth_dig"] = table(
    "mammoth_dig",
    pool((3, 6), item("bone", 10, (2, 6)), item("bone_meal", 6, (2, 8)), item("flint", 6, (1, 4)),
         item("torch", 6, (2, 6)), item("cooked_cod", 6, (1, 3)), item("expanse:cooked_venison", 5, (1, 2)),
         item("coal", 5, (1, 4)), item("paper", 4, (1, 4)), item("string", 4, (1, 3)), item("snowball", 3, (2, 6))),
    pool(1, empty(5), item("brush", 3), item("spyglass", 1), item("compass", 1)))
TABLES["mammoth_dig_hidden"] = table(
    "mammoth_dig_hidden",
    pool((2, 5), item("bone_block", 6, (1, 3)), item("emerald", 8, (2, 5)), item("gold_ingot", 4, (1, 3)),
         item("amethyst_shard", 5, (1, 4)), item("diamond", 2), item("brush", 4), sherd("howl", 3),
         sherd("snort", 3), sherd("explorer", 2)),
    pool(1, empty(3), item("sniffer_egg", 1), item("golden_apple", 1), book(2), lore("mammoth_dig", 3)))
TABLES["mammoth_dig_archaeology"] = table(
    "mammoth_dig_archaeology", pool(1, item("bone", 4), item("flint", 3), item("bone_meal", 2), item("gold_nugget", 2),
                                    sherd("howl", 2), sherd("snort", 2), sherd("danger"), item("emerald"),
                                    item("arrow", 2), item("sniffer_egg")), kind="archaeology")

TABLES["tea_garden"] = table(
    "tea_garden",
    pool((3, 6), item("honey_bottle", 6, (1, 2)), item("cookie", 8, (2, 6)), item("sugar", 6, (1, 4)),
         item("glass_bottle", 6, (1, 3)), item("bowl", 4, (1, 3)), item("candle", 5, (1, 3)),
         item("expanse:wisteria_blossoms", 6, (1, 4)), item("expanse:wisteria_sapling", 3),
         item("pink_petals", 4, (1, 4)),
         item("cod", 4, (1, 2))),
    pool(1, empty(5), item("emerald", 2), item("cake", 1), item("music_disc_strad", 1)))
TABLES["tea_garden_hidden"] = table(
    "tea_garden_hidden",
    pool((2, 5), item("emerald", 8, (2, 5)), item("gold_ingot", 4, (1, 3)), item("amethyst_shard", 4, (2, 5)),
         item("honeycomb", 5, (1, 3)), item("expanse:azure_wisteria_blossoms", 4, (1, 3)),
         item("lapis_lazuli", 4, (2, 6)),
         item("glow_berries", 3, (2, 5)), item("diamond", 1)),
    pool(1, empty(3), book(3), item("golden_apple", 1), item("name_tag", 1), lore("tea_garden", 2)))

TABLES["treehouse"] = table(
    "treehouse",
    pool((3, 6), item("expanse:cooked_venison", 8, (1, 3)), item("bread", 6, (1, 3)), item("arrow", 8, (2, 10)),
         item("torch", 6, (2, 6)), item("string", 5, (1, 3)), item("leather", 5, (1, 3)), item("apple", 5, (1, 3)),
         item("expanse:redwood_sapling", 4, (1, 2)), item("expanse:redwood_log", 4, (2, 6)), item("map", 2)),
    pool(1, empty(5), item("spyglass", 1), item("emerald", 2), item("bow", 2, damage=(0.3, 0.8))))
TABLES["treehouse_hidden"] = table(
    "treehouse_hidden",
    pool((2, 5), item("emerald", 8, (2, 5)), item("iron_ingot", 6, (2, 4)), item("gold_ingot", 4, (1, 3)),
         item("iron_sword", 3, enchant=True), item("bow", 3, enchant=True), item("arrow", 5, (8, 16)),
         item("honey_bottle", 4, (1, 3)), item("diamond", 1)),
    pool(1, empty(3), book(3), item("golden_apple", 1), item("name_tag", 1), lore("treehouse", 2)))

TABLES["fairy_ring"] = table(
    "fairy_ring",
    pool((3, 6), item("glow_berries", 8, (2, 6)), item("expanse:glowcap", 8, (1, 4)), item("brown_mushroom", 6, (1, 3)),
         item("red_mushroom", 6, (1, 3)), item("honey_bottle", 4), item("cookie", 5, (2, 5)), item("torch", 5, (2, 6)),
         item("expanse:lumen_sapling", 3), item("glow_ink_sac", 4, (1, 2)),
         item("expanse:prismite_cluster", 3, (1, 2))),
    pool(1, empty(5), item("emerald", 2), item("cake", 1), item("music_disc_mellohi", 1)))
TABLES["fairy_ring_hidden"] = table(
    "fairy_ring_hidden",
    pool((3, 6), item("gold_nugget", 10, (3, 12)), item("emerald", 6, (1, 4)), item("amethyst_shard", 5, (2, 6)),
         item("expanse:prismite_block", 3, (1, 2)), item("glowstone_dust", 5, (2, 6)),
         item("experience_bottle", 4, (1, 3)),
         item("diamond", 1)),
    pool(1, empty(3), book(3), item("golden_apple", 2), item("name_tag", 1), lore("fairy_ring", 2)))

TABLES["bayou_shack"] = table(
    "bayou_shack",
    pool((3, 6), item("cod", 10, (1, 4)), item("salmon", 8, (1, 3)), item("expanse:crab_meat", 8, (1, 3)),
         item("string", 6, (1, 4)), item("slime_ball", 4, (1, 2)), item("expanse:cattail", 4, (1, 3)),
         item("expanse:spanish_moss", 5, (1, 4)), item("lily_pad", 4, (1, 3)), item("bowl", 3)),
    pool(1, empty(5), item("fishing_rod", 3, damage=(0.2, 0.8)), item("emerald", 2), item("lead", 1)))
TABLES["bayou_shack_hidden"] = table(
    "bayou_shack_hidden",
    pool((2, 5), item("emerald", 8, (2, 5)), item("gold_ingot", 4, (1, 3)), item("gold_nugget", 6, (4, 10)),
         item("nautilus_shell", 2), item("fishing_rod", 4, enchant=True), item("clock", 2),
         item("expanse:cooked_crab", 5, (2, 5)), item("prismarine_shard", 2, (1, 3))),
    pool(1, empty(3), book(3), item("nautilus_shell", 1), item("name_tag", 1), lore("bayou_shack", 2)))

TABLES["windpump"] = table(
    "windpump",
    pool((3, 6), item("bread", 8, (1, 3)), item("wheat", 8, (2, 6)), item("leather", 6, (1, 3)), item("lead", 4),
         item("bucket", 3), item("string", 5, (1, 3)), item("iron_nugget", 6, (2, 8)), item("hay_block", 2),
         item("beef", 5, (1, 3)), item("expanse:baobab_sapling", 2)),
    pool(1, empty(5), item("saddle", 2), item("emerald", 2), item("name_tag", 1)))
TABLES["windpump_hidden"] = table(
    "windpump_hidden",
    pool((2, 5), item("gold_nugget", 10, (4, 12)), item("emerald", 8, (2, 5)), item("gold_ingot", 4, (1, 3)),
         item("iron_ingot", 5, (1, 4)), item("iron_horse_armor", 2), item("golden_carrot", 3, (2, 5)),
         item("copper_ingot", 4, (2, 6))),
    pool(1, empty(3), book(2), item("golden_horse_armor", 1), item("saddle", 2), lore("windpump", 2)))
TABLES["windpump_archaeology"] = table(
    "windpump_archaeology", pool(1, item("bone", 4), item("gold_nugget", 3), item("flint", 2), item("emerald"),
                                 sherd("plenty", 2), sherd("sheaf", 2), sherd("archer"), item("iron_nugget", 2),
                                 item("copper_ingot")), kind="archaeology")

TABLES["sand_colossus"] = table(
    "sand_colossus",
    pool((2, 5), item("gold_nugget", 10, (2, 8)), item("bone", 6, (1, 3)), item("expanse:opal_sand", 6, (2, 6)),
         item("glowstone_dust", 4, (1, 4)), item("rabbit_hide", 4, (1, 2)), item("dried_kelp", 4, (2, 5)),
         item("torch", 5, (2, 5)), item("paper", 4, (1, 3))),
    pool(1, empty(5), item("brush", 3), item("emerald", 2)))
TABLES["sand_colossus_hidden"] = table(
    "sand_colossus_hidden",
    pool((3, 6), item("gold_ingot", 10, (2, 5)), item("gold_nugget", 6, (5, 12)), item("emerald", 8, (2, 5)),
         item("lapis_lazuli", 5, (3, 8)), item("expanse:chiseled_opal_sandstone", 3, (1, 3)), item("diamond", 2),
         item("golden_horse_armor", 2), sherd("prize", 2), sherd("burn", 2)),
    pool(1, empty(2), item("golden_apple", 3), book(3), item("music_disc_relic", 1), lore("sand_colossus", 2)))
TABLES["sand_colossus_archaeology"] = table(
    "sand_colossus_archaeology", pool(1, item("gold_nugget", 4), item("emerald", 2), item("bone", 2), sherd("prize", 2),
                                      sherd("burn", 2), sherd("danger"), sherd("skull"), item("gold_ingot"),
                                      item("lapis_lazuli", 2)), kind="archaeology")

TABLES["terrace_farm"] = table(
    "terrace_farm",
    pool((3, 6), item("wheat", 10, (2, 8)), item("wheat_seeds", 8, (2, 8)), item("bread", 6, (1, 3)),
         item("bamboo", 6, (2, 6)), item("bowl", 4, (1, 2)), item("cooked_salmon", 5, (1, 2)),
         item("sugar_cane", 4, (1, 3)),
         item("melon_slice", 4, (2, 5)), item("paper", 3, (1, 3)), item("expanse:limestone", 3, (2, 6))),
    pool(1, empty(5), item("emerald", 2), item("iron_hoe", 2), item("lead", 1)))
TABLES["terrace_farm_hidden"] = table(
    "terrace_farm_hidden",
    pool((2, 5), item("emerald", 8, (2, 6)), item("gold_ingot", 4, (1, 3)), item("lime_dye", 3, (1, 4)),
         item("golden_carrot", 4, (2, 5)), item("wheat_seeds", 5, (8, 16)),
         item("pumpkin_seeds", 3, (2, 6)), item("melon_seeds", 3, (2, 6)), item("diamond", 1)),
    pool(1, empty(3), book(3), item("golden_apple", 1), lore("terrace_farm", 2)))

TABLES["pilgrim_shrine"] = table(
    "pilgrim_shrine",
    pool((3, 6), item("bread", 8, (1, 3)), item("candle", 8, (1, 3)), item("yellow_candle", 4, (1, 2)),
         item("paper", 6, (1, 4)), item("honey_bottle", 4), item("cocoa_beans", 4, (2, 5)),
         item("glow_berries", 4, (1, 4)),
         item("apple", 5, (1, 3)), item("expanse:edelweiss", 3, (1, 2))),
    pool(1, empty(5), item("emerald", 2), item("lantern", 2)))
TABLES["pilgrim_shrine_hidden"] = table(
    "pilgrim_shrine_hidden",
    pool((2, 5), item("emerald", 8, (2, 5)), item("gold_ingot", 4, (1, 3)), item("experience_bottle", 5, (1, 3)),
         item("lapis_lazuli", 5, (3, 8)), item("amethyst_shard", 4, (2, 5)), item("golden_carrot", 3, (2, 4)),
         item("diamond", 1)),
    pool(1, empty(2), book(4), item("golden_apple", 2), lore("pilgrim_shrine", 2)))

TABLES["miners_camp"] = table(
    "miners_camp",
    pool((3, 6), item("raw_iron", 8, (1, 4)), item("coal", 8, (2, 6)), item("expanse:prismite_cluster", 6, (1, 3)),
         item("torch", 8, (2, 8)), item("bread", 6, (1, 3)), item("rail", 5, (2, 8)), item("cobblestone", 5, (4, 12)),
         item("raw_copper", 5, (2, 6)), item("iron_nugget", 5, (2, 8))),
    pool(1, empty(5), item("iron_pickaxe", 2, damage=(0.2, 0.7)), item("emerald", 2), item("tnt", 1)))
TABLES["miners_camp_hidden"] = table(
    "miners_camp_hidden",
    pool((3, 6), item("iron_ingot", 8, (2, 6)), item("gold_ingot", 6, (1, 4)),
         item("expanse:prismite_block", 4, (1, 3)),
         item("emerald", 6, (2, 5)), item("diamond", 3, (1, 2)), item("amethyst_shard", 4, (2, 6)),
         item("iron_pickaxe", 3, enchant=True), item("redstone", 4, (4, 10))),
    pool(1, empty(3), book(2), item("golden_apple", 1), item("diamond_pickaxe", 1), lore("miners_camp", 2)))
TABLES["miners_camp_archaeology"] = table(
    "miners_camp_archaeology", pool(1, item("gold_nugget", 5), item("iron_nugget", 3), item("raw_copper", 2),
                                    item("emerald"), item("expanse:prismite_cluster", 2), sherd("miner", 2),
                                    sherd("prize"), item("amethyst_shard", 2), item("diamond")), kind="archaeology")

TABLES["beach_hut"] = table(
    "beach_hut",
    pool((3, 6), item("cod", 8, (1, 4)), item("cooked_cod", 6, (1, 3)), item("salmon", 5, (1, 3)),
         item("expanse:cooked_crab", 6, (1, 3)), item("kelp", 6, (2, 6)), item("string", 5, (1, 4)),
         item("bamboo", 4, (2, 6)), item("expanse:palm_sapling", 3), item("glass_bottle", 4, (1, 3)),
         item("sea_pickle", 3, (1, 3))),
    pool(1, empty(5), item("fishing_rod", 3, damage=(0.2, 0.8)), item("nautilus_shell", 1), item("emerald", 2)))
TABLES["beach_hut_hidden"] = table(
    "beach_hut_hidden",
    pool((2, 5), item("gold_ingot", 6, (1, 4)), item("emerald", 8, (2, 5)), item("nautilus_shell", 4, (1, 2)),
         item("prismarine_crystals", 4, (2, 5)), item("turtle_scute", 2), item("diamond", 2),
         item("gold_nugget", 6, (4, 12))),
    pool(1, empty(3), item("heart_of_the_sea", 1), book(2), item("golden_apple", 1), lore("beach_hut", 2)))

TABLES["summit_cairn"] = table(
    "summit_cairn",
    pool((2, 5), item("bread", 8, (1, 3)), item("expanse:edelweiss", 6, (1, 3)), item("paper", 6, (1, 4)),
         item("feather", 4, (1, 3)), item("torch", 5, (2, 5)), item("leather", 4, (1, 2)), item("string", 4, (1, 3)),
         item("snowball", 3, (2, 6)), item("cooked_mutton", 4, (1, 2))),
    pool(1, empty(4), item("spyglass", 2), item("compass", 2), lore("summit_cairn", 3)))
TABLES["summit_cairn_hidden"] = table(
    "summit_cairn_hidden",
    pool((2, 5), item("lead", 6, (1, 2)), item("golden_carrot", 5, (2, 5)), item("emerald", 6, (2, 4)),
         item("iron_ingot", 5, (1, 3)), item("leather_boots", 3, enchant=True), item("iron_pickaxe", 2, enchant=True),
         item("honey_bottle", 4, (1, 2)), item("diamond", 1)),
    pool(1, empty(3), book(2), item("golden_apple", 2), item("name_tag", 1)))


def _well_tables(name, flavor):
    TABLES[name] = table(
        name,
        pool((2, 5), item("bucket", 4), item("bread", 8, (1, 3)), item("string", 6, (1, 3)), item("candle", 5, (1, 2)),
             item("glass_bottle", 5, (1, 3)), item("iron_nugget", 6, (2, 6)), item("gold_nugget", 6, (1, 5)), *flavor),
        pool(1, empty(6), item("water_bucket", 2), item("emerald", 2)))
    TABLES[f"{name}_hidden"] = table(
        f"{name}_hidden",
        pool((3, 6), item("gold_nugget", 12, (4, 12)), item("iron_nugget", 8, (4, 10)), item("emerald", 8, (1, 4)),
             item("gold_ingot", 3, (1, 2)), item("copper_ingot", 4, (2, 5)), item("diamond", 1)),
        pool(1, empty(4), item("golden_apple", 1), item("name_tag", 2), item("music_disc_otherside", 1), book(2)))


_well_tables("old_well", [item("expanse:heather", 4, (1, 3)), item("apple", 4, (1, 2))])
_well_tables("old_well_limestone", [item("bamboo", 4, (2, 5)), item("lily_pad", 3, (1, 2))])
_well_tables("old_well_sandstone", [item("cactus", 3, (1, 2)), item("dried_kelp", 4, (2, 5))])
_well_tables("old_well_mud", [item("expanse:cattail", 4, (1, 3)), item("slime_ball", 3, (1, 2))])
TABLES["old_well_archaeology"] = table(
    "old_well_archaeology", pool(1, item("gold_nugget", 5), item("iron_nugget", 3), item("bone", 3), item("emerald", 2),
                                 sherd("mourner", 2), sherd("heart"), sherd("friend"), item("copper_ingot", 2),
                                 item("diamond")), kind="archaeology")

TABLES["graveyard"] = table(
    "graveyard",
    pool((2, 5), item("bone", 8, (1, 4)), item("candle", 8, (1, 3)), item("iron_shovel", 2, damage=(0.3, 0.8)),
         item("rotten_flesh", 4, (1, 3)), item("poppy", 5, (1, 3)), item("torch", 5, (2, 5)), item("string", 4, (1, 2)),
         item("bread", 4, (1, 2))),
    pool(1, empty(5), item("name_tag", 1), item("emerald", 2)))
TABLES["graveyard_hidden"] = table(
    "graveyard_hidden",
    pool((3, 6), item("gold_ingot", 8, (1, 4)), item("gold_nugget", 8, (4, 10)), item("emerald", 6, (2, 5)),
         item("iron_sword", 3, enchant=True), item("golden_helmet", 2), item("clock", 2),
         item("amethyst_shard", 3, (2, 4)),
         item("diamond", 1)),
    pool(1, empty(2), book(4), item("golden_apple", 2), item("music_disc_11", 1), lore("graveyard", 2)))
TABLES["graveyard_archaeology"] = table(
    "graveyard_archaeology", pool(1, item("bone", 5), item("gold_nugget", 3), item("iron_nugget", 2),
                                  sherd("mourner", 3), sherd("skull", 2), sherd("heartbreak"), item("emerald"),
                                  item("golden_carrot")), kind="archaeology")
