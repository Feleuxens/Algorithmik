import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

public class Dijkstra {
    private final Graph graph;
    private final int numNodes;
    private final double[] dist;
    private final boolean[] visited;
    private final PriorityQueue<NodeDistance> pq;

    public Dijkstra(Graph graph) {
        this.graph = graph;
        this.numNodes = graph.nodes.length;
        this.dist = new double[numNodes];
        this.visited = new boolean[numNodes];
        this.pq = new PriorityQueue<>(Comparator.comparingDouble(node -> node.dist));
    }

    public double shortestPath(int source, int target) {
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(visited, false);
        pq.clear();

        dist[source] = 0;
        pq.add(new NodeDistance(source, 0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            int u = current.node;

            if (visited[u]) continue;
            visited[u] = true;

            if (u == target) return dist[u];

            for (int i = graph.outOffsets[u]; i < graph.outOffsets[u + 1]; i++) {
                Edge edge = graph.outEdges[i];
                int v = edge.to;
                double alt = dist[u] + edge.weight;

                if (!visited[v] && alt < dist[v]) {
                    dist[v] = alt;
                    pq.add(new NodeDistance(v, alt));
                }
            }
        }

        return Double.POSITIVE_INFINITY; // unreachable
    }

    private static class NodeDistance {
        int node;
        double dist;

        NodeDistance(int node, double dist) {
            this.node = node;
            this.dist = dist;
        }
    }
}
