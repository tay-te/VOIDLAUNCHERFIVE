//! The other-players cosmetics cache: an LRU with a TTL that also remembers *misses*.
//!
//! The mod asks about every player it can see, and on a big server most of them are not VOID
//! players. `cosmetics-lookup` simply leaves those out of its answer, so without negative
//! entries every one of them would be asked about again on the next query. An entry is
//! therefore `Some(set)` (a VOID player wearing `set`) or `None` (we asked; not a VOID player),
//! and both kinds count as fresh for the TTL.
//!
//! Time is [`tokio::time::Instant`] so tests can drive it with a paused clock.

use std::collections::{BTreeMap, HashMap};
use std::time::Duration;

use tokio::time::Instant;
use void_bridge::EquippedSet;

/// How many players the launcher remembers.
pub(crate) const CAPACITY: usize = 2048;
/// How long an answer, either kind, is trusted.
pub(crate) const TTL: Duration = Duration::from_secs(5 * 60);

struct Entry {
    value: Option<EquippedSet>,
    fetched: Instant,
    /// Position in `order`; bigger is more recently used.
    tick: u64,
}

/// See the module docs.
pub(crate) struct CosmeticsCache {
    map: HashMap<String, Entry>,
    /// `tick → uuid`, oldest first, so eviction is `O(log n)`.
    order: BTreeMap<u64, String>,
    next_tick: u64,
    capacity: usize,
    ttl: Duration,
}

impl Default for CosmeticsCache {
    fn default() -> Self {
        CosmeticsCache::new(CAPACITY, TTL)
    }
}

impl CosmeticsCache {
    pub(crate) fn new(capacity: usize, ttl: Duration) -> Self {
        CosmeticsCache { map: HashMap::new(), order: BTreeMap::new(), next_tick: 0, capacity: capacity.max(1), ttl }
    }

    fn touch(&mut self, uuid: &str) {
        let tick = self.next_tick;
        if let Some(e) = self.map.get_mut(uuid) {
            self.order.remove(&e.tick);
            e.tick = tick;
            self.order.insert(tick, uuid.to_string());
            self.next_tick += 1;
        }
    }

    /// Is there an answer for `uuid` younger than the TTL? Counts as a use.
    pub(crate) fn is_fresh(&mut self, uuid: &str, now: Instant) -> bool {
        let fresh = self.map.get(uuid).is_some_and(|e| now.duration_since(e.fetched) < self.ttl);
        if fresh {
            self.touch(uuid);
        }
        fresh
    }

    /// The fresh answer for `uuid`: `Some(Some(set))`, `Some(None)` for a known non-VOID
    /// player, `None` when unknown or stale. Counts as a use.
    pub(crate) fn get_fresh(&mut self, uuid: &str, now: Instant) -> Option<Option<EquippedSet>> {
        if !self.is_fresh(uuid, now) {
            return None;
        }
        self.map.get(uuid).map(|e| e.value.clone())
    }

    /// The last set seen for `uuid`, fresh or stale. Does not count as a use.
    pub(crate) fn peek(&self, uuid: &str) -> Option<&EquippedSet> {
        self.map.get(uuid).and_then(|e| e.value.as_ref())
    }

    /// Record an answer. Returns whether it differs from what was there before (a new
    /// player, a changed set, or a change between VOID and not-VOID), whatever its age.
    pub(crate) fn insert(&mut self, uuid: &str, value: Option<EquippedSet>, now: Instant) -> bool {
        let tick = self.next_tick;
        self.next_tick += 1;
        let changed = match self.map.get_mut(uuid) {
            Some(e) => {
                let changed = e.value != value;
                self.order.remove(&e.tick);
                e.value = value;
                e.fetched = now;
                e.tick = tick;
                changed
            }
            None => {
                self.map.insert(uuid.to_string(), Entry { value, fetched: now, tick });
                true
            }
        };
        self.order.insert(tick, uuid.to_string());
        while self.map.len() > self.capacity {
            let Some((_, oldest)) = self.order.pop_first() else { break };
            self.map.remove(&oldest);
        }
        changed
    }

