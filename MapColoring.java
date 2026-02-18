package game1;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.*;

// class representing a single pixel/block in the grid
class Cell {
    int row, col;
    int regionId = -1; // -1 means no region assigned yet

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }
}

// class for a region of cells
class Region {
    int id;
    int color = -1; // -1 means uncolored
    boolean isLocked = false; // true if it's a starting clue
    Set<Cell> cells = new HashSet<>();

    public Region(int id) {
        this.id = id;
    }

    public void addCell(Cell c) {
        cells.add(c);
    }

    // calculates center for drawing the text
    public Point getCentroid() {
        int sr = 0, sc = 0;
        for (Cell c : cells) {
            sr += c.row;
            sc += c.col;
        }
        if (cells.size() > 0) {
            return new Point(sc / cells.size(), sr / cells.size());
        }
        return new Point(0, 0);
    }
}

// generates the random map structure
class mapgeneration {
    Random random = new Random();
    int gridRows, gridCols;
    Cell[][] grid;
    List<Region> regions = new ArrayList<>();
    static final int MIN_REGION_SIZE = 8;

    public mapgeneration(int gridRows, int gridCols) {
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.grid = new Cell[gridRows][gridCols];
        // init grid
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                grid[r][c] = new Cell(r, c);
            }
        }
    }

    // uses a seed growth approach to make regions
    public List<Region> generateRegions(int numRegions) {
        boolean[][] visited = new boolean[gridRows][gridCols];
        int avg = (gridRows * gridCols) / numRegions;
        int min = Math.max(MIN_REGION_SIZE, avg / 2);
        
        List<Cell> seeds = pickSeeds(numRegions);

        for (int i = 0; i < numRegions; i++) {
            Region region = new Region(i);
            Cell seed = seeds.get(i);
            
            // varied target size for natural look
            int target = Math.max(min, (int)(avg * (0.7 + random.nextDouble() * 0.6)));

            Queue<Cell> q = new LinkedList<>();
            q.add(seed);
            visited[seed.row][seed.col] = true;

            // grow region using bfs
            while (!q.isEmpty() && region.cells.size() < target) {
                Cell cur = q.poll();
                cur.regionId = i;
                region.addCell(cur);
                
                List<Cell> nbrs = neighbors(cur);
                Collections.shuffle(nbrs); // randomization
                
                for (Cell nb : nbrs) {
                    if (!visited[nb.row][nb.col]) {
                        // random chance to stop growing in one direction
                        if (random.nextDouble() < (region.cells.size() < min ? 0.95 : 0.65)) {
                            visited[nb.row][nb.col] = true;
                            q.add(nb);
                        }
                    }
                }
            }
            
            // force min size if too small
            while (region.cells.size() < min) {
                Cell exp = nearestUnvisited(region, visited);
                if (exp == null) break;
                visited[exp.row][exp.col] = true;
                exp.regionId = i;
                region.addCell(exp);
            }
            regions.add(region);
        }

        // fill in any empty spots (orphans)
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                Cell cell = grid[r][c];
                if (cell.regionId == -1) {
                    int nr = nearestRegion(cell);
                    cell.regionId = nr;
                    regions.get(nr).addCell(cell);
                }
            }
        }
        return regions;
    }

    private List<Cell> pickSeeds(int n) {
        List<Cell> seeds = new ArrayList<>();
        int minDist = (int) Math.sqrt((gridRows * gridCols) / n);
        int attempts = 1000;
        
        while (seeds.size() < n && attempts-- > 0) {
            Cell c = grid[random.nextInt(gridRows)][random.nextInt(gridCols)];
            boolean ok = true;
            for (Cell s : seeds) {
                if (Math.sqrt(Math.pow(c.row - s.row, 2) + Math.pow(c.col - s.col, 2)) < minDist) {
                    ok = false;
                    break;
                }
            }
            if (ok) seeds.add(c);
        }
        
        // if we couldn't find good spaced seeds, just pick random ones
        while (seeds.size() < n) {
            Cell c = grid[random.nextInt(gridRows)][random.nextInt(gridCols)];
            if (!seeds.contains(c)) seeds.add(c);
        }
        return seeds;
    }

    private Cell nearestUnvisited(Region region, boolean[][] visited) {
        for (Cell rc : region.cells) {
            for (Cell nb : neighbors(rc)) {
                if (!visited[nb.row][nb.col]) return nb;
            }
        }
        return null;
    }

    private List<Cell> neighbors(Cell cell) {
        List<Cell> list = new ArrayList<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = cell.row + d[0];
            int nc = cell.col + d[1];
            if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols) {
                list.add(grid[nr][nc]);
            }
        }
        return list;
    }

    private int nearestRegion(Cell cell) {
        for (Cell nb : neighbors(cell)) {
            if (nb.regionId != -1) return nb.regionId;
        }
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (Region r : regions) {
            Point cen = r.getCentroid();
            double d = Math.sqrt(Math.pow(cell.row - cen.y, 2) + Math.pow(cell.col - cen.x, 2));
            if (d < bestD) {
                bestD = d;
                best = r.id;
            }
        }
        return best;
    }

    public Cell[][] getGrid() { return grid; }
    public int getGridRows() { return gridRows; }
    public int getGridCols() { return gridCols; }
}

