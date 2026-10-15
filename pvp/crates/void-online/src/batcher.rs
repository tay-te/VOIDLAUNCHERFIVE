//! The debounced cosmetics batcher behind [`crate::Online::request_cosmetics`].
//!
//! The mod sends `cosmetics_query` whenever players come into view, often a handful at a time
//! within a few frames of each other. The batcher turns that drizzle into few requests:
//!
//! 1. The first request opens a window of [`DEBOUNCE`]. Everything that arrives inside it is
//!    merged into one de-duplicated set. The window does not slide, so a steady trickle still
//!    gets an answer every 150 ms instead of never.
//! 2. When it closes, UUIDs that became fresh in the cache meanwhile (another batch or a
//!    direct lookup answered them) are dropped.
//! 3. The rest is fetched in chunks of [`CHUNK`] (the `cosmetics-lookup` limit), one chunk at
//!    a time, and each answer is folded into the cache, misses included.
//! 4. Sets that are new or changed are emitted, once per chunk.
//!
//! A failed chunk is logged and dropped without caching anything, so the next query for those
//! players asks again. Requests that arrive while a fetch is in flight wait in the channel and
//! form the next window.

use std::collections::BTreeSet;
use std::future::Future;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use tokio::sync::mpsc;
use tokio::time::Instant;
use void_bridge::EquippedSet;

use crate::cache::CosmeticsCache;
use crate::Error;

/// How long the first request waits for company.
pub(crate) const DEBOUNCE: Duration = Duration::from_millis(150);
/// The most UUIDs one `cosmetics-lookup` takes.
pub(crate) const CHUNK: usize = 100;

