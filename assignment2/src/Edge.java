/**
 * Edge class to represent a directed edge in the graph.
 */
public class Edge {
    public final int source;
    public final int target;
    public final int weight;
    public final int type;
    public final int maxSpeed;
    public final int edgeIdA;  // -1 for original edges, otherwise ID of first replaced edge
    public final int edgeIdB;  // -1 for original edges, otherwise ID of second replaced edge

    // Flags for CH preprocessing
    private final boolean isShortcut;
    private boolean forward;
    private boolean backward;

    /**
     * Create a new edge.
     *
     * @param source source node ID
     * @param target target node ID
     * @param weight edge weight
     * @param type edge type
     * @param maxSpeed maximum speed
     * @param edgeIdA ID of first replaced edge (-1 if not a shortcut)
     * @param edgeIdB ID of second replaced edge (-1 if not a shortcut)
     */
    public Edge(int source, int target, int weight, int type, int maxSpeed, int edgeIdA, int edgeIdB) {
        this.source = source;
        this.target = target;
        this.weight = weight;
        this.type = type;
        this.maxSpeed = maxSpeed;
        this.edgeIdA = edgeIdA;
        this.edgeIdB = edgeIdB;

        this.isShortcut = (edgeIdA != -1 || edgeIdB != -1);
        this.forward = true;
        this.backward = true;
    }

    /**
     * Create a simple edge.
     *
     * @param source source node ID
     * @param target target node ID
     * @param weight edge weight
     */
    public Edge(int source, int target, int weight) {
        this(source, target, weight, 0, 0, -1, -1);
    }

    /**
     * Create a shortcut edge.
     *
     * @param source source node ID
     * @param target target node ID
     * @param weight edge weight
     * @param edgeIdA ID of first replaced edge
     * @param edgeIdB ID of second replaced edge
     * @return a new shortcut edge
     */
    public static Edge createShortcut(int source, int target, int weight, int edgeIdA, int edgeIdB) {
        return new Edge(source, target, weight, 0, 0, edgeIdA, edgeIdB);
    }

    /**
     * Get the source node ID.
     *
     * @return source node ID
     */
    public int getSource() {
        return source;
    }

    /**
     * Get the target node ID.
     *
     * @return target node ID
     */
    public int getTarget() {
        return target;
    }

    /**
     * Get the edge weight.
     *
     * @return edge weight
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Get the edge type.
     *
     * @return edge type
     */
    public int getType() {
        return type;
    }

    /**
     * Get the maximum speed.
     *
     * @return maximum speed
     */
    public int getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Get the ID of the first replaced edge.
     *
     * @return edge ID or -1 if not a shortcut
     */
    public int getEdgeIdA() {
        return edgeIdA;
    }

    /**
     * Get the ID of the second replaced edge.
     *
     * @return edge ID or -1 if not a shortcut
     */
    public int getEdgeIdB() {
        return edgeIdB;
    }

    /**
     * Check if this edge is a shortcut.
     *
     * @return true if a shortcut, false otherwise
     */
    public boolean isShortcut() {
        return isShortcut;
    }

    /**
     * Check if this edge can be used in the forward direction.
     *
     * @return true if forward, false otherwise
     */
    public boolean isForward() {
        return forward;
    }

    /**
     * Check if this edge can be used in the backward direction.
     *
     * @return true if backward, false otherwise
     */
    public boolean isBackward() {
        return backward;
    }

    /**
     * Set whether this edge can be used in the forward direction.
     *
     * @param forward true if forward, false otherwise
     */
    public void setForward(boolean forward) {
        this.forward = forward;
    }

    /**
     * Set whether this edge can be used in the backward direction.
     *
     * @param backward true if backward, false otherwise
     */
    public void setBackward(boolean backward) {
        this.backward = backward;
    }

    @Override
    public String toString() {
        return "Edge{" + source + "->" + target + ", weight=" + weight +
                (isShortcut ? ", shortcut" : "") + "}";
    }
}