use std::cell::RefCell;
use std::cmp::Reverse;
use std::collections::BTreeSet;
use priority_queue::PriorityQueue;
use thread_local::ThreadLocal;
use rayon::prelude::*;

use crate::graph::{CHGraph, CHEdge};
use crate::helper::PreGraph;
use crate::witness_search::WitnessSearch;

const MAX_SETTLED_NODES_CONTRACTION: u32 = 100;

pub struct Contractor {
    pub graph: CHGraph,
    node_count: u32,
}

impl Contractor {
    pub fn new(pre_graph: PreGraph, node_count: u32, edge_count: u32) -> Self {
        let mut contractor = Contractor {
            graph: CHGraph::new(pre_graph.nodes.clone(), node_count, edge_count),
            node_count,
        };
        contractor.run_contraction(pre_graph);
        contractor
    }

    fn run_contraction(&mut self, mut pre_graph: PreGraph) {
        let mut witness_search = WitnessSearch::new(self.node_count as usize);
        let mut levels = vec![0; self.node_count as usize];
        let mut queue = PriorityQueue::with_capacity(self.node_count as usize);

        let tenth = self.node_count / 10;
        let mut percentage_step = 0;

        let start_time = std::time::Instant::now();

        let locals: ThreadLocal<RefCell<WitnessSearch>> = ThreadLocal::new();

        let relevance_results = (0..self.node_count)
            .into_par_iter()
            .map(|node| {
                let mut witness_search = locals
                    .get_or(|| RefCell::new(WitnessSearch::new(self.node_count as usize)))
                    .borrow_mut();

                let priority = calculate_score(&pre_graph, &mut witness_search, node, 0);

                (node, Reverse(priority))
            })
            .collect_vec_list();

        for l in relevance_results {
            for (node, priority) in l {
                queue.push(node, priority);
            }
        }

        drop(locals);

        let elapsed_time = start_time.elapsed(). as_secs();
        println!("Relevance calculation finished after {}s", elapsed_time);

        let start_time = std::time::Instant::now();

        let mut rank = 0;
        while !queue.is_empty() {
            if rank >= percentage_step * tenth && percentage_step < 10 {
                let elapsed_time = start_time.elapsed();
                println!(
                    "Processed ~{}% elapsed: {}s",
                    10 * percentage_step,
                    elapsed_time.as_secs()
                );
                percentage_step += 1;
            }

            // This normally yields the greatest priority, but since we use Reverse, it's the
            // least.
            let node = queue.pop().unwrap().0;
            let mut neighbors = BTreeSet::new();
            for out_edge in &pre_graph.out_edges[node as usize] {
                neighbors.insert(out_edge.target);
                self.graph.fwd_edges.push(CHEdge {
                    source: node,
                    target: out_edge.target,
                    weight: out_edge.weight,
                });
            }
            self.graph.fwd_offsets[(rank + 1) as usize] = self.graph.get_number_of_out_edges();

            for in_edge in &pre_graph.in_edges[node as usize] {
                neighbors.insert(in_edge.target);
                self.graph.bwd_edges.push(CHEdge {
                    source: node,
                    target: in_edge.target,
                    weight: in_edge.weight,
                });
            }
            self.graph.bwd_offsets[(rank + 1) as usize] = self.graph.get_number_of_in_edges();

            self.graph.ranks[node as usize] = rank;
            self.graph.levels[node as usize] = levels[node as usize];
            contract_node(&mut pre_graph, &mut witness_search, node);

            for neighbor in neighbors {
                let level = u32::max(levels[neighbor as usize], levels[node as usize] + 1);
                levels[neighbor as usize] = level;
                let priority =
                    calculate_score(&pre_graph, &mut witness_search, neighbor, level);
                queue.change_priority(&neighbor, Reverse(priority));
            }

            rank += 1;
        }

        let elapsed_time = start_time.elapsed();
        println!(
            "Processed 100% elapsed: {}s", elapsed_time.as_secs()
        );
        drop(pre_graph);
    }
}

pub fn contract_node(
    graph: &mut PreGraph,
    witness_search: &mut WitnessSearch,
    node: u32,
) {
    for i in 0..graph.in_edges[node as usize].len() {
        let in_node = graph.in_edges[node as usize][i].target;
        witness_search.prepare(in_node, node);
        for j in 0..graph.out_edges[node as usize].len() {
            let weight = graph.in_edges[node as usize][i].weight + graph.out_edges[node as usize][j].weight;
            let out_node = graph.out_edges[node as usize][j].target;

            // We don't need to find the actual weight of a witness path.
            // As long as we can be sure that there is some witness with lower weight than the path.
            let max_witness_weight = witness_search.find_max_weight(
                graph,
                out_node,
                weight,
                MAX_SETTLED_NODES_CONTRACTION,
            );
            if max_witness_weight <= weight {
                continue;
            }

            graph.add_edge(in_node, out_node, weight);
        }
    }

    graph.disconnect(node);
}

pub fn calculate_score(
    graph: &PreGraph,
    witness_search: &mut WitnessSearch,
    node: u32,
    level: u32,
) -> u32 {
    let mut num_shortcuts = 0;

    for in_edge in &graph.in_edges[node as usize] {
        witness_search.prepare(in_edge.target, node);
        for out_edge in &graph.out_edges[node as usize] {
            let weight = in_edge.weight + out_edge.weight;
            let max_weight = witness_search.find_max_weight(
                graph,
                out_edge.target,
                weight,
                MAX_SETTLED_NODES_CONTRACTION,
            );
            if max_weight <= weight {
                continue;
            }
            num_shortcuts += 1;
        }
    }

    let num_edges = graph.out_edges[node as usize].len() + graph.in_edges[node as usize].len();
    let score = (0.1 * level as f32) + (num_shortcuts as f32 + 1.0) / (num_edges as f32 + 1.0);
    (score * 1000.0) as u32
}
