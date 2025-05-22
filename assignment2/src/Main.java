import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        String graphFile = "graph.fmi";
        graphFile = "stgtregbz.fmi";

        if (args.length >= 1) {
            graphFile = args[0];
        }

        // Check if file exists
        File graphFileObj = new File(graphFile);
        if (!graphFileObj.exists()) {
            System.out.println("Graph file not found: " + graphFile);
            System.out.println("Please make sure graph.fmi exists in the current directory.");
            return;
        }

        try {
            // Load graph
            Timer.startTiming("Graph loading");
            Graph graph = Graph.fromFile(graphFile);
            Timer.stopTiming("Graph loading");

            System.out.println("Graph loaded: " + graph.nodeCount + " nodes, " + graph.edgeCount + " edges");
            // Preprocess the graph to create a CH
            System.out.println("Starting CH preprocessing...");

            int originalEdgeCount = graph.edgeCount;

            Timer.startTiming("Preprocessing");
            CHPreprocessing preprocessor = new CHPreprocessing(graph);
            preprocessor.buildHierarchy();
            Timer.stopTiming("Preprocessing");

            System.out.println("CH preprocessing completed:");
            System.out.println("  - Original edges: " + originalEdgeCount);
            System.out.println("  - Shortcuts added: " + (graph.edgeCount - originalEdgeCount));
            System.out.println("  - Total edges: " + graph.edgeCount);

            // Write the CH graph to file
            Timer.startTiming("Writing CH graph");
            graph.writeToFile("graph.ch");
            Timer.stopTiming("Writing CH graph");

            CHQuery query = new CHQuery(graph);

            try (PrintWriter writer = new PrintWriter("results.txt")) {
                // Parse queries from queries.txt if it exists
                File queriesFile = new File("queries.txt");
                if (queriesFile.exists()) {
                    System.out.println("Processing queries for results.txt...");
                    try (BufferedReader reader = new BufferedReader(new FileReader(queriesFile))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) continue;

                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) {
                                int source = Integer.parseInt(parts[0]);
                                int target = Integer.parseInt(parts[1]);
                                long start_time = System.nanoTime();
                                double distance = query.queryDistance(source, target);
                                long elapsed = (System.nanoTime() - start_time) / 1000;

                                writer.println(source + " " + target + " " + distance + " " + elapsed);
                                Timer.reset();
                            }
                        }
                    }
                }
            }

            System.out.println("Results saved to results.txt");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}