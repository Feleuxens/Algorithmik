#[derive(Clone, Copy)]
#[allow(dead_code)]
pub struct Node {
    pub osm_id: u64,
    pub lat: f64,
    pub lon: f64,
    pub elevation: i32,
}

#[derive(Clone, Copy)]
pub struct Edge {
    #[allow(dead_code)]
    source: u32,
    pub target: u32,
    pub weight: u32,
}

pub struct Graph {
    nodes: Vec<Node>,

    fwd_offsets: Vec<u32>,
    fwd_edges: Vec<Edge>,

    bwd_offsets: Vec<u32>,
    bwd_edges: Vec<Edge>,
}

impl Graph {
    #[inline]
    pub fn out_edges(&self, node: u32) -> &[Edge] {
        let (s, e) = self.edge_range(& self.fwd_offsets, node);
        &self.fwd_edges[s..e]
    }

    #[inline]
    #[allow(dead_code)]
    pub fn in_edges(&self, node: u32) -> &[Edge] {
        let (s, e) = self.edge_range(& self.bwd_offsets, node);
        &self.bwd_edges[s..e]
    }

    fn edge_range(&self, offsets: &[u32], node: u32) -> (usize, usize) {
        let node = node as usize;
        (offsets[node] as usize, offsets[node + 1] as usize)
    }

    pub fn num_nodes(&self) -> usize {
        self.nodes.len()
    }

    #[allow(dead_code)]
    pub fn num_edges(&self) -> usize {
        self.fwd_edges.len()  // fwd_edges and bwd_edges must be of same length
    }


    pub fn get_weakly_connected_components(&self) -> u32 {
        let mut parent: Vec<u32> = (0..self.num_nodes() as u32).collect();
        let mut rank: Vec<u32> = vec![0; self.num_nodes()];

        for n in 0..self.num_nodes() as u32 {
            for edge in self.out_edges(n) {
                self.wcc_union(&mut rank, &mut parent, n, edge.target);
            }
        }
        (0..self.num_nodes() as u32).filter(|&i| self.find_root(&mut parent, i) == i).count() as u32
    }

    fn find_root(&self, parent: &mut Vec<u32>, node: u32) -> u32 {
        if parent[node as usize] != node {
            parent[node as usize] = self.find_root(parent, parent[node as usize]);
        }
        parent[node as usize]
    }

    fn wcc_union(&self, rank: &mut Vec<u32>, parent: &mut Vec<u32>, a: u32, b: u32) {
        let root_a = self.find_root(parent, a);
        let root_b = self.find_root(parent, b);

        if root_a == root_b { return; }

        match rank[root_a as usize].cmp(&rank[root_b as usize]) {
            std::cmp::Ordering::Less => parent[root_a as usize] = root_b,
            std::cmp::Ordering::Greater => parent[root_b as usize] = root_a,
            std::cmp::Ordering::Equal=> {
                parent[root_b as usize] = root_a;
                rank[root_a as usize] += 1;
            }
        }
    }
}


pub fn build(nodes: Vec<Node>, raw_edges: Vec<(u32, u32, u32)>) -> Graph {
    let n = nodes.len();

    let (fwd_offsets, fwd_edges) = build_csr(n, &raw_edges, false);
    let (bwd_offsets, bwd_edges) = build_csr(n, &raw_edges, true);

    Graph { nodes, fwd_offsets, fwd_edges, bwd_offsets, bwd_edges }
}

fn build_csr(n: usize, raw: &[(u32, u32, u32)], reverse: bool) -> (Vec<u32>, Vec<Edge>) {
    let mut degree = vec![0u32; n+1];

    for &(src, target, _) in raw {
        let from = if reverse { target } else { src };
        degree[from as usize + 1] += 1;
    }

    let mut offsets = degree;
    for i in 1..=n {
        offsets[i] += offsets[i-1];
    }

    let mut edges = vec![Edge { source: 0, target: 0, weight : 0}; raw.len()];
    let mut cursor = offsets[..n].to_vec();

    for &(src, target, weight) in raw {
        let (from, to) = if reverse { (target, src) } else { (src, target) };
        let pos = cursor[from as usize] as usize;
        edges[pos] = Edge { source: from, target: to, weight };
        cursor[from as usize] += 1;
    }

    (offsets, edges)
}
