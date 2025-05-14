import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Implementation of the CH preprocessing algorithm.
 */
public class CHPreprocessing {
    int[] nodeImportance;

    public Graph preprocess(Graph graph) {
        nodeImportance = new int[graph.nodeCount];
        Arrays.fill(nodeImportance, 0);

        int[] contractionOrder = calculateContractionOrder(graph);
        List<Edge> shortcuts = contractNodes(graph, contractionOrder);
        rebuildGraph(graph, shortcuts);
        assignNodeLevels(graph, contractionOrder);
        return graph;
    }

    private List<Edge> contractNodes(Graph graph, int[] contractionOrder) {
        List<Edge> shortcuts = new ArrayList<>(Math.min(1000000, graph.nodeCount));
        Map<Integer, List<Edge>> temporaryEdges = new HashMap<>(graph.nodeCount);
        boolean[] contracted = new boolean[graph.nodeCount];

        // Process metrics for optimization tracking
        int totalWitnessSearches = 0;
        int stallOnDemandSkips = 0;
        int hopLimitSkips = 0;
        int totalShortcuts = 0;

        for (int i = 0; i < contractionOrder.length; i++) {
            int nodeToContract = contractionOrder[i];

            // Find shortcuts with optimized witness search
            List<Edge> nodeShortcuts = findShortcuts(graph, nodeToContract, contracted,
                    temporaryEdges, totalWitnessSearches,
                    stallOnDemandSkips, hopLimitSkips);

            // Add shortcuts to temporary structure for future witness searches
            for (Edge shortcut : nodeShortcuts) {
                temporaryEdges.computeIfAbsent(shortcut.getSource(), k -> new ArrayList<>())
                        .add(shortcut);
            }

            // Add to main shortcuts collection
            shortcuts.addAll(nodeShortcuts);
            totalShortcuts += nodeShortcuts.size();

            // Mark node as contracted
            contracted[nodeToContract] = true;

            // Periodically log progress with detailed metrics
            if (i % 50000 == 0 || i == contractionOrder.length - 1) {
                System.out.println("Contracted " + (i+1) + " out of " + contractionOrder.length +
                        " nodes. Shortcuts so far: " + totalShortcuts +
                        ", Witness searches: " + totalWitnessSearches +
                        ", Stall-on-demand skips: " + stallOnDemandSkips +
                        ", Hop limit skips: " + hopLimitSkips);
            }
        }
        return shortcuts;
    }

    private List<Edge> findShortcuts(Graph graph, int nodeToContract, boolean[] contracted,
                                     Map<Integer, List<Edge>> temporaryEdges,
                                     int totalWitnessSearches, int stallOnDemandSkips, int hopLimitSkips) {
        List<Edge> shortcuts = new ArrayList<>();

        // Get incoming and outgoing edges to uncontracted nodes
        List<Edge> inEdges = new ArrayList<>();
        List<Edge> outEdges = new ArrayList<>();

        // Collect uncontracted incoming edges
        for (int i = graph.inOffsets[nodeToContract]; i < graph.inOffsets[nodeToContract + 1]; i++) {
            Edge edge = graph.inEdges[i];
            if (!contracted[edge.getSource()]) {
                inEdges.add(edge);
            }
        }

        // Collect uncontracted outgoing edges
        for (int i = graph.outOffsets[nodeToContract]; i < graph.outOffsets[nodeToContract + 1]; i++) {
            Edge edge = graph.outEdges[i];
            if (!contracted[edge.getTarget()]) {
                outEdges.add(edge);
            }
        }

        // Skip witness search for nodes with too many potential shortcuts
        int potentialShortcutCount = inEdges.size() * outEdges.size();
        if (potentialShortcutCount > 1000) {
            // For high-degree nodes, use direct shortcuts
            for (Edge inEdge: inEdges) {
                for (Edge outEdge: outEdges) {
                    if (inEdge.getSource() == outEdge.getTarget()) { continue; }

                    int distanceThroughNode = inEdge.getWeight() + outEdge.getWeight();
                    shortcuts.add(new Edge(inEdge.getSource(), outEdge.getTarget(), distanceThroughNode));
                }
            }
            return shortcuts;
        }

        // Group potential shortcuts by source for batch processing
        Map<Integer, Map<Integer, Integer>> sourceToTargets = new HashMap<>();

        for (Edge inEdge: inEdges) {
            int source = inEdge.getSource();
            int sourceToNodeDist = inEdge.getWeight();

            for (Edge outEdge: outEdges) {
                int target = outEdge.getTarget();

                // Skip self-loops
                if (source == target) { continue; }

                int nodeToTargetDist = outEdge.getWeight();
                int distanceThroughNode = sourceToNodeDist + nodeToTargetDist;

                // Group by source for batch processing
                sourceToTargets
                        .computeIfAbsent(source, k -> new HashMap<>())
                        .put(target, distanceThroughNode);
            }
        }

        // Process each source with optimized witness search
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : sourceToTargets.entrySet()) {
            int source = entry.getKey();
            Map<Integer, Integer> targets = entry.getValue();

            // Batch process witness searches from this source
            Map<Integer, Integer> shortestDistances =
                    batchWitnessSearch(graph, source, targets, contracted, nodeToContract, temporaryEdges,
                            totalWitnessSearches, stallOnDemandSkips, hopLimitSkips);

            // Create shortcuts where needed
            for (Map.Entry<Integer, Integer> targetEntry : targets.entrySet()) {
                int target = targetEntry.getKey();
                int throughNodeDist = targetEntry.getValue();

                int shortestDist = shortestDistances.getOrDefault(target, Integer.MAX_VALUE);
                if (shortestDist > throughNodeDist) {
                    shortcuts.add(new Edge(source, target, throughNodeDist));
                }
            }
        }

