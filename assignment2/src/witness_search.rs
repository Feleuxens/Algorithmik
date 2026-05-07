use crate::models::BoolArray;
use std::collections::BinaryHeap;
use crate::helper::{PreGraph};

#[derive(Clone, Copy)]
pub struct WitnessNode {
    pub id: u32,
    pub weight: u32,
}

impl WitnessNode {
    pub fn new(id: u32, weight: u32) -> Self {
        Self { id, weight }
    }
}

impl PartialEq for WitnessNode {
    fn eq(&self, other: &Self) -> bool {
        self.weight == other.weight
    }
}

impl Eq for WitnessNode {}

impl Ord for WitnessNode {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.weight.cmp(&other.weight)
    }
}

impl PartialOrd for WitnessNode {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(other.cmp(self))
    }
}

pub struct WitnessSearch {
    valid: BoolArray,
    data: Vec<Data>,

    heap: BinaryHeap<WitnessNode>,
    source: u32,
    avoid: u32,
    visited_nodes: u32,
}

impl Drop for WitnessSearch {
    fn drop(&mut self) {
        println!("WitnessSearch dropped");
    }
}

#[derive(Debug, Clone, Copy)]
struct Data {
    weight: u32,
    visited: bool,
}

impl Default for Data {
    fn default() -> Self {
        Self {
            weight: u32::MAX,
            visited: false,
        }
    }
}

impl WitnessSearch {
    pub fn new(node_count: usize) -> Self {
        Self {
            data: vec![Data::default(); node_count],
            valid: BoolArray::new(node_count),
            heap: BinaryHeap::with_capacity(200),
            source: u32::MAX,
            avoid: u32::MAX,
            visited_nodes: 0,
        }
    }

    pub fn prepare(&mut self, source: u32, avoid: u32) {
        self.source = source;
        self.avoid = avoid;

        self.heap.clear();
        self.valid.clear();
        self.visited_nodes = 0;

        self.update_node(source, 0);
        self.heap.push(WitnessNode::new(source, 0));
    }

    pub fn update_node(&mut self, node: u32, distance: u32) {
        self.valid.set(node as usize);
        self.data[node as usize].weight = distance;
        self.data[node as usize].visited = false;
    }

    pub fn is_visited(&self, node: u32) -> bool {
        self.valid.get(node as usize) && self.data[node as usize].visited
    }

    pub fn get_weight(&self, node: u32) -> u32 {
        if self.valid.get(node as usize) {
            self.data[node as usize].weight
        } else {
            u32::MAX
        }
    }

    pub fn find_max_weight(
        &mut self,
        graph: &PreGraph,
        target: u32,
        weight_limit: u32,
        visited_nodes_limit: u32,
    ) -> u32 {
        if target == self.source {
            return 0;
        }

        // check if we already computed this
        if self.valid.get(target as usize)
            && (self.data[target  as usize].visited || self.data[target as usize].weight <= weight_limit)
        {
            return self.data[target as usize].weight;
        }

        while let Some(&current) = self.heap.peek() {
            if self.visited_nodes >= visited_nodes_limit {
                break;
            }

            if current.weight > weight_limit {
                break;
            }

            self.heap.pop();

            if self.is_visited(current.id) {
                continue;
            }

            let mut found_target = false;
            for i in 0..graph.out_edges[current.id as usize].len() {
                let neighbour = graph.out_edges[current.id as usize][i].target;
                if neighbour == self.avoid {
                    continue;
                }
                let edge_weight = graph.out_edges[current.id as usize][i].weight;
                let weight = current.weight + edge_weight;
                if weight < self.get_weight(neighbour) {
                    self.update_node(neighbour, weight);
                    self.heap.push(WitnessNode::new(neighbour, weight));
                    if neighbour == target && weight <= weight_limit {
                        found_target = true;
                    }
                }
            }
            self.data[current.id as usize].visited = true;
            self.visited_nodes += 1;
            if found_target || current.id == target {
                break;
            }
        }

        self.get_weight(target)
    }
}
