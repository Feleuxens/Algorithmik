use rayon::prelude::*;

use crate::graph::CHGraph;
use crate::models::BoolArray;

#[derive(Clone, Copy)]
pub struct ClEdge {
    pub target: u32,
    pub weight: u32,
}

/// The precomputed forward/backward distance closure, stored as node-id-indexed CSR.
pub struct ClosureGraph {
    pub num_groups: u32,
    /// group index per node (indexed by node id). k <= 255.
    pub group: Vec<u8>,

    pub fwd_offsets: Vec<u32>, // len num_nodes + 1
    pub fwd_edges: Vec<ClEdge>,

    pub bwd_offsets: Vec<u32>,
    pub bwd_edges: Vec<ClEdge>,
}

impl ClosureGraph {
    /// Build the k-DGP closure from a finished CH.
    ///
    /// `separators` are rank thresholds in ascending order; `num_groups =
    /// separators.len() + 1`. Group of a node with rank `r` is the number of
    /// thresholds `<= r`, i.e. group 0 = ranks `[0, separators[0])`, group 1 =
    /// `[separators[0], separators[1])`, ... (low ranks = low/"unimportant" group).
    pub fn build(ch: &CHGraph, separators: &[u32]) -> Self {
        let n = ch.get_number_of_nodes() as usize;
        let num_groups = (separators.len() + 1) as u32;
        assert!(num_groups <= 256, "k must be <= 256 (group stored as u8)");

        // group per node, derived from its rank.
        let mut group = vec![0u8; n];
        for node in 0..n {
            let r = ch.ranks[node];
            group[node] = separators.partition_point(|&t| t <= r) as u8;
        }

        // Bucket nodes by CH level. Nodes in the same level form an independent
        // set (no edges among them at contraction time), so every CH edge of a
        // node points strictly upward in *level*. Processing levels high->low.
        let max_level = *ch.levels.iter().max().unwrap_or(&0);
        let mut level_buckets: Vec<Vec<u32>> = vec![Vec::new(); (max_level + 1) as usize];
        for node in 0..n {
            level_buckets[ch.levels[node] as usize].push(node as u32);
        }

        let mut dc_fwd: Vec<Vec<ClEdge>> = vec![Vec::new(); n];
        let mut dc_bwd: Vec<Vec<ClEdge>> = vec![Vec::new(); n];

        for level in (0..=max_level).rev() {
            let nodes = &level_buckets[level as usize];
            // Read-only borrow of dc_* during the parallel map; mutate afterward.
            let results: Vec<(u32, Vec<ClEdge>, Vec<ClEdge>)> = nodes
                .par_iter()
                .map(|&v| {
                    let f = build_closure_fwd(ch, &group, &dc_fwd, v);
                    let b = build_closure_bwd(ch, &group, &dc_bwd, v);
                    (v, f, b)
                })
                .collect();
            for (v, f, b) in results {
                dc_fwd[v as usize] = f;
                dc_bwd[v as usize] = b;
            }
        }

        // Flatten one direction at a time, dropping the per-node Vec storage as
        // soon as it is consumed to keep the transient memory peak down.
        let (fwd_offsets, fwd_edges) = flatten(&dc_fwd);
        drop(dc_fwd);
        let (bwd_offsets, bwd_edges) = flatten(&dc_bwd);
        drop(dc_bwd);

        ClosureGraph {
            num_groups,
            group,
            fwd_offsets,
            fwd_edges,
            bwd_offsets,
            bwd_edges,
        }
    }

    pub fn num_edges(&self) -> usize {
        self.fwd_edges.len() + self.bwd_edges.len()
    }
}

fn flatten(dc: &[Vec<ClEdge>]) -> (Vec<u32>, Vec<ClEdge>) {
    let n = dc.len();
    let mut offsets = vec![0u32; n + 1];
    for v in 0..n {
        offsets[v + 1] = offsets[v] + dc[v].len() as u32;
    }
    let total = offsets[n] as usize;
    let mut edges = Vec::with_capacity(total);
    for v in 0..n {
        edges.extend_from_slice(&dc[v]);
    }
    (offsets, edges)
}

