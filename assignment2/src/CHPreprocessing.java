import java.util.*;

public class CHPreprocessing {
    private Graph graph;
    private PriorityQueue<NodeImportance> nodeQueue;
    private boolean[] contracted;
    private int contractionLevel;

    public CHPreprocessing(Graph graph) {
        this.graph = graph;
        this.contracted = new boolean[graph.nodeCount];
        this.contractionLevel = 0;
    }

    /**
     * Main contraction method that builds the hierarchy
     */
    public void buildHierarchy() {
        // Initialize node importance queue
        initializeNodeImportance();
        System.out.println("Finished initializing importance");

        // Contract nodes in order of importance
        while (!nodeQueue.isEmpty()) {
            System.out.println(nodeQueue.size());
            NodeImportance nodeImportance = nodeQueue.poll();
            int nodeId = nodeImportance.nodeId;

            // Recalculate importance to ensure it's still the least important
            double currentImportance = calculateNodeImportance(nodeId);
            if (currentImportance > nodeImportance.importance + 1e-6) {
                // Node importance has increased, reinsert with new importance
                nodeQueue.offer(new NodeImportance(nodeId, currentImportance));
                continue;
            }

            // Contract the node
            contractNode(nodeId);
        }
    }

    /**
     * Initialize the priority queue with all nodes and their importance
     */
    private void initializeNodeImportance() {
        nodeQueue = new PriorityQueue<>();
        for (int i = 0; i < graph.nodeCount; i++) {
            if (!contracted[i]) {
                double importance = calculateNodeImportance(i);
                nodeQueue.offer(new NodeImportance(i, importance));
            }
            if (i % 10000 == 0) {
                System.out.println("Worked through " + i + " nodes");
            }
        }
    }

    /**
     * Calculate the importance of a node for contraction ordering
     */
    private double calculateNodeImportance(int nodeId) {
        if (contracted[nodeId]) {
            return Double.MAX_VALUE;
        }

        // Count edges without expensive witness search
        int inDegree = graph.inOffsets[nodeId + 1] - graph.inOffsets[nodeId];
        int outDegree = graph.outOffsets[nodeId + 1] - graph.outOffsets[nodeId];

        // Remove contracted edges from count
        int activeInDegree = 0;
        int activeOutDegree = 0;

        for (int i = graph.inOffsets[nodeId]; i < graph.inOffsets[nodeId + 1]; i++) {
            if (!contracted[graph.inEdges[i].source]) {
                activeInDegree++;
            }
        }

        for (int i = graph.outOffsets[nodeId]; i < graph.outOffsets[nodeId + 1]; i++) {
            if (!contracted[graph.outEdges[i].target]) {
                activeOutDegree++;
            }
        }

        // Simple heuristic: assume we need shortcuts for most edge pairs
        // This overestimates but is much faster than witness search
        int potentialShortcuts = activeInDegree * activeOutDegree;
        int edgesRemoved = activeInDegree + activeOutDegree;
        int edgeDifference = potentialShortcuts - edgesRemoved;

        // Add penalty for high-degree nodes and bonus for contracted neighbors
        int contractedNeighbors = (inDegree - activeInDegree) + (outDegree - activeOutDegree);

        return edgeDifference - contractedNeighbors * 0.5;
    }

    /**
     * Contract a specific node by adding necessary shortcuts
     */
    private void contractNode(int nodeId) {
        List<Edge> incomingEdges = getIncomingEdges(nodeId);
        List<Edge> outgoingEdges = getOutgoingEdges(nodeId);
        List<Edge> shortcuts = new ArrayList<>();

        // Early exit for low-degree nodes
        if (incomingEdges.isEmpty() || outgoingEdges.isEmpty()) {
            contracted[nodeId] = true;
            graph.nodes[nodeId].level = contractionLevel++;
            return;
        }

        // Find all necessary shortcuts with optimizations
        for (Edge inEdge : incomingEdges) {
            for (Edge outEdge : outgoingEdges) {
                if (inEdge.source == outEdge.target) continue;

                int shortcutWeight = inEdge.weight + outEdge.weight;

                // Skip witness search for very short shortcuts - they're almost always needed
                boolean needsShortcut = shortcutWeight <= 1000 ||
                        !hasWitnessPath(inEdge.source, outEdge.target, shortcutWeight, nodeId);

                if (needsShortcut) {
                    // Create shortcut edge
                    Edge shortcut = new Edge(inEdge.source, outEdge.target, shortcutWeight,
                            Math.max(inEdge.type, outEdge.type),
                            Math.min(inEdge.maxSpeed, outEdge.maxSpeed), -1 , -1);
                    shortcuts.add(shortcut);
                }
            }
        }

        // Add shortcuts to graph (only if we have any)
        if (!shortcuts.isEmpty()) {
            addShortcutsToGraph(shortcuts);
        }

        // Mark node as contracted and set its level
        contracted[nodeId] = true;
        graph.nodes[nodeId].level = contractionLevel++;
    }

