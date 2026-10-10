"""Writes assets/mettle/sounds.json: the sound events of the card draft (docs/SOUNDS.md).

    python tools/sounds/events.py           # from mettle/

Synthesised layers point at Mettle's own files (tools/sounds/synth.py); vanilla layers point at Minecraft's own sound
files by name ("minecraft:fire/ignite"), so no vanilla audio is copied into the mod. The Java list of events
(character.draft.CardSounds.NAMES) must match; CardCuesTest checks it.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import SOUNDS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "mettle", "sounds.json"))

SUBTITLES = {
    "card.slide": "subtitles.mettle.card.deal",
    "card.flare": "subtitles.mettle.card.keep",
    "card.burn": "subtitles.mettle.card.burn",
    "card.riffle": "subtitles.mettle.card.shuffle",
    "card.wrong": "subtitles.mettle.card.cursed",
    "mythic.rumble": "subtitles.mettle.mythic.stir",
    "mythic.burst": "subtitles.mettle.mythic.reveal",
}

# event -> vanilla sound files (by name, never copied)
VANILLA = {
    "card.flip": ["item/book/open_flip1", "item/book/open_flip2", "item/book/open_flip3"],
    "card.put": ["item/book/close_put1", "item/book/close_put2"],
    "card.slot": [f"block/chiseled_bookshelf/insert{i}" for i in range(1, 5)],
    "card.slot_enchanted": [f"block/chiseled_bookshelf/insert_enchanted{i}" for i in range(1, 5)],
    "card.tap": [f"entity/itemframe/add_item{i}" for i in range(1, 5)],
    "card.turn": [f"entity/itemframe/rotate_item{i}" for i in range(1, 5)],
    "card.weight": [f"block/lodestone/place{i}" for i in range(1, 5)],
    "card.ignite": ["fire/ignite"],
    "card.fizz": ["random/fizz"],
    "card.ember": ["liquid/lavapop"],
    "card.enchant": [f"block/enchantment_table/enchant{i}" for i in range(1, 4)],
    "card.ominous": ["item/ominous_bottle/dispose"],
    "card.deny": ["item/bundle/insert_fail"],
    "mythic.dim": ["block/respawn_anchor/deplete1", "block/respawn_anchor/deplete2"],
    "mythic.heartbeat": [f"mob/warden/heartbeat_{i}" for i in range(1, 5)],
    "mythic.shard": [f"block/amethyst/break{i}" for i in range(1, 5)],
    "mythic.eye": [f"block/end_portal/eyeplace{i}" for i in range(1, 4)],
    "mythic.rise": ["block/beacon/activate"],
    "mythic.bloom": [f"block/respawn_anchor/set_spawn{i}" for i in range(1, 4)],
    "mythic.glimmer": ["block/amethyst/shimmer"],
}


def build():
    events = {}
    for name, (_, count) in SOUNDS.items():
        event = name.replace("/", ".")
        files = [f"mettle:{name}{v}" if count > 1 else f"mettle:{name}" for v in range(1, count + 1)]
        events[event] = {"sounds": files}
    for event, files in VANILLA.items():
        events[event] = {"sounds": [f"minecraft:{f}" for f in files]}
    for event, key in SUBTITLES.items():
        events[event]["subtitle"] = key
    return dict(sorted(events.items()))


def main():
    events = build()
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(events, f, indent=2)
        f.write("\n")
    print(f"{len(events)} events -> {OUT}")


if __name__ == "__main__":
    main()