fn build_closure_fwd(ch: &CHGraph, group: &[u8], dc_fwd: &[Vec<ClEdge>], v: u32) -> Vec<ClEdge> {
    let gv = group[v as usize];
    let begin = ch.begin_out_edges(v) as usize;
    let end = ch.end_out_edges(v) as usize;
    let ch_edges = &ch.fwd_edges[begin..end];

    let mut cand: Vec<ClEdge> = Vec::with_capacity(ch_edges.len());
    for e in ch_edges {
        cand.push(ClEdge { target: e.target, weight: e.weight });
    }
    for e in ch_edges {
        if group[e.target as usize] == gv {
            let w_vu = e.weight;
            for ce in &dc_fwd[e.target as usize] {
                cand.push(ClEdge { target: ce.target, weight: w_vu + ce.weight });
            }
        }
    }
    dedup_min(&mut cand);
    cand
}

fn build_closure_bwd(ch: &CHGraph, group: &[u8], dc_bwd: &[Vec<ClEdge>], v: u32) -> Vec<ClEdge> {
    let gv = group[v as usize];
    let begin = ch.begin_in_edges(v) as usize;
    let end = ch.end_in_edges(v) as usize;
    let ch_edges = &ch.bwd_edges[begin..end];

    let mut cand: Vec<ClEdge> = Vec::with_capacity(ch_edges.len());
    for e in ch_edges {
        cand.push(ClEdge { target: e.target, weight: e.weight });
    }
    for e in ch_edges {
        if group[e.target as usize] == gv {
            let w_vu = e.weight;
            for ce in &dc_bwd[e.target as usize] {
                cand.push(ClEdge { target: ce.target, weight: w_vu + ce.weight });
            }
        }
    }
    dedup_min(&mut cand);
    cand
}

/// Sort by target and keep the minimum weight per target, in place.
fn dedup_min(cand: &mut Vec<ClEdge>) {
    if cand.len() <= 1 {
        return;
    }
    cand.sort_unstable_by_key(|c| c.target);
    let mut write = 0usize;
    for read in 1..cand.len() {
        if cand[read].target == cand[write].target {
            if cand[read].weight < cand[write].weight {
                cand[write].weight = cand[read].weight;
            }
        } else {
            write += 1;
            cand[write] = cand[read];
        }
    }
    cand.truncate(write + 1);
}

/// Reusable scratch for the bucket-based bidirectional closure query (Algorithm 1).
pub struct DGPQuery {
    dist_fwd: Vec<u32>,
    dist_bwd: Vec<u32>,
    valid_fwd: BoolArray,
    valid_bwd: BoolArray,
    queued_fwd: BoolArray,
    queued_bwd: BoolArray,
    buckets_fwd: Vec<Vec<u32>>,
    buckets_bwd: Vec<Vec<u32>>,
    touched_fwd: Vec<u32>,
    touched_bwd: Vec<u32>,
}

impl DGPQuery {
    pub fn new(num_nodes: usize, num_groups: usize) -> Self {
        DGPQuery {
            dist_fwd: vec![u32::MAX; num_nodes],
            dist_bwd: vec![u32::MAX; num_nodes],
            valid_fwd: BoolArray::new(num_nodes),
            valid_bwd: BoolArray::new(num_nodes),
            queued_fwd: BoolArray::new(num_nodes),
            queued_bwd: BoolArray::new(num_nodes),
            buckets_fwd: (0..num_groups).map(|_| Vec::with_capacity(64)).collect(),
            buckets_bwd: (0..num_groups).map(|_| Vec::with_capacity(64)).collect(),
            touched_fwd: Vec::with_capacity(1024),
            touched_bwd: Vec::with_capacity(1024),
        }
    }

    /// Shortest distance from `source` to `target`, or None if unreachable.
    pub fn query(&mut self, cg: &ClosureGraph, source: u32, target: u32) -> Option<u32> {
        if source == target {
            return Some(0);
        }
        self.search_fwd(cg, source);
        self.search_bwd(cg, target);

        // Meeting node: any node reached by both searches. Scan the smaller of
        // the two touched sets and probe the other's validity.
        let mut best = u32::MAX;
        if self.touched_fwd.len() <= self.touched_bwd.len() {
            for &v in &self.touched_fwd {
                let vi = v as usize;
                if self.valid_bwd.get(vi) {
                    let s = self.dist_fwd[vi].saturating_add(self.dist_bwd[vi]);
                    if s < best {
                        best = s;
                    }
                }
            }
        } else {
            for &v in &self.touched_bwd {
                let vi = v as usize;
                if self.valid_fwd.get(vi) {
                    let s = self.dist_fwd[vi].saturating_add(self.dist_bwd[vi]);
                    if s < best {
                        best = s;
                    }
                }
            }
        }
        if best == u32::MAX {
            None
        } else {
            Some(best)
        }
    }

