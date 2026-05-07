use std::collections::BinaryHeap;
use crate::models::BoolArray;
use crate::graph::CHGraph;
use crate::witness_search::WitnessNode;

pub struct BiDijkstra {
    fwd_data: Vec<Data>,
    bwd_data: Vec<Data>,
    fwd_valid: BoolArray,
    bwd_valid: BoolArray,
    fwd_heap: BinaryHeap<WitnessNode>,
    bwd_heap: BinaryHeap<WitnessNode>,
}

struct Data {
    visited: bool,
    weight: u32,
}

impl Data {
    fn new() -> Self {
        Data {
            visited: false,
            weight: u32::MAX,
        }
    }
}

impl BiDijkstra {
    pub fn new(num_nodes: usize) -> Self {
        println!("Preparing Bidirectional Dijkstra");

        let dijkstra = BiDijkstra {
            fwd_data: (0..num_nodes).map(|_i| Data::new()).collect(),
            bwd_data: (0..num_nodes).map(|_i| Data::new()).collect(),
            fwd_valid: BoolArray::new(num_nodes),
            bwd_valid: BoolArray::new(num_nodes),
            fwd_heap: BinaryHeap::with_capacity(300),
            bwd_heap: BinaryHeap::with_capacity(300),
        };
        dijkstra
    }

    pub fn query(&mut self, graph: &CHGraph, source: u32, target: u32) -> Option<u32> {
        self.fwd_heap.clear();
        self.bwd_heap.clear();
        self.fwd_valid.clear();
        self.bwd_valid.clear();

        let mut min_weight = u32::MAX;
        let mut meeting_node = u32::MAX;

        self.update_node_fwd(source as usize, 0);
        self.fwd_heap.push(WitnessNode::new(source, 0));
        self.update_node_bwd(target as usize, 0);
        self.bwd_heap.push(WitnessNode::new(target, 0));

        while !self.fwd_heap.is_empty() || !self.bwd_heap.is_empty() {
            while let Some(current_witness) = self.fwd_heap.pop() {
                if self.is_visited_fwd(current_witness.id as usize) {
                    continue;
                }

                if current_witness.weight > min_weight {
                    break;
                }

                // stall on demand optimization
                if self.is_stallable_fwd(graph, current_witness) {
                    continue;
                }

                self.fwd_data[current_witness.id as usize].visited = true;
                let begin_idx = graph.begin_out_edges(current_witness.id);
                let end_idx = graph.end_out_edges(current_witness.id);
                for edge_id in begin_idx..end_idx {
                    let t = graph.fwd_edges[edge_id as usize].target as usize;
                    if graph.levels[t] <= graph.levels[current_witness.id as usize] {
                        // not higher level
                        continue;
                    }

                    let edge_weight = graph.fwd_edges[edge_id as usize].weight;
                    let weight = current_witness.weight + edge_weight;
                    if weight < self.get_weight_fwd(t) {
                        self.update_node_fwd(t, weight);
                        self.fwd_heap.push(WitnessNode::new(t as u32, weight));
                    }
                }

                if self.bwd_valid.get(current_witness.id as usize) {
                    let weight = current_witness.weight + self.get_weight_bwd(current_witness.id as usize);

                    if weight < min_weight {
                        min_weight = current_witness.weight + self.get_weight_bwd(current_witness.id as usize);
                        meeting_node = current_witness.id;
                    }
                }
                break;
            }

            while let Some(current_witness) = self.bwd_heap.pop() {
                if self.is_visited_bwd(current_witness.id as usize) {
                    continue;
                }
                if current_witness.weight > min_weight {
                    break;
                }
                // stall on demand optimization
                if self.is_stallable_bwd(graph, current_witness) {
                    continue;
                }

                self.bwd_data[current_witness.id as usize].visited = true;
                let begin = graph.begin_in_edges(current_witness.id);
                let end = graph.end_in_edges(current_witness.id);
                for edge_id in begin..end {
                    let adj = graph.bwd_edges[edge_id as usize].target;
                    if graph.levels[adj as usize] <= graph.levels[current_witness.id as usize] {
                        // not higher level
                        continue;
                    }

                    let edge_weight = graph.bwd_edges[edge_id as usize].weight;
                    let weight = current_witness.weight + edge_weight;
                    if weight < self.get_weight_bwd(adj as usize) {
                        self.update_node_bwd(adj as usize, weight);
                        self.bwd_heap.push(WitnessNode::new(adj, weight));
                    }
                }
                if self.fwd_valid.get(current_witness.id as usize) {
                    let weight = current_witness.weight + self.get_weight_fwd(current_witness.id as usize);
                    if weight < min_weight {
                        min_weight = current_witness.weight + self.get_weight_fwd(current_witness.id as usize);
                        meeting_node = current_witness.id;
                    }
                }
                break;
            }
        }

        if meeting_node == u32::MAX {
            None
        } else {
            Some(min_weight)
        }
    }

    fn is_stallable_fwd(&self, graph: &CHGraph, current_witness: WitnessNode) -> bool {
        let begin = graph.begin_in_edges(current_witness.id);
        let end = graph.end_in_edges(current_witness.id);
        for edge_id in begin..end {
            let adj = graph.bwd_edges[edge_id as usize].target;
            let adj_weight = self.get_weight_fwd(adj as usize);
            if adj_weight == u32::MAX {
                continue;
            }
            let edge_weight = graph.bwd_edges[edge_id as usize].weight;
            if adj_weight + edge_weight < current_witness.weight {
                return true;
            }
        }
        false
    }

    fn is_stallable_bwd(&self, graph: &CHGraph, current_witness: WitnessNode) -> bool {
        let begin = graph.begin_out_edges(current_witness.id);
        let end = graph.end_out_edges(current_witness.id);
        for edge_id in begin..end {
            let adj = graph.fwd_edges[edge_id as usize].target;
            let adj_weight = self.get_weight_bwd(adj as usize);
            if adj_weight == u32::MAX {
                continue;
            }
            let edge_weight = graph.fwd_edges[edge_id as usize].weight;
            if adj_weight + edge_weight < current_witness.weight {
                return true;
            }
        }
        false
    }

    fn update_node_fwd(&mut self, node: usize, weight: u32) {
        self.fwd_valid.set(node);
        self.fwd_data[node].visited = false;
        self.fwd_data[node].weight = weight;
    }

    fn update_node_bwd(&mut self, node: usize, weight: u32) {
        self.bwd_valid.set(node);
        self.bwd_data[node].visited = false;
        self.bwd_data[node].weight = weight;
    }

    fn is_visited_fwd(&self, node: usize) -> bool {
        self.fwd_valid.get(node) && self.fwd_data[node].visited
    }

    fn is_visited_bwd(&self, node: usize) -> bool {
        self.bwd_valid.get(node) && self.bwd_data[node].visited
    }

    fn get_weight_fwd(&self, node: usize) -> u32 {
        if self.fwd_valid.get(node) {
            self.fwd_data[node].weight
        } else {
            u32::MAX
        }
    }

    fn get_weight_bwd(&self, node: usize) -> u32 {
        if self.bwd_valid.get(node) {
            self.bwd_data[node].weight
        } else {
            u32::MAX
        }
    }
}