    /**
     * Check if there exists a witness path that is not longer than the potential shortcut
     */
    private boolean hasWitnessPath(int source, int target, int maxWeight, int avoidNode) {
        if (source == target) return true;

        // Use a simple BFS-like approach with strict limits
        Queue<Integer> queue = new ArrayDeque<>();
        int[] distances = new int[graph.nodeCount];
        Arrays.fill(distances, Integer.MAX_VALUE);

        queue.offer(source);
        distances[source] = 0;

        int nodesExplored = 0;
        int maxNodes = 20; // Very aggressive limit
        int hopLimit = 3;   // Maximum 3 hops

        while (!queue.isEmpty() && nodesExplored < maxNodes) {
            int current = queue.poll();
            int currentDist = distances[current];

            if (currentDist >= maxWeight) continue;
            if (currentDist > maxWeight - 100) continue; // Early pruning

            // Hop limit check (approximate)
            if (currentDist > hopLimit * (maxWeight / 4)) continue;

            nodesExplored++;

            // Check neighbors
            for (int i = graph.outOffsets[current]; i < graph.outOffsets[current + 1]; i++) {
                Edge edge = graph.outEdges[i];

                if (edge.target == avoidNode || contracted[edge.target]) continue;

                int newDist = currentDist + edge.weight;

                if (edge.target == target && newDist < maxWeight) {
                    return true; // Found witness path
                }

                if (newDist < distances[edge.target] && newDist < maxWeight) {
                    distances[edge.target] = newDist;
                    queue.offer(edge.target);
                }
            }
        }

        return false;
    }

    /**
     * Get incoming edges for a node
     */
    private List<Edge> getIncomingEdges(int nodeId) {
        List<Edge> edges = new ArrayList<>();
        for (int i = graph.inOffsets[nodeId]; i < graph.inOffsets[nodeId + 1]; i++) {
            edges.add(graph.inEdges[i]);
        }
        return edges;
    }

    /**
     * Get outgoing edges for a node
     */
    private List<Edge> getOutgoingEdges(int nodeId) {
        List<Edge> edges = new ArrayList<>();
        for (int i = graph.outOffsets[nodeId]; i < graph.outOffsets[nodeId + 1]; i++) {
            edges.add(graph.outEdges[i]);
        }
        return edges;
    }

    /**
     * Add shortcuts to the graph structure
     */
    private void addShortcutsToGraph(List<Edge> shortcuts) {
        if (shortcuts.isEmpty()) return;

        // This is a simplified version - in practice, you'd need to rebuild
        // the offset arrays and edge arrays to maintain the compact representation
        List<Edge> newOutEdges = new ArrayList<>();
        List<Edge> newInEdges = new ArrayList<>();

        // Copy existing edges
        for (Edge edge : graph.outEdges) {
            if (edge != null) newOutEdges.add(edge);
        }
        for (Edge edge : graph.inEdges) {
            if (edge != null) newInEdges.add(edge);
        }

        // Add shortcuts
        for (Edge shortcut : shortcuts) {
            newOutEdges.add(shortcut);
            newInEdges.add(new Edge(shortcut.target, shortcut.source, shortcut.weight,
                    shortcut.type, shortcut.maxSpeed, -1, -1));
        }

        // Update graph (this requires rebuilding the offset arrays)
        rebuildGraphStructure(newOutEdges, newInEdges);
    }

    /**
     * Rebuild the graph structure with new edges
     * This is a simplified version - in practice, you'd sort edges and rebuild offsets
     */
    private void rebuildGraphStructure(List<Edge> outEdges, List<Edge> inEdges) {
        // Sort edges by source node
        outEdges.sort((a, b) -> Integer.compare(a.source, b.source));
        inEdges.sort((a, b) -> Integer.compare(a.target, b.target));

        // Rebuild out edges and offsets
        graph.outEdges = outEdges.toArray(new Edge[0]);
        graph.edgeCount = outEdges.size();

        // Rebuild out offsets
        int currentNode = 0;
        graph.outOffsets[0] = 0;
        for (int i = 0; i < outEdges.size(); i++) {
            while (currentNode < outEdges.get(i).source) {
                currentNode++;
                graph.outOffsets[currentNode] = i;
            }
        }
        while (currentNode < graph.nodeCount) {
            graph.outOffsets[++currentNode] = outEdges.size();
        }

        // Rebuild in edges and offsets similarly
        graph.inEdges = inEdges.toArray(new Edge[0]);
        currentNode = 0;
        graph.inOffsets[0] = 0;
        for (int i = 0; i < inEdges.size(); i++) {
            while (currentNode < inEdges.get(i).target) {
                currentNode++;
                graph.inOffsets[currentNode] = i;
            }
        }
        while (currentNode < graph.nodeCount) {
            graph.inOffsets[++currentNode] = inEdges.size();
        }
    }

    /**
     * Helper class for node importance in priority queue
     */
    private static class NodeImportance implements Comparable<NodeImportance> {
        int nodeId;
        double importance;

        NodeImportance(int nodeId, double importance) {
            this.nodeId = nodeId;
            this.importance = importance;
        }

        @Override
        public int compareTo(NodeImportance other) {
            return Double.compare(this.importance, other.importance);
        }
    }

    /**
     * Helper class for Dijkstra's algorithm during witness search
     */
    private static class DijkstraState implements Comparable<DijkstraState> {
        int nodeId;
        int distance;

        DijkstraState(int nodeId, int distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        @Override
        public int compareTo(DijkstraState other) {
            return Integer.compare(this.distance, other.distance);
        }
    }
}