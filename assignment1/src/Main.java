import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        Graph graph = new Graph("graph.fmi");

        long startTime = System.currentTimeMillis();
        int numComponents = graph.countWeaklyConnectedComponents();
        long endTime = System.currentTimeMillis();

        System.out.println("Weakly connected components: " + numComponents);
        System.out.println("Time: " + (endTime - startTime) + " ms");

        Dijkstra dijkstra = new Dijkstra(graph);

        BufferedReader br = new BufferedReader(new FileReader("queries.txt"));
        StringBuilder result = new StringBuilder();
        String[] queryLine;
        double distance;
        for (String query = br.readLine(); query != null; query = br.readLine()) {
            if (query.trim().isEmpty() || query.startsWith("#")) continue;

            queryLine = query.split(" ");
            startTime = System.currentTimeMillis();
            distance = dijkstra.shortestPath(Integer.parseInt(queryLine[0]), Integer.parseInt(queryLine[1]));
            endTime = System.currentTimeMillis();
            result.append(queryLine[0]).append(" ").append(queryLine[1]).append(" ").append(distance).append(" ").append(endTime - startTime).append("ms\n");
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter("result.txt", false));
        writer.write(result.toString());
        writer.close();
    }
}