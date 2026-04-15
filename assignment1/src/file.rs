use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter, Write};
use crate::graph::{build, Graph, Node};

pub fn parse(path: &str) -> Result<Graph, Box<dyn std::error::Error>> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let mut lines = reader.lines().map(|l| l.unwrap()).filter(|l| !l.is_empty() && !l.starts_with('#'));

    let next_usize = |lines: &mut dyn Iterator<Item=String>| -> usize {
        lines.next().unwrap().trim().parse().unwrap()
    };

    let num_nodes = next_usize(&mut lines);
    let num_edges = next_usize(&mut lines);

    let mut nodes: Vec<Node> = Vec::with_capacity(num_nodes);
    let mut edges: Vec<(u32, u32, u32)> = Vec::with_capacity(num_edges);

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
        edges.push((
            parts[0].parse()?,
            parts[1].parse()?,
            parts[2].parse()?,
            ));
    }

    Ok(build(nodes, edges))
}

pub fn read_queries(path: &str) -> Result<Vec<(u32, u32)>, Box<dyn std::error::Error>> {
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
        writeln!(writer, "{} {} {} {}ms", source, target, d, time)?;
    }

    Ok(())
}