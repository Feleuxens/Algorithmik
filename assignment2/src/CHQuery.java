import java.io.*;
import java.util.*;

/**
 * Implementation of the CH query algorithm.
 * This is a minimal version just for compilation - not used in preprocessing.
 */
public class CHQuery {
    private Graph graph;
    private int[] forwardDistances;
    private int[] backwardDistances;
    private boolean[] forwardVisited;
    private boolean[] backwardVisited;
    private PriorityQueue<QueryState> forwardQueue;
    private PriorityQueue<QueryState> backwardQueue;

    public CHQuery(Graph graph) {
        this.graph = graph;
        this.forwardDistances = new int[graph.nodeCount];
        this.backwardDistances = new int[graph.nodeCount];
        this.forwardVisited = new boolean[graph.nodeCount];
        this.backwardVisited = new boolean[graph.nodeCount];
    }

    /**
     * Query the shortest distance between source and target nodes
     * @param source Source node ID
     * @param target Target node ID
     * @return Shortest distance, or -1 if no path exists
     */
    public int queryDistance(int source, int target) {
        if (source == target) {
            return 0;
        }

        // Initialize data structures
        initializeQuery();

        // Start bidirectional search
        forwardQueue.offer(new QueryState(source, 0));
        backwardQueue.offer(new QueryState(target, 0));
        forwardDistances[source] = 0;
        backwardDistances[target] = 0;

        int bestDistance = Integer.MAX_VALUE;
        int forwardMaxDistance = 0;
        int backwardMaxDistance = 0;

        // Bidirectional Dijkstra with hierarchy constraints
        while (!forwardQueue.isEmpty() || !backwardQueue.isEmpty()) {

            // Forward search step
            if (!forwardQueue.isEmpty() &&
                    (backwardQueue.isEmpty() || forwardQueue.peek().distance <= backwardQueue.peek().distance)) {

                QueryState current = forwardQueue.poll();

                // Pruning: if current distance is greater than best found, skip
                if (current.distance > bestDistance) {
                    continue;
                }

                forwardMaxDistance = Math.max(forwardMaxDistance, current.distance);

                // Early termination condition
                if (forwardMaxDistance + backwardMaxDistance >= bestDistance) {
                    break;
                }

                if (forwardVisited[current.nodeId]) {
                    continue;
                }
                forwardVisited[current.nodeId] = true;

                // Check if we've met the backward search
                if (backwardVisited[current.nodeId]) {
                    bestDistance = Math.min(bestDistance,
                            current.distance + backwardDistances[current.nodeId]);
                }

                // Relax outgoing edges (only to higher level nodes)
                relaxForwardEdges(current);
            }

            // Backward search step
            else if (!backwardQueue.isEmpty()) {
                QueryState current = backwardQueue.poll();

                // Pruning: if current distance is greater than best found, skip
                if (current.distance > bestDistance) {
                    continue;
                }

                backwardMaxDistance = Math.max(backwardMaxDistance, current.distance);

                // Early termination condition
                if (forwardMaxDistance + backwardMaxDistance >= bestDistance) {
                    break;
                }

                if (backwardVisited[current.nodeId]) {
                    continue;
                }
                backwardVisited[current.nodeId] = true;

                // Check if we've met the forward search
                if (forwardVisited[current.nodeId]) {
                    bestDistance = Math.min(bestDistance,
                            current.distance + forwardDistances[current.nodeId]);
                }

                // Relax incoming edges (only to higher level nodes)
                relaxBackwardEdges(current);
            }
        }

        return bestDistance == Integer.MAX_VALUE ? -1 : bestDistance;
    }

    /**
     * Initialize query data structures
     */
    private void initializeQuery() {
        // Reset arrays
        Arrays.fill(forwardDistances, Integer.MAX_VALUE);
        Arrays.fill(backwardDistances, Integer.MAX_VALUE);
        Arrays.fill(forwardVisited, false);
        Arrays.fill(backwardVisited, false);

        // Initialize priority queues
        forwardQueue = new PriorityQueue<>();
        backwardQueue = new PriorityQueue<>();
    }

    /**
     * Relax outgoing edges for forward search
     */
    private void relaxForwardEdges(QueryState current) {
        int currentLevel = graph.nodes[current.nodeId].level;

        for (int i = graph.outOffsets[current.nodeId];
             i < graph.outOffsets[current.nodeId + 1]; i++) {
            Edge edge = graph.outEdges[i];
            int targetLevel = graph.nodes[edge.target].level;

            // Only follow edges to higher level nodes (upward search)
            if (targetLevel <= currentLevel) {
                continue;
            }

            if (forwardVisited[edge.target]) {
                continue;
            }

            int newDistance = current.distance + edge.weight;

            if (newDistance < forwardDistances[edge.target]) {
                forwardDistances[edge.target] = newDistance;
                forwardQueue.offer(new QueryState(edge.target, newDistance));
            }
        }
    }

