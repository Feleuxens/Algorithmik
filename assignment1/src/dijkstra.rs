use std::cmp::Reverse;
use std::collections::BinaryHeap;
use crate::graph::Graph;


pub struct Dijkstra<'a> {
    source: Option<u32>,
    graph: &'a Graph,
    dist: Vec<u32>,
    heap: BinaryHeap<Reverse<(u32, u32)>>,
}


impl<'a> Dijkstra<'a> {
    pub fn new(graph: &'a Graph) -> Self {
        Dijkstra {
            source: None,
            graph,
            dist: vec![u32::MAX; graph.num_nodes()],
            heap: BinaryHeap::new(),
        }
    }

    pub fn query(&mut self, source: u32, target: u32) -> Option<u32> {
        if self.source != Some(source) {
            self.dist.fill(u32::MAX);
            self.heap.clear();
            self.dist[source as usize] = 0;
            self.heap.push(Reverse((0, source)));
            self.source = Some(source);
        }

        if self.dist[target as usize] != u32::MAX {
            return Some(self.dist[target as usize]);
        }

        while let Some(Reverse((cost, u))) = self.heap.pop() {
            if u == target { return Some(cost); }

            if cost > self.dist[u as usize] { continue; }

            for edge in self.graph.out_edges(u) {
                let next_cost = cost + edge.weight;
                if next_cost < self.dist[edge.target as usize] {
                    self.dist[edge.target as usize] = next_cost;
                    self.heap.push(Reverse((next_cost, edge.target)));
                }
            }
        }

        None
    }
}