// this class handles the graph structure (adjacency)
class GameGraph {
    List<Region> regions;
    Map<Integer, Set<Integer>> adj = new HashMap<>();
    int numColors;

    public GameGraph(List<Region> regions, Cell[][] grid, int gridRows, int gridCols, int numColors) {
        this.regions = regions;
        this.numColors = numColors;
        for (Region r : regions) {
            adj.put(r.id, new HashSet<>());
        }

        // build adjacency list from the grid
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int rid = grid[r][c].regionId;
                int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                for (int[] d : dirs) {
                    int nr = r + d[0];
                    int nc = c + d[1];
                    if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols) {
                        int nid = grid[nr][nc].regionId;
                        if (nid != rid) {
                            adj.get(rid).add(nid);
                        }
                    }
                }
            }
        }
    }

    public Set<Integer> availableColors(int regionId) {
        Set<Integer> avail = new HashSet<>();
        for (int i = 0; i < numColors; i++) avail.add(i);
        
        for (int n : adj.get(regionId)) {
            int c = regions.get(n).color;
            if (c != -1) avail.remove(c);
        }
        return avail;
    }

    // checks if a region conflicts with its neighbors
    public boolean inConflict(int regionId) {
        int c = regions.get(regionId).color;
        if (c == -1) return false;
        for (int n : adj.get(regionId)) {
            if (regions.get(n).color == c) return true;
        }
        return false;
    }

    public List<Region> getRegions() { return regions; }
    public Set<Integer> getNeighbors(int rid) { return adj.get(rid); }
    public int getNumColors() { return numColors; }
}

// main solver logic using divide and conquer
class DivideAndConquerBot {

    static final int BASE_SIZE = 6; // stop dividing when small enough
    GameGraph graph;
    int numColors;

    // stored for visualization in the GUI
    public Set<Integer> lastPartitionA = new HashSet<>();
    public Set<Integer> lastPartitionB = new HashSet<>();
    public Set<Integer> lastBoundaryRegions = new HashSet<>();

    public DivideAndConquerBot(GameGraph graph) {
        this.graph = graph;
        this.numColors = graph.getNumColors();
    }

    public Map<Integer, Integer> solve() {
        List<Region> regions = graph.getRegions();
        Map<Integer, Integer> assignment = new HashMap<>();

        // initialize current state
        for (Region r : regions) assignment.put(r.id, r.color);

        // check if current state is already invalid
        for (Region r : regions) {
            if (r.color == -1) continue;
            for (int n : graph.getNeighbors(r.id)) {
                if (assignment.getOrDefault(n, -1) == r.color) {
                    System.out.println("Constraint check failed: Region " + r.id + " conflicts with " + n);
                    return null;
                }
            }
        }

        List<Integer> free = new ArrayList<>();
        for (Region r : regions) {
            if (!r.isLocked && r.color == -1) free.add(r.id);
        }

        if (free.isEmpty()) {
            return isFullyValid(assignment) ? assignment : null;
        }

        lastPartitionA = new HashSet<>();
        lastPartitionB = new HashSet<>();
        lastBoundaryRegions = new HashSet<>();

        System.out.println("Starting solver on " + free.size() + " regions...");

        boolean ok = dcSolve(free, assignment, 0);
        if (!ok) return null;

        return isFullyValid(assignment) ? assignment : null;
    }