    /**
     * Relax incoming edges for backward search
     */
    private void relaxBackwardEdges(QueryState current) {
        int currentLevel = graph.nodes[current.nodeId].level;

        for (int i = graph.inOffsets[current.nodeId];
             i < graph.inOffsets[current.nodeId + 1]; i++) {
            Edge edge = graph.inEdges[i];
            int sourceLevel = graph.nodes[edge.source].level;

            // Only follow edges to higher level nodes (upward search)
            if (sourceLevel <= currentLevel) {
                continue;
            }

            if (backwardVisited[edge.source]) {
                continue;
            }

            int newDistance = current.distance + edge.weight;

            if (newDistance < backwardDistances[edge.source]) {
                backwardDistances[edge.source] = newDistance;
                backwardQueue.offer(new QueryState(edge.source, newDistance));
            }
        }
    }

    /**
     * Query distance with additional optimizations for repeated queries
     * This version reuses allocated arrays for better performance
     */
    public int queryDistanceOptimized(int source, int target) {
        if (source == target) {
            return 0;
        }

        // Use a smaller working set for better cache performance
        Set<Integer> forwardReached = new HashSet<>();
        Set<Integer> backwardReached = new HashSet<>();
        Map<Integer, Integer> forwardDist = new HashMap<>();
        Map<Integer, Integer> backwardDist = new HashMap<>();

        PriorityQueue<QueryState> fwdQueue = new PriorityQueue<>();
        PriorityQueue<QueryState> bwdQueue = new PriorityQueue<>();

        fwdQueue.offer(new QueryState(source, 0));
        bwdQueue.offer(new QueryState(target, 0));
        forwardDist.put(source, 0);
        backwardDist.put(target, 0);

        int bestDistance = Integer.MAX_VALUE;

        while (!fwdQueue.isEmpty() || !bwdQueue.isEmpty()) {

            // Forward search
            if (!fwdQueue.isEmpty() &&
                    (bwdQueue.isEmpty() || fwdQueue.peek().distance <= bwdQueue.peek().distance)) {

                QueryState current = fwdQueue.poll();

                if (current.distance > bestDistance) {
                    continue;
                }

                if (forwardReached.contains(current.nodeId)) {
                    continue;
                }
                forwardReached.add(current.nodeId);

                // Check for meeting point
                if (backwardReached.contains(current.nodeId)) {
                    int totalDist = current.distance + backwardDist.get(current.nodeId);
                    bestDistance = Math.min(bestDistance, totalDist);
                }

                // Expand forward
                expandForward(current, forwardDist, fwdQueue, forwardReached);
            }

            // Backward search
            else if (!bwdQueue.isEmpty()) {
                QueryState current = bwdQueue.poll();

                if (current.distance > bestDistance) {
                    continue;
                }

                if (backwardReached.contains(current.nodeId)) {
                    continue;
                }
                backwardReached.add(current.nodeId);

                // Check for meeting point
                if (forwardReached.contains(current.nodeId)) {
                    int totalDist = current.distance + forwardDist.get(current.nodeId);
                    bestDistance = Math.min(bestDistance, totalDist);
                }

                // Expand backward
                expandBackward(current, backwardDist, bwdQueue, backwardReached);
            }
        }

        return bestDistance == Integer.MAX_VALUE ? -1 : bestDistance;
    }

    private void expandForward(QueryState current, Map<Integer, Integer> distances,
                               PriorityQueue<QueryState> queue, Set<Integer> reached) {
        int currentLevel = graph.nodes[current.nodeId].level;

        for (int i = graph.outOffsets[current.nodeId];
             i < graph.outOffsets[current.nodeId + 1]; i++) {
            Edge edge = graph.outEdges[i];

            if (graph.nodes[edge.target].level <= currentLevel || reached.contains(edge.target)) {
                continue;
            }

            int newDistance = current.distance + edge.weight;

            if (newDistance < distances.getOrDefault(edge.target, Integer.MAX_VALUE)) {
                distances.put(edge.target, newDistance);
                queue.offer(new QueryState(edge.target, newDistance));
            }
        }
    }

    private void expandBackward(QueryState current, Map<Integer, Integer> distances,
                                PriorityQueue<QueryState> queue, Set<Integer> reached) {
        int currentLevel = graph.nodes[current.nodeId].level;

        for (int i = graph.inOffsets[current.nodeId];
             i < graph.inOffsets[current.nodeId + 1]; i++) {
            Edge edge = graph.inEdges[i];

            if (graph.nodes[edge.source].level <= currentLevel || reached.contains(edge.source)) {
                continue;
            }

            int newDistance = current.distance + edge.weight;

            if (newDistance < distances.getOrDefault(edge.source, Integer.MAX_VALUE)) {
                distances.put(edge.source, newDistance);
                queue.offer(new QueryState(edge.source, newDistance));
            }
        }
    }

    /**
     * Helper class for query states in priority queue
     */
    private static class QueryState implements Comparable<QueryState> {
        int nodeId;
        int distance;

        QueryState(int nodeId, int distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }

        @Override
        public int compareTo(QueryState other) {
            return Integer.compare(this.distance, other.distance);
        }
    }
}