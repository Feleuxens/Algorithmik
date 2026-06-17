//! Three strategies for intersecting two *sorted* slices.
//!
//! All functions assume `a` and `b` are sorted in strictly increasing order
//! (no duplicates). They are generic over `T: Ord + Copy` so the same code can
//! intersect different types.

use std::cmp::Ordering;

/// Naive linear merge, like the merge step of mergesort.
/// Time: O(|a| + |b|), sequential / cache-friendly access.
pub fn intersect_merge<T: Ord + Copy>(a: &[T], b: &[T]) -> Vec<T> {
    let mut out = Vec::with_capacity(a.len().min(b.len()));
    let (mut i, mut j) = (0usize, 0usize);
    while i < a.len() && j < b.len() {
        match a[i].cmp(&b[j]) {
            Ordering::Less => i += 1,
            Ordering::Greater => j += 1,
            Ordering::Equal => {
                out.push(a[i]);
                i += 1;
                j += 1;
            }
        }
    }
    out
}

/// Binary search in `a` for every element of `b`.
/// Time: O(|b| * log|a|). Latency-bound: each probe is a random access into
/// `a`, and the probes form a dependent chain, so this is dominated by cache
/// misses when `a` is large.
pub fn intersect_binary<T: Ord + Copy>(a: &[T], b: &[T]) -> Vec<T> {
    let mut out = Vec::with_capacity(a.len().min(b.len()));
    for &x in b {
        if a.binary_search(&x).is_ok() {
            out.push(x);
        }
    }
    out
}

/// Galloping (exponential) search.
///
/// Like binary search, but exploits that `b` is sorted: we never restart from
/// the front of `a`. A cursor `pos` marks the start of the still-relevant tail
/// of `a`; for each `x` we probe `pos, pos+1, pos+2, pos+4, ...` until we
/// overshoot, then binary-search the last gap. The cursor then goes to the
/// lower bound of `x`, so the next (larger) element of `b` searches an even
/// shorter tail.
///
/// Time: roughly O(|b| * log(|a| / |b|)). When `b` is dense the gaps are ~1, and
/// it behaves like the merge; when `b` is sparse it behaves like binary search
/// but cheaper.
pub fn intersect_gallop<T: Ord + Copy>(a: &[T], b: &[T]) -> Vec<T> {
    let mut out = Vec::with_capacity(a.len().min(b.len()));
    let mut pos = 0usize; // start of the unsearched tail of `a`

    for &x in b {
        if pos >= a.len() {
            break; // all remaining b elements are > max(a)
        }
        let rest = &a[pos..];

        // Exponential probe: find a `bound` with rest[bound] >= x (or hit end).
        let mut bound = 1usize;
        while bound < rest.len() && rest[bound] < x {
            bound <<= 1;
        }

        // Binary search the gap (lo, hi] for the lower bound of x.
        let lo = bound >> 1;
        let hi = rest.len().min(bound + 1);
        let idx = lo + rest[lo..hi].partition_point(|&v| v < x);

        if idx < rest.len() && rest[idx] == x {
            out.push(x);
        }
        // idx is the lower bound of x within `rest`; the next b element is >= x,
        // so it cannot lie before this position. Advancing by `idx` (not idx+1)
        // is always safe even if x was not found.
        pos += idx;
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn reference(a: &[i32], b: &[i32]) -> Vec<i32> {
        a.iter().copied().filter(|x| b.contains(x)).collect()
    }

    fn check(a: &[i32], b: &[i32]) {
        let expected = reference(a, b);
        assert_eq!(intersect_merge(a, b), expected);
        assert_eq!(intersect_binary(a, b), expected);
        assert_eq!(intersect_gallop(a, b), expected);
    }

    #[test]
    fn basic() {
        check(&[], &[]);
        check(&[1, 2, 3], &[]);
        check(&[], &[1, 2, 3]);
        check(&[1, 2, 3, 4, 5], &[2, 4, 6]);
        check(&[1, 2, 3], &[1, 2, 3]);
        check(&[1, 5, 9], &[2, 3, 4]); // disjoint
        check(&[10, 20, 30], &[5, 10, 30, 40]); // first & last match
        check(&[1], &[1]);
        check(&[1], &[2]);
    }

    #[test]
    fn larger_randomized() {
        // Deterministic pseudo-random sorted/deduped inputs.
        let mut s = 0x2545F4914F6CDD1Du64;
        let mut next = || {
            s ^= s << 13;
            s ^= s >> 7;
            s ^= s << 17;
            s
        };
        for _ in 0..200 {
            let mut a: Vec<i32> = (0..100).map(|_| (next() % 500) as i32).collect();
            let mut b: Vec<i32> = (0..100).map(|_| (next() % 500) as i32).collect();
            a.sort_unstable();
            a.dedup();
            b.sort_unstable();
            b.dedup();
            check(&a, &b);
        }
    }
}