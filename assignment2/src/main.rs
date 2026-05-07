use std::io::Write;
use std::error::Error;
use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter};
use std::time::{Duration, Instant};
use crate::contractor::Contractor;
use crate::helper::PreGraph;
use crate::query::BiDijkstra;

mod graph;
mod query;
mod helper;
mod models;
mod contractor;
mod witness_search;


fn main() -> Result<(), Box<dyn Error>> {
    println!("Reading graph.fmi");
    let start = Instant::now();
    let (graph, num_nodes, num_edges) = PreGraph::from_file("graph.fmi")?;
    let file_read_time = start.elapsed();
    println!("File read in {}ms", file_read_time.as_millis());

    println!("Starting preprocessing");
    let start = Instant::now();
    let ch_graph = Contractor::new(graph, num_nodes as u32, num_edges as u32).graph;
    let preprocess_time = start.elapsed();
    println!("Preprocessing time: {}s", preprocess_time.as_secs());
    let total_edges = ch_graph.get_number_of_out_edges() + ch_graph.get_number_of_in_edges();
    let num_shortcuts = total_edges - num_edges as u32;
    println!("Number of shortcuts: {}", num_shortcuts);

    println!("Executing queries");
    let queries = read_queries("queries.txt")?;

    let mut dijkstra = BiDijkstra::new(ch_graph.get_number_of_nodes() as usize);
    let mut results: Vec<(u32, u32, Option<u32>, u128)> = Vec::with_capacity(queries.len());
    for (source, target) in queries {
        let start = Instant::now();
        let result = dijkstra.query(&ch_graph, source, target);
        results.push((source, target, result, start.elapsed().as_micros()));
    }

    write_result("results.txt", &results)?;
    println!("Writing ch graph to file");
    let start = Instant::now();
    ch_graph.write_file("graph.ch").expect("Failed writing ch graph to file!");
    let file_write_time = start.elapsed();
    println!("Wrote ch graph to file in {}ms", file_write_time.as_millis());
    println!("Writing log file...");
    write_log_file("log.txt", file_read_time, preprocess_time, file_write_time, total_edges, num_edges, num_shortcuts)?;

    Ok(())
}


pub fn read_queries(path: &str) -> Result<Vec<(u32, u32)>, Box<dyn Error>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let lines = reader.lines().map(|l| l.unwrap()).filter(|l| !l.is_empty() && !l.starts_with('#'));

    let mut queries: Vec<(u32, u32)> = Vec::new();

    for line in lines {
        let parts = line.split_whitespace().collect::<Vec<_>>();
        queries.push((parts[0].parse()?, parts[1].parse()?));
    }

    Ok(queries)
}

pub fn write_result(path: &str, results: &[(u32, u32, Option<u32>, u128)]) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(path)?;
    let mut writer = BufWriter::new(file);
    for (source, target, dist, time) in results {
        let d = match dist {
            Some(d) => d.to_string(),
            None => "None".to_string(),
        };
        writeln!(writer, "{} {} {} {}", source, target, d, time)?;
    }

    Ok(())
}

pub fn write_log_file(path: &str, time_io_graph: Duration, time_ch_processing: Duration, time_io_ch_file: Duration, edge_count: u32, orig_edge_count: usize, shortcut: u32) -> Result<(), Box<dyn std::error::Error>> {
    let file = File::create(path)?;
    let mut writer = BufWriter::new(file);
    writeln!(writer, "Reading graph file took {:?}s", time_io_graph)?;
    writeln!(writer, "Preprocessing of contraction hierarchies took {:?}s", time_ch_processing)?;
    writeln!(writer, "Writing ch graph took {:?}s\n", time_io_ch_file)?;
    writeln!(writer, "Overall edges: {}", edge_count)?;
    writeln!(writer, "Original edge count: {}", orig_edge_count)?;
    writeln!(writer, "Shortcut edge count: {}", shortcut)?;

    Ok(())
}