    // recursive function
    private boolean dcSolve(List<Integer> free, Map<Integer, Integer> assignment, int depth) {
        // base case: small size, just use backtracking
        if (free.size() <= BASE_SIZE) {
            return backtrack(new ArrayList<>(free), 0, assignment);
        }

        // DIVIDE: split graph into two parts
        List<Integer>[] parts = graphBisect(free);
        List<Integer> left = parts[0];
        List<Integer> right = parts[1];

        // fail-safe if split didn't work well
        if (left.isEmpty() || right.isEmpty()) {
            int mid = free.size() / 2;
            left = new ArrayList<>(free.subList(0, mid));
            right = new ArrayList<>(free.subList(mid, free.size()));
        }

        if (depth == 0) {
            lastPartitionA = new HashSet<>(left);
            lastPartitionB = new HashSet<>(right);
        }

        // CONQUER: solve left then right
        if (!dcSolve(left, assignment, depth + 1)) return false;
        if (!dcSolve(right, assignment, depth + 1)) return false;

        // MERGE: fix the boundary (seam) conflicts
        Set<Integer> seam = findSeamConflicts(left, right, assignment);
        lastBoundaryRegions.addAll(seam);

        if (!seam.isEmpty()) {
            List<Integer> seamList = new ArrayList<>(seam);
            // reset seam colors
            for (int rid : seamList) assignment.put(rid, -1);

            // try to fix seam
            if (!backtrack(seamList, 0, assignment)) {
                return false;
            }
        }

        return true;
    }

    // splits the graph based on distance (bfs)
    @SuppressWarnings("unchecked")
    private List<Integer>[] graphBisect(List<Integer> free) {
        Set<Integer> freeSet = new HashSet<>(free);

        // lambda to get distances
        java.util.function.Function<Integer, Map<Integer, Integer>> bfsDist = (src) -> {
            Map<Integer, Integer> dist = new HashMap<>();
            Queue<Integer> q = new LinkedList<>();
            dist.put(src, 0);
            q.add(src);
            while (!q.isEmpty()) {
                int cur = q.poll();
                for (int nb : graph.getNeighbors(cur)) {
                    if (freeSet.contains(nb) && !dist.containsKey(nb)) {
                        dist.put(nb, dist.get(cur) + 1);
                        q.add(nb);
                    }
                }
            }
            return dist;
        };

        int seedA = free.get(0);
        Map<Integer, Integer> distFromSeed = bfsDist.apply(seedA);

        // find farthest node from seedA
        int nodeA = seedA;
        int maxD = -1;
        for(Map.Entry<Integer, Integer> e : distFromSeed.entrySet()){
            if(e.getValue() > maxD) {
                maxD = e.getValue();
                nodeA = e.getKey();
            }
        }

        Map<Integer, Integer> distFromA = bfsDist.apply(nodeA);

        // find farthest from nodeA to get nodeB
        int nodeB = free.get(free.size() / 2);
        maxD = -1;
        for(Map.Entry<Integer, Integer> e : distFromA.entrySet()){
            if(!e.getKey().equals(nodeA) && e.getValue() > maxD) {
                maxD = e.getValue();
                nodeB = e.getKey();
            }
        }

        Map<Integer, Integer> distFromB = bfsDist.apply(nodeB);

        List<Integer> partA = new ArrayList<>();
        List<Integer> partB = new ArrayList<>();
        for (int rid : free) {
            int dA = distFromA.getOrDefault(rid, Integer.MAX_VALUE / 2);
            int dB = distFromB.getOrDefault(rid, Integer.MAX_VALUE / 2);
            if (dA <= dB) partA.add(rid);
            else partB.add(rid);
        }

        return new List[]{ partA, partB };
    }

    private Set<Integer> findSeamConflicts(List<Integer> left, List<Integer> right, Map<Integer, Integer> assignment) {
        Set<Integer> rightSet = new HashSet<>(right);
        Set<Integer> result = new HashSet<>();

        for (int rid : left) {
            int cl = assignment.getOrDefault(rid, -1);
            if (cl == -1) continue;
            for (int n : graph.getNeighbors(rid)) {
                if (rightSet.contains(n) && assignment.getOrDefault(n, -1) == cl) {
                    result.add(rid);
                    result.add(n);
                }
            }
        }
        return result;
    }

