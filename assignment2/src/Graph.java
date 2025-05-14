import java.io.*;
import java.util.*;

/**
 * Graph class to represent the road network for Contraction Hierarchies.
 * Provides methods for graph operations, loading/saving, and optimizations.
 */
public class Graph {
    public Node[] nodes;
    public Edge[] edges;
    public int nodeCount; // Actual number of nodes
    public int edgeCount; // Actual number of edges
    public int[] outOffsets;
    public int[] inOffsets;
    Edge[] outEdges;
    Edge[] inEdges;

    public boolean isCHGraph = false;

    /**
     * Constructor for an empty graph.
     */
    public Graph() {
        nodes = new Node[1];
        edges = new Edge[1];
        nodeCount = 0;
        edgeCount = 0;
    }

    /**
     * Load graph from file.
     *
     * @param filename the file to load
     * @return the loaded graph
     * @throws IOException if an I/O error occurs
     */
    public static Graph fromFile(String filename) throws IOException {
        System.out.println("Reading graph from " + filename);
        Graph graph = new Graph();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean inNodeSection = true; // Start assuming we're reading nodes
            int nodeCount = 0;
            int edgeCount = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) { continue; }
                if (line.startsWith("#")) { continue; }
                if (nodeCount == 0) {
                    nodeCount = Integer.parseInt(line);
                    continue;
                }
                edgeCount = Integer.parseInt(line);
                break;
            }

            graph.nodeCount = nodeCount;
            graph.edgeCount = edgeCount;
            graph.nodes = new Node[nodeCount];
            graph.edges = new Edge[edgeCount];

            int numberOfNodes = 0;
            int numberOfEdges = 0;
            while ((line = reader.readLine()) != null) {
                if (numberOfNodes == nodeCount) {
                    inNodeSection = false;
                }

                line = line.trim();

                // Try to determine if this is a node or edge line
                String[] parts = line.split(" ");

                if (line.isEmpty()) { continue;}

                if (inNodeSection) {
                    // Try to parse as node: <ID> <OSMID> <Lat> <Lon> <Height> <Level>
                    int id = Integer.parseInt(parts[0]);
                    long osmId = Long.parseLong(parts[1]);
                    double lat = Double.parseDouble(parts[2]);
                    double lon = Double.parseDouble(parts[3]);
                    int height = Integer.parseInt(parts[4]);
                    int level;

                    if (parts.length == 5) {
                        level = -1;
                    } else {
                        level = Integer.parseInt(parts[5]);
                    }

                    Node node = new Node(id, osmId, lat, lon, height, level);
                    graph.nodes[numberOfNodes] = node; // first use index, then increase because index starts at 0
                    numberOfNodes++;
                }
                if (!inNodeSection) {
                    // Try to parse as edge: <SrcID> <TrgID> <Weight> <Type> <MaxSpeed> <EdgeIdA> <EdgeIdB>
                    int srcId = Integer.parseInt(parts[0]);
                    int trgId = Integer.parseInt(parts[1]);
                    int weight = Integer.parseInt(parts[2]);
                    int type = Integer.parseInt(parts[3]);
                    int maxSpeed = Integer.parseInt(parts[4]);
                    int edgeIdA;
                    int edgeIdB;

                    if (parts.length == 7) {
                        edgeIdA = Integer.parseInt(parts[5]);
                        edgeIdB = Integer.parseInt(parts[6]);
                    } else {
                        edgeIdA = -1;
                        edgeIdB = -1;
                    }

                    Edge edge = new Edge(srcId, trgId, weight, type, maxSpeed, edgeIdA, edgeIdB);
                    graph.edges[numberOfEdges] = edge; // first use index, then increase because index starts at 0
                    numberOfEdges++;
                }
            }
            if (graph.edges[0].getEdgeIdA() != -1) { graph.isCHGraph = true; }

            System.out.println("Graph loading complete:");
            System.out.println("  - Nodes: " + nodeCount);
            System.out.println("  - Edges: " + edgeCount);
        }

        graph.buildOffsetArrays();

        return graph;
    }

    public void buildOffsetArrays() {
        int n = nodes.length;

        int[] outgoingEdgesPerNode = new int[n];
        int[] incomingEdgesPerNode = new int[n];
        for (Edge edge : edges) {
            outgoingEdgesPerNode[edge.getSource()]++;
            incomingEdgesPerNode[edge.getTarget()]++;
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
            outEdges[outIndex[edge.getSource()]++] = edge;
            inEdges[inIndex[edge.getTarget()]++] = edge;
        }
    }

    /**
     * Write graph to file in CH format.
     *
     * @param filename the file to write
     * @throws IOException if an I/O error occurs
     */
    public void writeToFile(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(filename)) {
            // Write nodes first
            for (Node node : nodes) {
                if (node != null) {
                    writer.println(node.getId() + " " + node.getOsmId() + " " +
                            node.getLat() + " " + node.getLon() + " " +
                            node.getHeight() + " " + node.getLevel());
                }
            }

            writer.println(); // Separate nodes from edges

            // Write edges
            for (Edge edge : edges) {
                writer.println(edge.getSource() + " " + edge.getTarget() + " " +
                        edge.getWeight() + " " + edge.getType() + " " +
                        edge.getMaxSpeed() + " " + edge.getEdgeIdA() + " " +
                        edge.getEdgeIdB());
            }
        }
    }
}