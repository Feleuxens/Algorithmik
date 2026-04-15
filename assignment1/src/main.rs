use std::error::Error;
use std::time::Instant;
use crate::dijkstra::Dijkstra;

mod graph;
mod dijkstra;
mod file;

fn main() -> Result<(), Box<dyn Error>> {
    let graph = file::parse("graph.fmi")?;
    let start = Instant::now();
    let wcc = graph.get_weakly_connected_components();
    println!("Number of weakly connected components: {}, Time: {}ms", wcc, start.elapsed().as_millis());
    
    let mut dijkstra = Dijkstra::new(&graph);
    let queries = file::read_queries("queries.txt")?;
    let mut results: Vec<(u32, u32, Option<u32>, u128)> = Vec::with_capacity(queries.len());
    for (source, target) in queries {
        let start = Instant::now();
        let result = dijkstra.query(source, target);
        results.push((source, target, result, start.elapsed().as_millis() as u128));
    }

    file::write_result("results.txt", &results)?;

    Ok(())
}