    // standard backtracking for small problems or seam fixing
    private boolean backtrack(List<Integer> ids, int index, Map<Integer, Integer> assignment) {
        if (index == ids.size()) return true;

        // MRV  sort by most constrained
        int bestIdx = index;
        int bestCount = Integer.MAX_VALUE;
        for (int i = index; i < ids.size(); i++) {
            int cnt = legalColors(ids.get(i), assignment).size();
            if (cnt < bestCount) {
                bestCount = cnt;
                bestIdx = i;
            }
        }
        Collections.swap(ids, index, bestIdx);
        int rid = ids.get(index);

        List<Integer> legal = legalColors(rid, assignment);
        for (int color : legal) {
            assignment.put(rid, color);
            if (backtrack(ids, index + 1, assignment)) return true;
            assignment.put(rid, -1); // undo
        }

        // backtrack needs to restore order if we swapped?
        // actually for this simple list it's fine as we just fail
        Collections.swap(ids, index, bestIdx);
        return false;
    }

    private List<Integer> legalColors(int rid, Map<Integer, Integer> assignment) {
        Set<Integer> used = new HashSet<>();
        for (int n : graph.getNeighbors(rid)) {
            int c = assignment.getOrDefault(n, -1);
            if (c != -1) used.add(c);
        }
        List<Integer> legal = new ArrayList<>();
        for (int i = 0; i < numColors; i++) {
            if (!used.contains(i)) legal.add(i);
        }
        return legal;
    }

    private boolean isFullyValid(Map<Integer, Integer> assignment) {
        for (Region r : graph.getRegions()) {
            int c = assignment.getOrDefault(r.id, -1);
            if (c == -1) return false;
            for (int n : graph.getNeighbors(r.id)) {
                if (assignment.getOrDefault(n, -1) == c) return false;
            }
        }
        return true;
    }

    // fallback for when global solver fails
    // sometimes we just want a valid color locally even if we can't prove global validity
    public int findSimpleLocalColor(int rid) {
        Set<Integer> used = new HashSet<>();
        for (int n : graph.getNeighbors(rid)) {
            int c = graph.getRegions().get(n).color;
            if (c != -1) used.add(c);
        }
        for (int i = 0; i < numColors; i++) {
            if (!used.contains(i)) return i;
        }
        return -1;
    }

    // attempts to find a color for targetRid that is valid globally
    public int findBestColorForRegion(int targetRid) {
        List<Region> regions = graph.getRegions();
        Map<Integer, Integer> baseAssignment = new HashMap<>();
        for (Region r : regions) baseAssignment.put(r.id, r.color);

        Set<Integer> candidates = new HashSet<>();
        for (int i = 0; i < numColors; i++) candidates.add(i);

        // remove neighbor colors
        for (int n : graph.getNeighbors(targetRid)) {
            int nc = baseAssignment.getOrDefault(n, -1);
            if (nc != -1) candidates.remove(nc);
        }

        for (int tryColor : candidates) {
            Map<Integer, Integer> trial = new HashMap<>(baseAssignment);
            trial.put(targetRid, tryColor);

            List<Integer> free = new ArrayList<>();
            for (Region r : regions) {
                if (!r.isLocked && r.color == -1 && r.id != targetRid) free.add(r.id);
            }

            if (dcSolve(free, trial, 0) && isFullyValid(trial)) {
                System.out.println("Solution found with color " + tryColor);
                return tryColor;
            }
        }
        return -1;
    }
}

// class to return multiple values from the bot logic
class BotMoveResult {
    public boolean humanMoveCorrected;
    public int humanRegionId;
    public int originalColor;
    public int finalColor;
    public Integer botRegionId;
    public int botColor;
    public Set<Integer> partitionA;
    public Set<Integer> partitionB;
    public Set<Integer> boundary;

    public BotMoveResult(boolean corrected, int hRid, int orig, int fin, Integer bRid, int bColor, Set<Integer> pA, Set<Integer> pB, Set<Integer> bd) {
        this.humanMoveCorrected = corrected;
        this.humanRegionId = hRid;
        this.originalColor = orig;
        this.finalColor = fin;
        this.botRegionId = bRid;
        this.botColor = bColor;
        this.partitionA = pA;
        this.partitionB = pB;
        this.boundary = bd;
    }
}

