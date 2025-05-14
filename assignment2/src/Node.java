public class Node {
    private final int id;
    private final long osmId;
    private final double lat;
    private final double lon;
    private final int height;
    private int level;

    /**
     * Create a new node.
     *
     * @param id node ID
     * @param osmId OSM ID
     * @param lat latitude
     * @param lon longitude
     * @param height height (elevation)
     * @param level contraction level
     */
    public Node(int id, long osmId, double lat, double lon, int height, int level) {
        this.id = id;
        this.osmId = osmId;
        this.lat = lat;
        this.lon = lon;
        this.height = height;
        this.level = level;
    }

    /**
     * Create a new node with default level 0.
     *
     * @param id node ID
     * @param osmId OSM ID
     * @param lat latitude
     * @param lon longitude
     * @param height height (elevation)
     */
    public Node(int id, long osmId, double lat, double lon, int height) {
        this(id, osmId, lat, lon, height, 0);
    }

    public int getId() { return id; }
    public long getOsmId() { return osmId; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public int getHeight() { return height; }
    public int getLevel() { return level; }

    public void setLevel(int level) { this.level = level; }
}
