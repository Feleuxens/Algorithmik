import java.io.*;
import java.util.*;

/**
 * Implementation of the CH query algorithm.
 * This is a minimal version just for compilation - not used in preprocessing.
 */
public class CHQuery {
    public static double computeDistance(Graph graph, int sourceId, int targetId) {
        double[] distances = new double[graph.nodeCount];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        distances[sourceId] = 0;

        // Use integer primitive array for predecessor tracking
        int[] pred = new int[graph.nodeCount];
        Arrays.fill(pred, -1);

        // Basic priority queue
        PriorityQueue<Integer> queue = new PriorityQueue<>(
                Comparator.comparingDouble(nodeId -> distances[nodeId]));
        queue.add(sourceId);

        boolean[] visited = new boolean[graph.nodeCount];

        while (!queue.isEmpty()) {
            int nodeId = queue.poll();

            // Skip if already visited
            if (visited[nodeId]) continue;
            visited[nodeId] = true;

            // Reached target
            if (nodeId == targetId) {
                return distances[targetId];
            }

            // Process outgoing edges using offset arrays
            for (int i = graph.outOffsets[nodeId]; i < graph.outOffsets[nodeId + 1]; i++) {
                if (i >= 0 && i < graph.outEdges.length) {  // Safety check
                    Edge edge = graph.outEdges[i];
                    if (edge != null) {
                        int targetNode = edge.getTarget();
                        if (targetNode >= 0 && targetNode < graph.nodeCount) {  // Safety check
                            double weight = edge.getWeight();

                            double newDist = distances[nodeId] + weight;
                            if (newDist < distances[targetNode]) {
                                distances[targetNode] = newDist;
                                pred[targetNode] = nodeId;
                                queue.add(targetNode);
                            }
                        }
                    }
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    public static double computeDistance2(Graph graph, int sourceId, int targetId) {
        double[] forwardDistance = new double[graph.nodeCount];
        double[] backwardDistance = new double[graph.nodeCount];
        Arrays.fill(forwardDistance, Double.POSITIVE_INFINITY);
        Arrays.fill(backwardDistance, Double.POSITIVE_INFINITY);
        forwardDistance[sourceId] = 0;
        backwardDistance[targetId] = 0;
        boolean[] forwardVisited = new boolean[graph.nodeCount];
        boolean[] backwardVisited = new boolean[graph.nodeCount];
        Arrays.fill(forwardVisited, false);
        Arrays.fill(backwardVisited, false);

        PriorityQueue<Node> forwardQueue = new PriorityQueue<>(graph.nodeCount, Comparator.comparingDouble(node -> forwardDistance[node.getId()]));
        PriorityQueue<Node> backwardQueue = new PriorityQueue<>(graph.nodeCount, Comparator.comparingDouble(node -> backwardDistance[node.getId()]));

        forwardQueue.add(graph.nodes[sourceId]);
        backwardQueue.add(graph.nodes[targetId]);

        double distance = Double.POSITIVE_INFINITY;
        boolean stall = false;

        while(!forwardQueue.isEmpty() || !backwardQueue.isEmpty()) {
            if (!forwardQueue.isEmpty()) {
                Node node = forwardQueue.poll();
                for (int i = graph.inOffsets[node.getId()]; i < graph.inOffsets[node.getId() + 1]; i++) {
                    Edge edge = graph.inEdges[i];
                    if (forwardDistance[edge.getSource()] + edge.getWeight() < forwardDistance[node.getId()]) {
                        stall = true;
                        break;
                    }
                }

                if (!stall) {
                    if (forwardDistance[node.getId()] <= distance) {
                        if (!forwardVisited[node.getId()]) {
                            forwardVisited[node.getId()] = true;
                            for (int i = graph.outOffsets[node.getId()]; i < graph.outOffsets[node.getId() + 1]; i++) {
                                Edge edge = graph.outEdges[i];
                                // only care about nodes with higher level
                                if (node.getLevel() < graph.nodes[edge.getTarget()].getLevel()) {
                                    double newDistance = forwardDistance[edge.getSource()] + edge.getWeight();
                                    if (newDistance < forwardDistance[edge.getTarget()]) {
                                        forwardDistance[edge.getTarget()] = newDistance;
                                        if (!forwardVisited[edge.getTarget()]) {
                                            forwardQueue.add(graph.nodes[edge.getTarget()]);
                                        }
                                    }
                                }
                            }
                        }
                        if (backwardVisited[node.getId()]) {
                            if (backwardDistance[node.getId()] + forwardDistance[node.getId()] <= distance) {
                                distance = backwardDistance[node.getId()] + forwardDistance[node.getId()];
                            }
                        }
                    }
                }
                stall = false;
            }

            if (!backwardQueue.isEmpty()) {
                Node node = backwardQueue.poll();

                for (int i = graph.outOffsets[node.getId()]; i < graph.outOffsets[node.getId() + 1]; i++) {
                    Edge edge = graph.outEdges[i];
                    if (backwardDistance[edge.getTarget()] + edge.getWeight() < backwardDistance[node.getId()]) {
                        stall = true;
                        break;
                    }
                }

                if (!stall) {
                    if (backwardDistance[node.getId()] <= distance) {
                        if (!backwardVisited[node.getId()]) {
                            backwardVisited[node.getId()] = true;
                            for (int i = graph.inOffsets[node.getId()]; i < graph.inOffsets[node.getId() + 1]; i++) {
                                Edge edge = graph.inEdges[i];
                                // only care about nodes with higher level
                                if (node.getLevel() < graph.nodes[edge.getSource()].getLevel()) {
                                    double newDistance = backwardDistance[edge.getTarget()] + edge.getWeight();
                                    if (newDistance < backwardDistance[edge.getSource()]) {
                                        backwardDistance[edge.getSource()] = newDistance;
                                        if (!backwardVisited[edge.getSource()]) {
                                            backwardQueue.add(graph.nodes[edge.getSource()]);
                                        }
                                    }
                                }
                            }
                        }
                        if (forwardVisited[node.getId()]) {
                            if (backwardDistance[node.getId()] + forwardDistance[node.getId()] <= distance) {
                                distance = backwardDistance[node.getId()] + forwardDistance[node.getId()];
                            }
                        }
                    }
                }
                stall = false;
            }
        }

        return distance;
    }
}