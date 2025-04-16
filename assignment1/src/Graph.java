import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Graph {
    Node[] nodes;
    Edge[] edges;

    int[] outOffsets;
    int[] inOffsets;
    Edge[] outEdges;
    Edge[] inEdges;

    public Graph(String filename) throws IOException {
        parseFile(filename);
        buildOffsetArrays();
    }

    private void parseFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;

        int numNodes;
        int numEdges;

        while (true) {
            line = br.readLine();
            if (line.startsWith("#")) continue;
            if (line.trim().isEmpty()) continue;
            numNodes = Integer.parseInt(line.trim());
            break;
        }
        numEdges = Integer.parseInt(br.readLine().trim());

        nodes = new Node[numNodes];
        for (int i = 0; i < numNodes; i++) {
            line = br.readLine();
            String[] parts = line.split(" ");
            int id = Integer.parseInt(parts[0]);
            long osmId = Long.parseLong(parts[1]);
            double lon = Double.parseDouble(parts[2]);
            double lat = Double.parseDouble(parts[3]);
            int elevation = Integer.parseInt(parts[4]);
            nodes[id] = new Node(id, osmId, lon, lat, elevation);
        }

        edges = new Edge[numEdges];
        for (int i = 0; i < numEdges; i++) {
            line = br.readLine();
            String[] parts = line.split(" ");
            int from = Integer.parseInt(parts[0]);
            int to = Integer.parseInt(parts[1]);
            int weight = Integer.parseInt(parts[2]);
            int type = Integer.parseInt(parts[3]);
            int maxSpeed = Integer.parseInt(parts[4]);
            edges[i] = new Edge(from, to, weight, type, maxSpeed);
        }

        br.close();
    }

    private void buildOffsetArrays() {
        int n = nodes.length;

        int[] outgoingEdgesPerNode = new int[n];
        int[] incomingEdgesPerNode = new int[n];
        for (Edge edge : edges) {
            outgoingEdgesPerNode[edge.from]++;
            incomingEdgesPerNode[edge.to]++;
        }

        outOffsets = new int[n+1];
        inOffsets = new int[n+1];
        for (int i = 0; i < n; i++) {
            outOffsets[i+1] = outOffsets[i] + outgoingEdgesPerNode[i];
            inOffsets[i+1] = inOffsets[i] + incomingEdgesPerNode[i];
        }

        outEdges = new Edge[edges.length];
        inEdges = new Edge[edges.length];

        int[] outIndex = Arrays.copyOf(outOffsets, outOffsets.length);
        int[] inIndex = Arrays.copyOf(inOffsets, inOffsets.length);

        for (Edge edge : edges) {
            outEdges[outIndex[edge.from]++] = edge;
            inEdges[inIndex[edge.to]++] = edge;
        }
    }

    public int countWeaklyConnectedComponents() {
        int n = nodes.length;
        boolean[] visited = new boolean[n];
        int count = 0;

        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                count++;
                bfs(i, visited);
            }
        }

        return count;
    }

    private void bfs(int start, boolean[] visited) {
        Queue<Integer> queue = new ArrayDeque<>(1000);
        queue.add(start);
        visited[start] = true;

        while (!queue.isEmpty()) {
            int node = queue.poll();

            // Traverse outgoing edges
            for (int i = outOffsets[node]; i < outOffsets[node + 1]; i++) {
                int neighbor = outEdges[i].to;
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }

            // Traverse incoming edges (treat as undirected)
            for (int i = inOffsets[node]; i < inOffsets[node + 1]; i++) {
                int neighbor = inEdges[i].from;
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.add(neighbor);
                }
            }
        }
    }
}

class Edge {
    int from, to, weight, type, maxSpeed;

    Edge(int from, int to, int weight, int type, int maxSpeed) {
        this.from = from;
        this.to = to;
        this.weight = weight;
        this.type = type;
        this.maxSpeed = maxSpeed;
    }
}

class Node {
    int id;
    long osmId;
    double lon, lat;
    int elevation;

    Node(int id, long osmId, double lon, double lat, int elevation) {
        this.id = id;
        this.osmId = osmId;
        this.lon = lon;
        this.lat = lat;
        this.elevation = elevation;
    }
}