// Logic for the Bot
class BotStrategy {
    GameGraph graph;
    DivideAndConquerBot solver;

    public BotStrategy(GameGraph graph) {
        this.graph = graph;
        this.solver = new DivideAndConquerBot(graph);
    }

    public BotMoveResult reactToHumanMove(int humanRegionId) {
        System.out.println("Bot is checking move on Region " + humanRegionId);

        int humanColor = graph.getRegions().get(humanRegionId).color;

        // Check 1: did the human make a direct conflict?
        if (graph.inConflict(humanRegionId)) {
            System.out.println("Conflict detected! Attempting to fix...");

            // temporarily remove color to find best replacement
            graph.getRegions().get(humanRegionId).color = -1;
            int betterColor = solver.findBestColorForRegion(humanRegionId);

            // if global solver failed, try local fix (greedy)
            if (betterColor == -1) {
                betterColor = solver.findSimpleLocalColor(humanRegionId);
                if (betterColor != -1) System.out.println("Used local fix instead.");
            }

            if (betterColor == -1) {// stuck
                graph.getRegions().get(humanRegionId).color = humanColor;
                System.out.println("No solution found.");
                return new BotMoveResult(false, humanRegionId, humanColor, humanColor,
                        null, -1, solver.lastPartitionA, solver.lastPartitionB, solver.lastBoundaryRegions);
            }

            // apply fix
            graph.getRegions().get(humanRegionId).color = betterColor;
            System.out.println("Corrected to color " + betterColor);

            return new BotMoveResult(true, humanRegionId, humanColor, betterColor, null, -1, solver.lastPartitionA, solver.lastPartitionB, solver.lastBoundaryRegions);
        }

        // Step 2: Human move is valid locally, but does it block the future?
        Map<Integer, Integer> solution = solver.solve();

        if (solution != null) {
            // All good, bot makes a move
            System.out.println("Move accepted.");
            Integer bRid = pickMostConstrained();
            int bColor = -1;
            if (bRid != null) {
                bColor = solution.get(bRid);
                graph.getRegions().get(bRid).color = bColor;
                System.out.println("Bot colored Region " + bRid);
            }
            return new BotMoveResult(false, humanRegionId, humanColor, humanColor, bRid, bColor, solver.lastPartitionA, solver.lastPartitionB, solver.lastBoundaryRegions);
        }

        // Step 3: It blocks the future, so we must change it
        System.out.println("Move leads to dead end. Correcting...");

        graph.getRegions().get(humanRegionId).color = -1;
        int betterColor = solver.findBestColorForRegion(humanRegionId);

        // try local fix if global failed
        if (betterColor == -1) {
            betterColor = solver.findSimpleLocalColor(humanRegionId);
        }

        if (betterColor == -1) {
            graph.getRegions().get(humanRegionId).color = humanColor;
            return new BotMoveResult(false, humanRegionId, humanColor, humanColor, null, -1, solver.lastPartitionA, solver.lastPartitionB, solver.lastBoundaryRegions);
        }

        graph.getRegions().get(humanRegionId).color = betterColor;

        return new BotMoveResult(true, humanRegionId, humanColor, betterColor,
                null, -1, solver.lastPartitionA, solver.lastPartitionB, solver.lastBoundaryRegions);
    }

    // Heuristic: pick the hardest region to color next
    private Integer pickMostConstrained() {
        Integer best = null;
        int minAvail = Integer.MAX_VALUE;
        for (Region r : graph.getRegions()) {
            if (r.isLocked || r.color != -1) continue;
            int a = graph.availableColors(r.id).size();
            if (a < minAvail) {
                minAvail = a;
                best = r.id;
            }
        }
        return best;
    }

    public boolean isPuzzleSolved() {
        for (Region r : graph.getRegions()) {
            if (r.color == -1) return false;
            if (graph.inConflict(r.id)) return false;
        }
        return true;
    }
}

public class MapColoring {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("Starting Map Coloring Game...");
            new GameGUI(25, 4, 20, 25).setVisible(true);
        });
    }
}
