import java.util.*;

class CHPreprocessing {
    int[] nodeImportance;

    public CHPreprocessing() {}

    public Graph preprocess(Graph graph) {
        System.out.println("Starting chunked preprocessing: " + graph.nodeCount + " nodes");

        // 1. Calculate simplified contraction ordering
        int[] contractionOrder = contractionOrder(graph);

        // 2. Process in completely independent chunks
        int chunkSize = 100000; // Process 100K nodes at once
        int chunkCount = (graph.nodeCount + chunkSize - 1) / chunkSize;
        System.out.println("Processing graph in " + chunkCount + " chunks of " + chunkSize + " nodes each");

        List<Edge> allShortcuts = new ArrayList<>();
        boolean[] contracted = new boolean[graph.nodeCount];

        for (int chunk = 0; chunk < chunkCount; chunk++) {
            int startIdx = chunk * chunkSize;
            int endIdx = Math.min((chunk + 1) * chunkSize, graph.nodeCount);

            // Process this chunk without dependency checking
            List<Edge> chunkShortcuts = processChunkIndependently(
                    graph, contractionOrder, startIdx, endIdx, contracted);

            // Add shortcuts from this chunk
            allShortcuts.addAll(chunkShortcuts);

            System.out.println("Chunk " + (chunk+1) + " complete. " +
                    chunkShortcuts.size() + " shortcuts added. " +
                    "Total shortcuts: " + allShortcuts.size());
        }

        rebuildGraph(graph, allShortcuts);

        // 3. Set node levels based on contraction order
        assignNodeLevels(graph, contractionOrder);

        System.out.println("Chunked preprocessing complete.");
        return graph;
    }

    private void rebuildGraph(Graph graph, List<Edge> shortcuts) {
        int requiredSize = graph.edgeCount + shortcuts.size();
        if (graph.edges.length < requiredSize) {
            Edge[] newEdges = new Edge[requiredSize];
            System.arraycopy(graph.edges, 0, newEdges, 0, graph.edgeCount);
            graph.edges = newEdges;
        }

        // Add new shortcuts
        for (Edge shortcut : shortcuts) {
            graph.edges[graph.edgeCount++] = shortcut;
        }

        // Rebuild offset arrays
        graph.buildOffsetArrays();
    }


    private void assignNodeLevels(Graph graph, int[] contractionOrder) {
        // Map from node ID to rank
        int[] rankMap = new int[graph.nodeCount];
        for (int i = 0; i < contractionOrder.length; i++) {
            rankMap[contractionOrder[i]] = i;
        }

        // Set node levels
        for (int i = 0; i < graph.nodeCount; i++) {
            graph.nodes[i].setLevel(rankMap[i]);
        }
    }

    private int[] contractionOrder(Graph graph) {
        System.out.println("Computing contraction order...");

        nodeImportance = new int[graph.nodeCount];

        // Calculate initial node importance based on degree
        for (int i = 0; i < graph.nodeCount; i++) {
            int inDegree = graph.inOffsets[i + 1] - graph.inOffsets[i];
            int outDegree = graph.outOffsets[i + 1] - graph.outOffsets[i];
            nodeImportance[i] = inDegree + outDegree;
        }

        // Create node list
        Integer[] nodeIds = new Integer[graph.nodeCount];
        for (int i = 0; i < graph.nodeCount; i++) {
            nodeIds[i] = i;
        }

        // Sort by importance
        final int[] importance = nodeImportance;
        Arrays.sort(nodeIds, Comparator.comparingInt(id -> importance[id]));

        // Convert to primitive array
        int[] contractionOrder = new int[graph.nodeCount];
        for (int i = 0; i < graph.nodeCount; i++) {
            contractionOrder[i] = nodeIds[i];
        }

        return contractionOrder;
    }

    /**
     * Process a chunk of nodes completely independently
     */
    private List<Edge> processChunkIndependently(
            Graph graph, int[] contractionOrder,
            int startIdx, int endIdx, boolean[] contracted) {

        List<Edge> shortcuts = new ArrayList<>();

        // Process each node in this chunk
        for (int i = startIdx; i < endIdx; i++) {
            int nodeToContract = contractionOrder[i];

            // Skip if already contracted
            if (contracted[nodeToContract]) {
                continue;
            }

            // Collect all edges
            List<Edge> inEdges = new ArrayList<>();
            List<Edge> outEdges = new ArrayList<>();

            // Get incoming edges from uncontracted nodes
            for (int j = graph.inOffsets[nodeToContract]; j < graph.inOffsets[nodeToContract + 1]; j++) {
                Edge edge = graph.inEdges[j];
                if (!contracted[edge.getSource()]) {
                    inEdges.add(edge);
                }
            }

            // Get outgoing edges to uncontracted nodes
            for (int j = graph.outOffsets[nodeToContract]; j < graph.outOffsets[nodeToContract + 1]; j++) {
                Edge edge = graph.outEdges[j];
                if (!contracted[edge.getTarget()]) {
                    outEdges.add(edge);
                }
            }

            // IMPORTANT: For large graphs, we need to ensure connectivity
            // Create necessary shortcuts between all neighbors - eliminate witness path search completely
            // This is less efficient but ensures paths exist
            for (Edge inEdge : inEdges) {
                for (Edge outEdge : outEdges) {
                    // Skip self-loops
                    if (inEdge.getSource() == outEdge.getTarget()) continue;

                    // Create shortcut
                    int dist = inEdge.getWeight() + outEdge.getWeight();
                    shortcuts.add(new Edge(inEdge.getSource(), outEdge.getTarget(), dist));
                }
            }

            // Mark as contracted
            contracted[nodeToContract] = true;
        }

        return shortcuts;
    }
}