    fn search_fwd(&mut self, cg: &ClosureGraph, source: u32) {
        self.valid_fwd.clear();
        self.queued_fwd.clear();
        self.touched_fwd.clear();
        for b in &mut self.buckets_fwd {
            b.clear();
        }

        let gs = cg.group[source as usize] as usize;
        self.dist_fwd[source as usize] = 0;
        self.valid_fwd.set(source as usize);
        self.queued_fwd.set(source as usize);
        self.touched_fwd.push(source);
        self.buckets_fwd[gs].push(source);

        for i in gs..cg.num_groups as usize {
            // bucket i never grows during its own pass (we only push to gy > i).
            let mut idx = 0;
            while idx < self.buckets_fwd[i].len() {
                let a = self.buckets_fwd[i][idx];
                idx += 1;
                let da = self.dist_fwd[a as usize];
                let begin = cg.fwd_offsets[a as usize] as usize;
                let end = cg.fwd_offsets[a as usize + 1] as usize;
                for e in &cg.fwd_edges[begin..end] {
                    let y = e.target as usize;
                    let nd = da + e.weight;
                    if !self.valid_fwd.get(y) {
                        self.valid_fwd.set(y);
                        self.dist_fwd[y] = nd;
                        self.touched_fwd.push(e.target);
                    } else if nd < self.dist_fwd[y] {
                        self.dist_fwd[y] = nd;
                    }
                    let gy = cg.group[y] as usize;
                    if gy > i && !self.queued_fwd.get(y) {
                        self.queued_fwd.set(y);
                        self.buckets_fwd[gy].push(e.target);
                    }
                }
            }
        }
    }

    fn search_bwd(&mut self, cg: &ClosureGraph, target: u32) {
        self.valid_bwd.clear();
        self.queued_bwd.clear();
        self.touched_bwd.clear();
        for b in &mut self.buckets_bwd {
            b.clear();
        }

        let gt = cg.group[target as usize] as usize;
        self.dist_bwd[target as usize] = 0;
        self.valid_bwd.set(target as usize);
        self.queued_bwd.set(target as usize);
        self.touched_bwd.push(target);
        self.buckets_bwd[gt].push(target);

        for i in gt..cg.num_groups as usize {
            let mut idx = 0;
            while idx < self.buckets_bwd[i].len() {
                let a = self.buckets_bwd[i][idx];
                idx += 1;
                let da = self.dist_bwd[a as usize];
                let begin = cg.bwd_offsets[a as usize] as usize;
                let end = cg.bwd_offsets[a as usize + 1] as usize;
                for e in &cg.bwd_edges[begin..end] {
                    let y = e.target as usize;
                    let nd = da + e.weight;
                    if !self.valid_bwd.get(y) {
                        self.valid_bwd.set(y);
                        self.dist_bwd[y] = nd;
                        self.touched_bwd.push(e.target);
                    } else if nd < self.dist_bwd[y] {
                        self.dist_bwd[y] = nd;
                    }
                    let gy = cg.group[y] as usize;
                    if gy > i && !self.queued_bwd.get(y) {
                        self.queued_bwd.set(y);
                        self.buckets_bwd[gy].push(e.target);
                    }
                }
            }
        }
    }
}

/// Convert separators expressed as percentages of the maximum rank into rank thresholds
pub fn separators_from_percentages(num_nodes: u32, pct: &[f64]) -> Vec<u32> {
    if num_nodes == 0 {
        return Vec::new();
    }
    let max_rank = (num_nodes - 1) as f64;
    let mut sep: Vec<u32> = pct
        .iter()
        .map(|p| (p / 100.0 * max_rank).round() as u32)
        .collect();
    sep.sort_unstable();
    sep.dedup();
    sep
}