    /// Fold a lookup answer in. `requested` is what was asked (dashed), `found` what came
    /// back; a requested UUID missing from `found` is recorded as not a VOID player. Returns
    /// the sets that are new or changed, for [`crate::OnlineEvent::Cosmetics`].
    pub(crate) fn absorb(&mut self, requested: &[String], found: &[EquippedSet], now: Instant) -> Vec<EquippedSet> {
        let mut by_uuid: HashMap<String, &EquippedSet> = HashMap::new();
        for set in found {
            if let Some(u) = void_bridge::cosmetics::normalize_uuid(&set.uuid) {
                by_uuid.insert(u, set);
            }
        }
        let mut changed = Vec::new();
        for uuid in requested {
            let value = by_uuid.remove(uuid).map(|s| EquippedSet { uuid: uuid.clone(), ..s.clone() });
            if self.insert(uuid, value.clone(), now) {
                if let Some(set) = value {
                    changed.push(set);
                }
            }
        }
        // Anything the server volunteered that we did not ask for is still true.
        for (uuid, set) in by_uuid {
            let set = EquippedSet { uuid: uuid.clone(), ..set.clone() };
            if self.insert(&uuid, Some(set.clone()), now) {
                changed.push(set);
            }
        }
        changed
    }

    pub(crate) fn clear(&mut self) {
        self.map.clear();
        self.order.clear();
    }

    #[cfg(test)]
    pub(crate) fn len(&self) -> usize {
        self.map.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn set(uuid: &str, cape: Option<&str>) -> EquippedSet {
        EquippedSet { cape: cape.map(str::to_string), ..EquippedSet::empty(uuid) }
    }

    #[tokio::test(start_paused = true)]
    async fn answers_expire_after_the_ttl_both_kinds() {
        let mut c = CosmeticsCache::new(10, Duration::from_secs(300));
        let t0 = Instant::now();
        c.insert("a", Some(set("a", None)), t0);
        c.insert("b", None, t0);
        assert_eq!(c.get_fresh("a", t0), Some(Some(set("a", None))));
        assert_eq!(c.get_fresh("b", t0), Some(None), "a miss is a fresh answer too");
        assert_eq!(c.get_fresh("c", t0), None);

        let later = t0 + Duration::from_secs(299);
        assert!(c.is_fresh("a", later) && c.is_fresh("b", later));
        let expired = t0 + Duration::from_secs(300);
        assert!(!c.is_fresh("a", expired) && !c.is_fresh("b", expired));
        assert_eq!(c.peek("a"), Some(&set("a", None)), "stale sets stay peekable");
    }

    #[tokio::test(start_paused = true)]
    async fn least_recently_used_goes_first() {
        let mut c = CosmeticsCache::new(2, TTL);
        let now = Instant::now();
        c.insert("a", None, now);
        c.insert("b", None, now);
        assert!(c.is_fresh("a", now)); // a is now the most recent
        c.insert("c", None, now);
        assert_eq!(c.len(), 2);
        assert!(c.is_fresh("a", now));
        assert!(!c.is_fresh("b", now), "b was the least recently used");
        assert!(c.is_fresh("c", now));
    }

    #[tokio::test(start_paused = true)]
    async fn insert_reports_changes_only() {
        let mut c = CosmeticsCache::default();
        let now = Instant::now();
        assert!(c.insert("a", Some(set("a", None)), now), "new");
        assert!(!c.insert("a", Some(set("a", None)), now), "same");
        assert!(c.insert("a", Some(set("a", Some("void-cape-rift"))), now), "changed");
        assert!(c.insert("a", None, now), "VOID → not VOID is a change");
    }

    #[tokio::test(start_paused = true)]
    async fn absorb_caches_misses_and_returns_new_or_changed_sets() {
        let mut c = CosmeticsCache::default();
        let now = Instant::now();
        let a = "069a79f4-44e9-4726-a5be-fca90e38aaf5".to_string();
        let b = "853c80ef-3c37-49fd-aa49-938b674adae6".to_string();
        // The server may answer with an undashed or uppercase uuid; it is normalised.
        let found = vec![set("069A79F444E94726A5BEFCA90E38AAF5", Some("void-cape-rift"))];
        let changed = c.absorb(&[a.clone(), b.clone()], &found, now);
        assert_eq!(changed, vec![set(&a, Some("void-cape-rift"))]);
        assert_eq!(c.get_fresh(&b, now), Some(None));

        let again = c.absorb(&[a.clone(), b.clone()], &[set(&a, Some("void-cape-rift"))], now);
        assert!(again.is_empty(), "nothing changed");
    }
}
