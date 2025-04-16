import java.io.IOException;
import java.util.Random;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) throws IOException {
        // Graph graph = new Graph("/home/feleuxens/Documents/Studium/Master/Semester-2/algorithmik/assignment1/stgtregbz.fmi");
        System.out.println("Starting to read graph");
        long startTime = System.currentTimeMillis();
        Graph graph = new Graph("/home/feleuxens/Documents/Studium/Master/Semester-2/algorithmik/assignment1/germany.fmi");
        long endTime = System.currentTimeMillis();
        System.out.println("Finished reading graph in " + (endTime - startTime) + " ms");


        System.out.println("Counting weakly connected components");
        startTime = System.currentTimeMillis();
        int numComponents = graph.countWeaklyConnectedComponents();
        endTime = System.currentTimeMillis();

        System.out.println("Weakly connected components: " + numComponents);
        System.out.println("Time: " + (endTime - startTime) + " ms");


        System.out.println("Preparing dijkstra");
        Dijkstra dijkstra = new Dijkstra(graph);

        int n = graph.nodes.length;
        Random r = new Random();

        double totalTime = 0;
        int successful = 0;

        for (int i = 0; i < 100; i++) {
            System.out.println("Query: " + i);
            int source = r.nextInt(n);
            int target = r.nextInt(n);

            long start = System.nanoTime();
            double distance = dijkstra.shortestPath(source, target);
            long end = System.nanoTime();
            System.out.println("Query time: " + (end - start) + " ns");

            if (distance < Double.POSITIVE_INFINITY) {
                successful++;
            }

            totalTime += (double) (end - start) / 1_000_000;
        }

        System.out.printf("Successful paths: %d/100%n", successful);
        System.out.printf("Average time per query: %.3f ms%n", totalTime / 100.0);
    }
}