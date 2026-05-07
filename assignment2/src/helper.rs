use std::fs::File;
use std::io::{BufRead, BufReader};
use crate::models::{Edge, Node};

const INITIAL_EDGES_CAPACITY: usize = 4;

pub struct PreGraph {
    pub nodes: Vec<Node>,
    pub in_edges: Vec<Vec<Edge>>,
    pub out_edges: Vec<Vec<Edge>>,
}

impl PreGraph {
    pub fn from_file(path: &str) -> Result<(PreGraph, usize, usize), Box<dyn std::error::Error>> {
        let file = File::open(path)?;
        let reader = BufReader::new(file);
        let mut lines = reader.lines().map(|l| l.unwrap()).filter(|l| !l.is_empty() && !l.starts_with('#'));

        let next_usize = |lines: &mut dyn Iterator<Item=String>| -> usize {
            lines.next().unwrap().trim().parse().unwrap()
        };

        let num_nodes = next_usize(&mut lines);
        let num_edges = next_usize(&mut lines);

        let mut nodes: Vec<Node> = Vec::with_capacity(num_nodes);
        let mut in_edges: Vec<Vec<Edge>> = vec![Vec::with_capacity(INITIAL_EDGES_CAPACITY); num_nodes];
        let mut out_edges: Vec<Vec<Edge>> = vec![Vec::with_capacity(INITIAL_EDGES_CAPACITY); num_nodes];

        for _ in 0..num_nodes {
            let line = lines.next().unwrap();
            let parts = line.split_whitespace().collect::<Vec<_>>();
            nodes.push(Node {
                osm_id: parts[1].parse()?,
                lon: parts[2].parse()?,
                lat: parts[3].parse()?,
                elevation: parts[4].parse()?,
            });
        }
        for _ in 0..num_edges {
            let line = lines.next().unwrap();
            let parts = line.split_whitespace().collect::<Vec<_>>();
            let source = parts[0].parse()?;
            let target = parts[1].parse()?;
            let weight = parts[2].parse()?;
            out_edges[source as usize].push(Edge {
                target,
                weight,
            });
            in_edges[target as usize].push(Edge {
                target: source,
                weight,
            });
        }

        Ok((PreGraph {
            nodes,
            in_edges,
            out_edges,
        }, num_nodes, num_edges))
    }

    pub fn add_edge(&mut self, source: u32, target: u32, weight: u32) {
        // out_edges[source]: keep at most one (source -> target), with the smallest weight
        /*let outs = &mut self.out_edges[source as usize];
        if let Some(e) = outs.iter_mut().find(|e| e.target == target) {
            if weight < e.weight { e.weight = weight; }
            // also fix the corresponding in_edges entry
            let ins = &mut self.in_edges[target as usize];
            if let Some(e) = ins.iter_mut().find(|e| e.target == source) {
                if weight < e.weight { e.weight = weight; }
            }
            return;
        }
        outs.push(Edge { target, weight });*/
        self.out_edges[source as usize].push(Edge { target, weight });
        self.in_edges[target as usize].push(Edge { target: source, weight });
    }

    pub fn disconnect(&mut self, node: u32) {
        for i in 0..self.out_edges[node as usize].len() {
            let t = self.out_edges[node as usize][i].target;
            self.remove_in_edge(t, node);
        }
        for i in 0..self.in_edges[node as usize].len() {
            let t = self.in_edges[node as usize][i].target;
            self.remove_out_edge(t, node);
        }
        self.in_edges[node as usize].clear();
        self.out_edges[node as usize].clear();
    }

    #[inline]
    pub fn remove_out_edge(&mut self, node: u32, neighbour: u32) {
        Self::remove_edge_with_neighbor_node(&mut self.out_edges[node as usize], neighbour);
    }

    #[inline]
    pub fn remove_in_edge(&mut self, node: u32, neighbour: u32) {
        Self::remove_edge_with_neighbor_node(&mut self.in_edges[node as usize], neighbour);
    }

    #[inline]
    pub fn remove_edge_with_neighbor_node(edges: &mut Vec<Edge>, neighbour: u32) {
        edges.retain(|e| e.target != neighbour);
    }
}