        return shortcuts;
    }


    private int findShortestPath(Graph graph, int source, int target, int limit, AtomicIntegerArray contracted, int excludedNode, Map<Integer, List<Edge>> temporaryEdges) {
        // Distance array and priority queue for Dijkstra
        int[] distances = new int[graph.nodeCount];
        Arrays.fill(distances, Integer.MAX_VALUE);
        distances[source] = 0;
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(node -> distances[node.getId()]));
        boolean[] visited = new boolean[graph.nodeCount];
        Arrays.fill(visited, false);
        boolean[] stalled = new boolean[graph.nodeCount];
        Arrays.fill(stalled, false);

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (distances[node.getId()] > limit) {
                break;
            }
            if (node.getId() == target) {
                return distances[node.getId()];
            }
            if (visited[node.getId()] || stalled[node.getId()]) {
                continue;
            }

            for (int i = graph.inOffsets[node.getId()]; i < graph.inOffsets[node.getId() + 1]; i++) {
                Edge edge = graph.inEdges[i];
                if (contracted.get(edge.getSource()) == 1 || edge.getSource() == excludedNode) {
                    continue;
                }
                if (distances[edge.getSource()] != Integer.MAX_VALUE && distances[edge.getSource()] + edge.getWeight() < distances[node.getId()]) {
                    stalled[node.getId()] = true;
                    break;
                }
            }

            visited[node.getId()] = true;
            for (int i = graph.outOffsets[node.getId()]; i < graph.outOffsets[node.getId() + 1]; i++) {
                Edge edge = graph.outEdges[i];

                // Skip contracted nodes and the excluded node
                if (contracted.get(edge.getTarget()) == 1 || edge.getTarget() == excludedNode) {
                    continue;
                }

                int newDist = distances[node.getId()] + edge.getWeight();
                if (newDist < distances[edge.getTarget()]) {
                    distances[edge.getTarget()] = newDist;
                    queue.add(graph.nodes[edge.getTarget()]);
                }
            }
            if (temporaryEdges.containsKey(node.getId())) {
                for (Edge edge : temporaryEdges.get(node.getId())) {
                    // Skip contracted nodes and the excluded node
                    if (contracted.get(edge.getTarget()) == 1 || edge.getTarget() == excludedNode) {
                        continue;
                    }

                    int newDist = distances[node.getId()] + edge.getWeight();
                    if (newDist < distances[edge.getTarget()]) {
                        distances[edge.getTarget()] = newDist;
                        queue.add(graph.nodes[edge.getTarget()]);
                    }
                }
            }
        }

        return distances[target];
    }

    private Map<Integer, Integer> batchWitnessSearch(
            Graph graph, int source, Map<Integer, Integer> targets,
            boolean[] contracted, int excludedNode, Map<Integer, List<Edge>> temporaryEdges,
            int totalWitnessSearches, int stallOnDemandSkips, int hopLimitSkips) {

        // Find minimum limit for early termination
        int globalLimit = Integer.MAX_VALUE;
        for (int limit : targets.values()) {
            globalLimit = Math.min(globalLimit, limit);
        }

        // Results map: target -> distance
        Map<Integer, Integer> results = new HashMap<>();
        Set<Integer> remainingTargets = new HashSet<>(targets.keySet());

        // If we still have targets, run full witness search with optimizations
        if (!remainingTargets.isEmpty()) {
            totalWitnessSearches++;

            // Optimized Dijkstra with stall-on-demand and hop limits
            int[] distances = new int[graph.nodeCount];
            Arrays.fill(distances, Integer.MAX_VALUE);
            distances[source] = 0;

            // Add hop counter to limit search depth
            int maxHops = 10; // Adjust based on graph characteristics
            int[] hops = new int[graph.nodeCount];
            Arrays.fill(hops, Integer.MAX_VALUE);
            hops[source] = 0;

            PriorityQueue<Node> queue = new PriorityQueue<>(
                    Comparator.comparingInt(node -> distances[node.getId()]));
            queue.add(graph.nodes[source]);

            boolean[] visited = new boolean[graph.nodeCount];
            boolean[] stalled = new boolean[graph.nodeCount];

            while (!queue.isEmpty() && !remainingTargets.isEmpty()) {
                Node node = queue.poll();
                int nodeId = node.getId();

                // Skip if we exceed the global limit or hit hop limit
                if (distances[nodeId] > globalLimit || hops[nodeId] >= maxHops) {
                    hopLimitSkips++;
                    continue;
                }

                // Skip if already visited or stalled
                if (visited[nodeId] || stalled[nodeId]) {
                    continue;
                }

                // Apply stall-on-demand optimization
                boolean shouldStall = false;

                // Check incoming edges for potential better paths
                for (int i = graph.inOffsets[nodeId]; i < graph.inOffsets[nodeId + 1]; i++) {
                    Edge edge = graph.inEdges[i];
                    int sourceId = edge.getSource();

                    // Skip contracted nodes and excluded node
                    if (contracted[sourceId] || sourceId == excludedNode) {
                        continue;
                    }

                    // If this node can be reached via a better path through one of its
                    // in-neighbors, we don't need to explore its outgoing edges
                    if (distances[sourceId] != Integer.MAX_VALUE &&
                            distances[sourceId] + edge.getWeight() < distances[nodeId]) {
                        shouldStall = true;
                        break;
                    }
                }

                if (shouldStall) {
                    stalled[nodeId] = true;
                    stallOnDemandSkips++;
                    continue;
                }

                // Mark node as visited
                visited[nodeId] = true;

                // Check if this is one of our targets
                if (remainingTargets.contains(nodeId)) {
                    int targetLimit = targets.get(nodeId);
                    if (distances[nodeId] <= targetLimit) {
                        results.put(nodeId, distances[nodeId]);
                    } else {
                        // If distance exceeds target's limit, use infinity
                        results.put(nodeId, Integer.MAX_VALUE);
                    }
                    remainingTargets.remove(nodeId);

                    // If all targets found, we're done
                    if (remainingTargets.isEmpty()) {
                        break;
                    }
                }

                // Process outgoing edges
                for (int i = graph.outOffsets[nodeId]; i < graph.outOffsets[nodeId + 1]; i++) {
                    Edge edge = graph.outEdges[i];
                    int targetId = edge.getTarget();

                    // Skip contracted nodes and excluded node
                    if (contracted[targetId] || targetId == excludedNode) {
                        continue;
                    }

                    int newDist = distances[nodeId] + edge.getWeight();
                    if (newDist < distances[targetId]) {
                        distances[targetId] = newDist;
                        hops[targetId] = hops[nodeId] + 1;
                        queue.add(graph.nodes[targetId]);
                    }
                }

                // Process temporary edges
                List<Edge> tempEdges = temporaryEdges.get(nodeId);
                if (tempEdges != null) {
                    for (Edge edge : tempEdges) {
                        int targetId = edge.getTarget();

                        // Skip contracted nodes and excluded node
                        if (contracted[targetId] || targetId == excludedNode) {
                            continue;
                        }

                        int newDist = distances[nodeId] + edge.getWeight();
                        if (newDist < distances[targetId]) {
                            distances[targetId] = newDist;
                            hops[targetId] = hops[nodeId] + 1;
                            queue.add(graph.nodes[targetId]);
                        }
                    }
                }
            }

            // Add any remaining targets with infinity distance
            for (int target : remainingTargets) {
                results.put(target, Integer.MAX_VALUE);
            }
        }

        return results;
    }

    // Assign levels to nodes based on contraction order
    private void assignNodeLevels(Graph graph, int[] contractionOrder) {
        int[] rankMap = new int[graph.nodeCount];
        for (int i = 0; i < contractionOrder.length; i++) {
            rankMap[contractionOrder[i]] = i;
        }

        for (int i = 0; i < graph.nodeCount; i++) {
            graph.nodes[i].setLevel(rankMap[i]);
        }
    }

    private int[] calculateContractionOrder(Graph graph) {
        int[] contractionOrder = new int[graph.nodeCount];
        boolean[] contracted = new boolean[graph.nodeCount];

        //PriorityQueue<Node> pq = new PriorityQueue<>(graph.nodeCount, Comparator.comparingInt(node -> nodeImportance[node.getId()]));
        PriorityQueue<NodeImportance> pq = new PriorityQueue<>();

        for (int i = 0; i < graph.nodeCount; i++) {
            int newImportance = calculateNodeImportance(graph, i , contracted);
            nodeImportance[i] = newImportance;
            pq.add(new NodeImportance(i, newImportance));
        }

        int orderIndex = -1;

        while (!pq.isEmpty()) {
            NodeImportance node = pq.poll();
            int importance = node.importance;

            if (contracted[node.nodeId] || importance != nodeImportance[node.nodeId]) {
                continue;
            }
            orderIndex++;
            contractionOrder[orderIndex] = node.nodeId;
            contracted[node.nodeId] = true;

            Set<Integer> neighbors = getNeighbors(graph, node.nodeId);
            for (int neighbor : neighbors) {
                if (!contracted[neighbor]) {
                    int newImportance = calculateNodeImportance(graph, neighbor, contracted);
                    nodeImportance[neighbor] = newImportance;
                    pq.add(new NodeImportance(neighbor, newImportance));
                }
            }
        }
        return contractionOrder;
    }

    private int calculateNodeImportance(Graph graph, int nodeId, boolean[] contracted) {
        int potentialShortcuts = 0;
        int inEdges = graph.inOffsets[nodeId + 1] - graph.inOffsets[nodeId] ;
        int outEdges = graph.outOffsets[nodeId + 1] - graph.outOffsets[nodeId] ;

        for (int i = graph.inOffsets[nodeId]; i < graph.inOffsets[nodeId + 1]; i++) {
            Edge inEdge = graph.inEdges[i];
            for (int j = graph.outOffsets[nodeId]; j < graph.outOffsets[nodeId + 1]; j++) {
                Edge outEdge = graph.outEdges[j];
                if (inEdge.getSource() != outEdge.getSource()) {
                    potentialShortcuts++;
                }
            }
        }
        return potentialShortcuts - (inEdges + outEdges);

    }

    private Set<Integer> getNeighbors(Graph graph, int nodeId) {
        Set<Integer> neighbors = new HashSet<>();

        for (int i = graph.inOffsets[nodeId]; i < graph.inOffsets[nodeId + 1]; i++) {
            Edge inEdge = graph.inEdges[i];
            neighbors.add(inEdge.getSource());
        }
        for (int i = graph.outOffsets[nodeId]; i < graph.outOffsets[nodeId + 1]; i++) {
            Edge outEdge = graph.outEdges[i];
            neighbors.add(outEdge.getTarget());
        }
        return neighbors;
    }


    private void rebuildGraph(Graph graph, List<Edge> shortcuts) {
        int newEdgeCount = graph.edgeCount + shortcuts.size();
        graph.edges = Arrays.copyOf(graph.edges, newEdgeCount);

        for (Edge edge : shortcuts) {
            graph.edges[graph.edgeCount] = edge;
            graph.edgeCount++;
        }

        graph.buildOffsetArrays();
    }

    private static class NodeImportance implements Comparable<NodeImportance> {
        final int nodeId;
        final int importance;

        NodeImportance(int nodeId, int importance) {
            this.nodeId = nodeId;
            this.importance = importance;
        }

        @Override
        public int compareTo(NodeImportance other) {
            return Integer.compare(importance, other.importance);
        }
    }
}