/// Run until every sender is gone. `fetch` gets at most [`CHUNK`] dashed UUIDs; `emit` gets
/// each chunk's new or changed sets (never an empty list).
pub(crate) async fn run<F, Fut, E>(
    mut rx: mpsc::UnboundedReceiver<Vec<String>>,
    debounce: Duration,
    cache: Arc<Mutex<CosmeticsCache>>,
    mut fetch: F,
    mut emit: E,
) where
    F: FnMut(Vec<String>) -> Fut,
    Fut: Future<Output = Result<Vec<EquippedSet>, Error>>,
    E: FnMut(Vec<EquippedSet>),
{
    while let Some(first) = rx.recv().await {
        let mut pending: BTreeSet<String> = first.into_iter().collect();
        let deadline = Instant::now() + debounce;
        loop {
            tokio::select! {
                more = rx.recv() => match more {
                    Some(more) => pending.extend(more),
                    None => break,
                },
                _ = tokio::time::sleep_until(deadline) => break,
            }
        }

        let wanted: Vec<String> = {
            let now = Instant::now();
            let mut cache = cache.lock().unwrap();
            pending.into_iter().filter(|u| !cache.is_fresh(u, now)).collect()
        };
        for chunk in wanted.chunks(CHUNK) {
            match fetch(chunk.to_vec()).await {
                Ok(found) => {
                    let changed = cache.lock().unwrap().absorb(chunk, &found, Instant::now());
                    if !changed.is_empty() {
                        emit(changed);
                    }
                }
                Err(e) => tracing::warn!(error = %e, players = chunk.len(), "cosmetics lookup failed"),
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn uuid(n: usize) -> String {
        format!("00000000-0000-4000-8000-{n:012x}")
    }

    struct Harness {
        tx: mpsc::UnboundedSender<Vec<String>>,
        calls: Arc<Mutex<Vec<Vec<String>>>>,
        emitted: Arc<Mutex<Vec<Vec<EquippedSet>>>>,
        cache: Arc<Mutex<CosmeticsCache>>,
    }

    /// A batcher whose "server" says every even-numbered player is a VOID player wearing
    /// `cape`, read at fetch time so tests can change it.
    fn harness(cape: Arc<Mutex<Option<String>>>) -> Harness {
        let (tx, rx) = mpsc::unbounded_channel();
        let calls = Arc::new(Mutex::new(Vec::new()));
        let emitted = Arc::new(Mutex::new(Vec::new()));
        let cache = Arc::new(Mutex::new(CosmeticsCache::default()));
        let (c, e) = (calls.clone(), emitted.clone());
        tokio::spawn(run(
            rx,
            DEBOUNCE,
            cache.clone(),
            move |uuids: Vec<String>| {
                c.lock().unwrap().push(uuids.clone());
                let cape = cape.lock().unwrap().clone();
                async move {
                    Ok(uuids
                        .iter()
                        .filter(|u| u.ends_with(['0', '2', '4', '6', '8', 'a', 'c', 'e']))
                        .map(|u| EquippedSet { cape: cape.clone(), ..EquippedSet::empty(u.clone()) })
                        .collect())
                }
            },
            move |sets| e.lock().unwrap().push(sets),
        ));
        Harness { tx, calls, emitted, cache }
    }

    async fn settle() {
        // Let spawned work run to its next timer.
        for _ in 0..10 {
            tokio::task::yield_now().await;
        }
    }

    #[tokio::test(start_paused = true)]
    async fn requests_inside_the_window_are_merged_and_deduplicated() {
        let h = harness(Arc::new(Mutex::new(None)));
        h.tx.send(vec![uuid(1), uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(100)).await;
        h.tx.send(vec![uuid(2), uuid(3)]).unwrap();
        settle().await;
        assert!(h.calls.lock().unwrap().is_empty(), "still inside the window");

        tokio::time::sleep(Duration::from_millis(60)).await;
        settle().await;
        assert_eq!(*h.calls.lock().unwrap(), vec![vec![uuid(1), uuid(2), uuid(3)]]);
        assert_eq!(*h.emitted.lock().unwrap(), vec![vec![EquippedSet::empty(uuid(2))]]);
    }

    #[tokio::test(start_paused = true)]
    async fn the_window_does_not_slide() {
        let h = harness(Arc::new(Mutex::new(None)));
        for i in 0..10 {
            h.tx.send(vec![uuid(i)]).unwrap();
            tokio::time::sleep(Duration::from_millis(40)).await;
        }
        settle().await;
        // 400 ms of a steady trickle: answered in windows, not starved until it stops.
        assert!(h.calls.lock().unwrap().len() >= 2);
    }

    #[tokio::test(start_paused = true)]
    async fn large_batches_are_chunked_by_100() {
        let h = harness(Arc::new(Mutex::new(None)));
        h.tx.send((0..250).map(uuid).collect()).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        let sizes: Vec<usize> = h.calls.lock().unwrap().iter().map(Vec::len).collect();
        assert_eq!(sizes, vec![100, 100, 50]);
        let emitted: usize = h.emitted.lock().unwrap().iter().map(Vec::len).sum();
        assert_eq!(emitted, 125);
    }

    #[tokio::test(start_paused = true)]
    async fn fresh_answers_and_misses_are_not_asked_again_until_the_ttl() {
        let cape = Arc::new(Mutex::new(None));
        let h = harness(cape.clone());
        h.tx.send(vec![uuid(1), uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        assert_eq!(h.calls.lock().unwrap().len(), 1);

        // uuid(1) was a miss, uuid(2) a hit: both are fresh, so nothing is fetched.
        h.tx.send(vec![uuid(1), uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        assert_eq!(h.calls.lock().unwrap().len(), 1);
        assert!(h.cache.lock().unwrap().get_fresh(&uuid(1), Instant::now()) == Some(None));

        // After the TTL both are asked again; uuid(2) changed and is emitted, unchanged would not be.
        tokio::time::sleep(crate::cache::TTL).await;
        *cape.lock().unwrap() = Some("void-cape-rift".to_string());
        h.tx.send(vec![uuid(1), uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        assert_eq!(h.calls.lock().unwrap().len(), 2);
        let emitted = h.emitted.lock().unwrap();
        assert_eq!(emitted.len(), 2);
        assert_eq!(emitted[1][0].cape.as_deref(), Some("void-cape-rift"));
    }

    #[tokio::test(start_paused = true)]
    async fn unchanged_sets_are_not_re_emitted_after_the_ttl() {
        let h = harness(Arc::new(Mutex::new(None)));
        h.tx.send(vec![uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        tokio::time::sleep(crate::cache::TTL).await;
        h.tx.send(vec![uuid(2)]).unwrap();
        tokio::time::sleep(Duration::from_millis(200)).await;
        settle().await;
        assert_eq!(h.calls.lock().unwrap().len(), 2, "stale, so asked again");
        assert_eq!(h.emitted.lock().unwrap().len(), 1, "but the same answer is not news");
    }
}
