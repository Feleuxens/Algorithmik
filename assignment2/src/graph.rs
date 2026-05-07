use std::io::Write;
use std::fs::File;
use std::io::BufWriter;
use crate::models::Node;

pub struct CHEdge {
    pub source: u32,
    pub target: u32,
    pub weight: u32,
}

pub struct CHGraph {
    node_count: u32,
    pub nodes: Vec<Node>,

    pub levels: Vec<u32>,
    pub ranks: Vec<u32>,

    pub fwd_offsets: Vec<u32>,
    pub fwd_edges: Vec<CHEdge>,

    pub bwd_offsets: Vec<u32>,
    pub bwd_edges: Vec<CHEdge>,
}

impl CHGraph {
    pub fn new(nodes: Vec<Node>, number_of_nodes: u32, number_of_edges: u32) -> Self {
        CHGraph {
            nodes,
            node_count: number_of_nodes,
            levels: vec![0; number_of_nodes as usize],
            ranks: vec![0; number_of_nodes as usize],
            fwd_offsets: vec![0; (number_of_nodes + 1) as usize],
            fwd_edges: Vec::with_capacity((number_of_edges * 2) as usize),
            bwd_offsets: vec![0; (number_of_nodes + 1) as usize],
            bwd_edges: Vec::with_capacity((number_of_edges * 2) as usize),
        }
    }

    pub fn get_number_of_nodes(&self) -> u32 {
        self.node_count
    }

    pub fn get_number_of_out_edges(&self) -> u32 {
        self.fwd_edges.len() as u32
    }

    pub fn get_number_of_in_edges(&self) -> u32 {
        self.bwd_edges.len() as u32
    }

    pub fn begin_in_edges(&self, node: u32) -> u32 {
        self.bwd_offsets[self.ranks[node as usize] as usize]
    }

    pub fn end_in_edges(&self, node: u32) -> u32 {
        self.bwd_offsets[self.ranks[node as usize] as usize + 1]
    }

    pub fn begin_out_edges(&self, node: u32) -> u32 {
        self.fwd_offsets[self.ranks[node as usize] as usize]
    }

    pub fn end_out_edges(&self, node: u32) -> u32 {
        self.fwd_offsets[self.ranks[node as usize] as usize + 1]
    }

    pub fn write_file(&self, path: &str) -> Result<(), Box<dyn std::error::Error>> {
        let file = File::create(path)?;
        let mut writer = BufWriter::new(file);
        writeln!(&mut writer, "{}", self.node_count)?;
        writeln!(&mut writer, "{}", self.fwd_edges.len() + self.bwd_edges.len())?;

        for i in 0..self.node_count {
            let node = self.nodes[i as usize];
            writeln!(&mut writer, "{} {} {} {} {} {}", i, node.osm_id, node.lat, node.lon, node.elevation, self.ranks[i as usize])?;
        }

        for edge in self.fwd_edges.iter() {
            writeln!(&mut writer, "{} {} {} 0 0 -1 -1", edge.source, edge.target, edge.weight)?;
        }

        for edge in self.bwd_edges.iter() {
            writeln!(&mut writer, "{} {} {} 0 0 -1 -1", edge.source, edge.target, edge.weight)?;
        }

        Ok(())
    }
}
