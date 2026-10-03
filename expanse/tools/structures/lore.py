"""In-world writing: one journal per structure (lecterns, hidden chests) and sign texts.

Pages are kept short (well under a book page's ~14 lines) and titles under
the 32 character limit of WrittenBookContent.
"""

BOOKS = {
    "stone_circle": {
        "title": "The Barrow on the Moor", "author": "Ysolde, warden",
        "pages": [
            "The heather blooms purple here only because the old ones were laid beneath it. We raised the stones "
            "to count the summers, one stone for every chieftain sleeping under the moor.",
            "Do not move the altar stone. Under the gravel the stair goes down to the barrow, and the barrow does "
            "not sleep as soundly as the moor does.",
            "If you must go down, bring light and leave an edelweiss on the altar. They are fond of white flowers. "
            "They are less fond of thieves.",
        ]},
    "ruined_watchtower": {
        "title": "Last Watch Log", "author": "Sergeant Brannoc",
        "pages": [
            "Day 212. Fog off the peaks again. Lit the beacon brazier at dusk as ordered. No relief column. "
            "The limestone sweats in this damp; mortar on the north-east face is cracking.",
            "Day 230. Half the garrison gone down to the valley. I locked the armoury behind the iron door. The "
            "lever is where the old captain hid it: under the stair, by the barrels.",
            "Day 241. The corner came down in the night with a sound like thunder. Nobody hurt. I am staying. "
            "Someone must keep the light on the top floor.",
        ]},
    "woodland_lodge": {
        "title": "Hunter's Almanac", "author": "Old Maren",
        "pages": [
            "The redwoods were old when my grandmother's grandmother cut this clearing. We take only fallen timber "
            "and one elk each winter. The forest remembers who is greedy.",
            "Smoked venison keeps until spring if the cellar stays cold. Mind the rug by the hearth: the hatch "
            "under it sticks in wet weather.",
            "Bees in the meadow hives, honey by midsummer. Saplings for any traveller who asks; plant them where a "
            "giant has fallen, it is only polite.",
        ]},
    "sun_shrine": {
        "title": "Hymn to the Pink Sun", "author": "The last priestess",
        "pages": [
            "When the dunes were young the sun rose rose-coloured over the opal sand, and we built it a house "
            "of the same stone so it would know where to return each morning.",
            "The offerings were sealed under the sun disc in the sanctum floor. The disc lifts for the faithful. "
            "For everyone else, there is a pickaxe.",
            "The wind buries the courtyard a little more every year. Brush the sand gently - our names are written "
            "in what lies beneath.",
        ]},
    "stilt_hamlet": {
        "title": "Tide Book of Willowmere", "author": "Fen the net-mender",
        "pages": [
            "We built on stilts because the bayou rises without warning, and because the crabs cannot climb "
            "willow. Mostly they cannot.",
            "Catch for the season: cod, salmon, more crabs than anyone wanted. The moss on the eaves keeps the "
            "smokehouse cool. The good nets are in the storehouse.",
            "Our savings are kept under the boards, where Grandmother hid them from the tax-taker. Lift the "
            "trapdoor by the barrels, mind the gap.",
        ]},
    "karst_pagoda": {
        "title": "Sutra of the Stone Forest", "author": "Abbot Lin of the karst",
        "pages": [
            "The limestone pillars grow one grain a century. We built the tower to grow faster, so the bell could "
            "be heard over the river mist.",
            "Each storey is a stage of the climb: the hall of incense, the library of rain, the treasury of quiet. "
            "Visitors may read; borrowers are struck by the bell.",
            "The scroll room behind the tall shelves holds the oldest sutras. Wisteria blooms when the abbot is "
            "pleased. It has not bloomed this year.",
        ]},
    "lighthouse": {
        "title": "Keeper's Logbook", "author": "Keeper Isla Tamsin",
        "pages": [
            "Lamp lit at dusk, trimmed at midnight, out at dawn. Three ships passed the palm reef safely. The crabs "
            "have taken up residence under the jetty again.",
            "The cartographer left a chart before he sailed. It marks a place far inland where they say a stone "
            "tower rises among the karst pillars. I have pinned it in the map drawer.",
            "Storm last night. If the light ever goes out, the spare sea lanterns are under the cottage floor. "
            "Keep the jetty boat bailed.",
        ]},
    "lumen_shrine": {
        "title": "Notes on Luminous Wood", "author": "Alchemist Vey",
        "pages": [
            "The lumen trees drink moonlight and give it back as a glow. The prismite under their roots does the "
            "same with heat. I built my still beside the old shrine to study both.",
            "Glowcap tincture: one cap, one bottle of water, a pinch of prismite. Night-sight for an hour, bad "
            "dreams for a week. My best work is kept behind the vines.",
            "The shrine was a ruin when I came. Whoever raised the spire wanted to be seen from very far away. I "
            "think they were afraid of the dark.",
        ]},
    "frost_outpost": {
        "title": "Tusk Season", "author": "Hrolf of the frost camp",
        "pages": [
            "Mammoths cross the tundra twice a year. We follow at a respectful distance with spears we hope never "
            "to use. The arch at the gate is from one that died of old age. Honour it.",
            "Smithy fire must never go out: cold iron splits. Spare blades and good leather are in the ice cellar. "
            "Look for the hatch under the bear rug.",
            "Frostbloom only opens where the snow is deepest. Pick one for luck before the long walk south.",
        ]},
    "nomad_camp": {
        "title": "Songs of the Amber Road", "author": "Teller Sarnai",
        "pages": [
            "We follow the grass and the grass follows the rain. Where the amber steppe turns gold we stop, raise "
            "the round tents, and dye the wool in the colours of the sunset.",
            "Every caravan keeps a strongbox in the wagon of the eldest. Ours rides under the bench, behind the "
            "red cloth. Do not tell the horses.",
            "Sit by the fire, stranger. A song for a story, a story for a cup of milk. Nobody leaves the camp "
            "hungry.",
        ]},
    "cloud_monastery": {
        "title": "The Cloud Rule", "author": "Brother Anselm",
        "pages": [
            "Rise before the mist. Ring the bell so the valley knows we live. Tend the terraces, copy one page, "
            "speak to the jungle only when it speaks first.",
            "The great library is open to all who wash their hands. The enchanting desk may be used by any who "
            "can read the old script. The bookcase that sounds hollow is not for novices.",
            "We built high so the clouds would carry our prayers. Now the jungle climbs as fast as we do. Keep "
            "the tower taller than the trees.",
        ]},
}
