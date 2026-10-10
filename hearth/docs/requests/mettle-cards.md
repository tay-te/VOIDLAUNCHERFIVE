# Requests from VOID Mettle: cards v2

VOID Mettle's perks are becoming **cards** (`mettle/docs/CARDS.md`): every kept draft is a unique, rolled card with a
world-unique id, kept in a virtual binder (never an item). Players slot a few per skill, rebuild their decks at a cost,
and trade cards with each other. These requests follow the six in `mettle.md` (all done 2026-10-07) and keep the same
split, agreed with the user on 2026-10-03: **Mettle owns the numbers and the cards; Hearth owns how the world reacts,
its markets, residents and coin.** Mettle calls the api only, from `compat.hearth`, and only when Hearth is loaded.

None of these block Hearth's own work. Mettle's card trading works face to face without them; the broker and the world
reactions wait for them.

## 7. Virtual goods at a market: the card broker

**What.** A way for another mod to list things that are not items at a Hearth market, and a **card broker** who lists
players' cards in towns and cities. In `api/`:

```java
/** Goods that live outside inventories (VOID Mettle's cards). The owning mod keeps them; Hearth only lists them. */
public interface VirtualGoods {
	String kind();                                            // "mettle:card"
	List<Long> listable(ServerPlayer seller);                 // what the seller may list now
	Component name(long id);                                  // "Tunneler · Rare"
	List<Component> describe(long id);                        // tooltip lines (traits, requirement, history)
	ItemStack display(long id);                               // a display-only stack Hearth can draw (never given out)
	boolean hold(long id, ServerPlayer seller, long listing); // take it into escrow; false if it can't be listed
	void release(long id);                                    // listing withdrawn or expired: back to the seller
	boolean transfer(long id, UUID buyer);                    // sold: give it to the buyer (who may be offline)
}
HearthServices.addVirtualGoods(VirtualGoods)
```

Hearth owns the rest:
- a **broker** in towns and cities (a new `JobType.BROKER`, or a merchant or trader at a "card table" in the market
  hall: Hearth's choice of building and dialogue);
- the **listing board**: the seller sets a price; a listing fee and the town's tax on a sale go to the settlement;
  listings expire after Hearth's own term and the card goes back;
- **coin escrow** and paying a seller who is away (to their bank, or held at the broker until they call);
- the screen, drawing each card with `display` and `describe`; a search by `name`.

**Why.** The user wants players to sell cards for coin, not only trade face to face. Markets, folk and coin are
Hearth's; Mettle only knows who owns a card. Mettle's rules for a listed card: it must be in the seller's binder (not
slotted, not attuning); while listed it can't be slotted or traded; a buyer gets it attuning for 20 minutes. NPCs never
sell cards of their own.

## 8. A player-to-player coin step for trades

**What.** One atomic call:

```java
/** Moves coin from one player to another (making change); false and nothing moves if the payer can't. */
default boolean transfer(ServerPlayer from, ServerPlayer to, long bits)
```

**Why.** Face-to-face trades put coin beside cards and items. Mettle can do it today as `charge(from)` then `pay(to)`
in one tick, but `pay` drops coins that don't fit, and the trade screen should refuse a trade the receiver can't hold
instead of dropping coin at their feet. A `canReceive(player, bits)` check would do as well.

## 9. A standing event for "Honoured"

**What.** An api event when a player's standing with a settlement changes band:
`(ServerPlayer, Settlement, Identifier from, Identifier to)`, or just when they first reach the top band of a city.

**Why.** The world-first Mythic **The Open Hand** goes to the first player Honoured in a Hearth city. Mettle only
needs to hear about it.

## 10. Folk who know a legend

**What.** Hearth dialogue may read a player's Mythics through the player-skills slot:

```java
/** Mythic cards the player holds ("mettle:mythic/dragonfall"); empty when unknown. */
default List<Identifier> titles(ServerPlayer player) { return List.of(); }
```

and greet them by it ("You're the one who brought down the dragon"), or let it move opinion a little. Entirely Hearth's
call how, or whether.

**Why.** Mythics are public by design: the world should notice. Mettle owns the card; Hearth owns the reaction.

## 11. The trainer's rebuild option (FYI, no change needed)

Mettle's own `Trainers` provider will add "Rebuild your Mining deck · fee" in the training band (300–399), charging
with `EconomyService.charge(player, settlement, trainer, fee)`, like lessons. Nothing new is asked; this is so Hearth
knows the band is getting a second option per trainer.
