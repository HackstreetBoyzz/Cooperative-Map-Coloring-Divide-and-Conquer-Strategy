package game1;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import game1.BotMoveResult;
import game1.BotStrategy;
import game1.Cell;
import game1.GameGraph;
import game1.Region;
import game1.mapgeneration;

//Main GUI class
class GameGUI extends JFrame {
 GameGraph graph;
 BotStrategy bot;
 Cell[][] grid;
 int gridRows, gridCols;
 int cellSize = 20;

 boolean isHumanTurn = true;
 int selectedColor = -1;
 int hoveredRegion = -1;

 JPanel mapPanel;
 JLabel statusLabel, phaseLabel, statsLabel;
 JButton[] colorButtons;

 Set<Integer> hlA = new HashSet<>();
 Set<Integer> hlB = new HashSet<>();
 Set<Integer> hlSeam = new HashSet<>();
 boolean showOverlay = false;
 int correctedRegion = -1;

 Color[] COLORS = {
     new Color(220, 30, 75),
     new Color(50, 175, 70),
     new Color(0, 120, 195),
     new Color(245, 130, 45),
 };
 
 // translucent colors for overlay
 Color TINT_SEAM = new Color(255, 40, 40, 130);

 public GameGUI(int numRegions, int numColors, int gridRows, int gridCols) {
     mapgeneration gen = new mapgeneration(gridRows, gridCols);
     List<Region> regions = gen.generateRegions(numRegions);
     this.grid = gen.getGrid();
     this.gridRows = gridRows;
     this.gridCols = gridCols;
     this.graph = new GameGraph(regions, grid, gridRows, gridCols, numColors);
     
     lockInitialRegions();
     this.bot = new BotStrategy(graph);
     
     buildGUI();
 }

 // randomly lock some regions as clues
 private void lockInitialRegions() {
     Random rnd = new Random();
     List<Region> regions = graph.getRegions();
     int nc = graph.getNumColors();
     int numToLock = Math.max(nc, regions.size() / 5);

     List<Integer> avail = new ArrayList<>();
     for (int i = 0; i < regions.size(); i++) avail.add(i);
     Collections.shuffle(avail);

     // ensure at least one of each color is present if possible
     for (int color = 0; color < nc && !avail.isEmpty(); color++) {
         int rid = avail.remove(0);
         Region r = regions.get(rid);
         if (graph.availableColors(rid).contains(color)) {
             r.color = color;
             r.isLocked = true;
         }
     }
     
     // lock more random regions
     while (!avail.isEmpty() && countLocked() < numToLock) {
         int rid = avail.remove(0);
         Region r = regions.get(rid);
         Set<Integer> ok = graph.availableColors(rid);
         if (!ok.isEmpty()) {
             // pick a random valid color
             Integer[] arr = ok.toArray(new Integer[0]);
             int c = arr[rnd.nextInt(arr.length)];
             r.color = c;
             r.isLocked = true;
         }
     }
 }

 private int countLocked() {
     int n = 0;
     for (Region r : graph.getRegions()) {
         if (r.isLocked) n++;
     }
     return n;
 }

 private void buildGUI() {
     setTitle("Map Coloring Game");
     setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
     setLayout(new BorderLayout(8, 8));

     JPanel top = new JPanel(new GridLayout(3, 1));
     top.setBackground(new Color(240, 240, 240));

     statusLabel = makeLabel("Select a color and click a region", 14, Font.BOLD, Color.BLACK);
     phaseLabel = makeLabel("The Bot will help you solve it", 12, Font.BOLD, new Color(0, 100, 200));
     statsLabel = makeLabel(statsText(), 11, Font.ITALIC, Color.DARK_GRAY);

     top.add(statusLabel);
     top.add(phaseLabel);
     top.add(statsLabel);
     add(top, BorderLayout.NORTH);

     JPanel colorPanel = new JPanel();
     colorPanel.setBorder(BorderFactory.createTitledBorder("Colors"));
     colorPanel.setBackground(new Color(250, 250, 250));

     colorButtons = new JButton[graph.getNumColors()];
     for (int i = 0; i < graph.getNumColors(); i++) {
         final int ci = i; // needs to be final for lambda
         JButton btn = new JButton("Color " + (i + 1));
         btn.setPreferredSize(new Dimension(100, 48));
         btn.setBackground(COLORS[i]);
         btn.setOpaque(true);
         btn.setBorderPainted(true);
         btn.setForeground(ci == 3 ? Color.BLACK : Color.WHITE);

         btn.addActionListener(e -> selectColor(ci));

         colorButtons[i] = btn;
         colorPanel.add(btn);
     }

     add(colorPanel, BorderLayout.SOUTH);

     mapPanel = new JPanel() {
         @Override
         protected void paintComponent(Graphics g) {
             super.paintComponent(g);
             drawMap(g);
         }
     };
     mapPanel.setPreferredSize(new Dimension(gridCols * cellSize + 100, gridRows * cellSize + 100));
     mapPanel.setBackground(Color.WHITE);

     mapPanel.addMouseMotionListener(new MouseMotionAdapter() {
         @Override
         public void mouseMoved(MouseEvent e) {
             onHover(e.getX(), e.getY());
         }
     });

     mapPanel.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
             if (isHumanTurn && selectedColor != -1) {
                 onClickMap(e.getX(), e.getY());
             }
         }
         @Override
         public void mouseExited(MouseEvent e) {
             hoveredRegion = -1;
             mapPanel.repaint();
         }
     });

     add(new JScrollPane(mapPanel), BorderLayout.CENTER);
     pack();
     setLocationRelativeTo(null);
 }

 private JLabel makeLabel(String text, int size, int style, Color fg) {
     JLabel l = new JLabel(text, SwingConstants.CENTER);
     l.setFont(new Font("Arial", style, size));
     l.setForeground(fg);
     return l;
 }

 private void selectColor(int color) {
     if (!isHumanTurn) {
         statusLabel.setText("Wait, Bot is thinking...");
         return;
     }
     selectedColor = color;
     for (int i = 0; i < colorButtons.length; i++) {
         if (i == color) {
             colorButtons[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 3));
         } else {
             colorButtons[i].setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
         }
     }
     statusLabel.setText("Color " + (color + 1) + " selected.");
 }

 private void onHover(int mx, int my) {
     int col = (mx - 50) / cellSize;
     int row = (my - 50) / cellSize;

     if (row >= 0 && row < gridRows && col >= 0 && col < gridCols) {
         int rid = grid[row][col].regionId;
         if (rid != hoveredRegion) {
             hoveredRegion = rid;
             if (isHumanTurn && selectedColor != -1) {
                 Region r = graph.getRegions().get(rid);
                 if (r.isLocked) {
                     statusLabel.setText("Region " + rid + " is locked.");
                 } else {
                     statusLabel.setText("Click to color Region " + rid);
                 }
             }
             mapPanel.repaint();
         }
     }
 }

 private void onClickMap(int mx, int my) {
     int col = (mx - 50) / cellSize;
     int row = (my - 50) / cellSize;

     if (row < 0 || row >= gridRows || col < 0 || col >= gridCols) return;

     int rid = grid[row][col].regionId;
     Region region = graph.getRegions().get(rid);

     if (region.isLocked) {
         JOptionPane.showMessageDialog(this, "You cannot change a locked region.", "Warning", JOptionPane.WARNING_MESSAGE);
         return;
     }

     region.color = selectedColor;
     mapPanel.repaint();

     if (bot.isPuzzleSolved()) {
         showVictory();
         return;
     }

     isHumanTurn = false;
     selectedColor = -1;

     // visual reset
     for (JButton b : colorButtons) {
         b.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
     }

     statusLabel.setText("Bot running solver...");
     phaseLabel.setText("Checking for conflicts...");

     // use a timer so the UI updates first
     Timer t = new Timer(900, e -> {
         BotMoveResult res = bot.reactToHumanMove(rid);
         applyBotResult(res);
     });
     t.setRepeats(false);
     t.start();
 }

 private void applyBotResult(BotMoveResult r) {
     correctedRegion = -1;
     hlA = r.partitionA;
     hlB = r.partitionB;
     hlSeam = r.boundary;
     showOverlay = true;

     if (r.humanMoveCorrected) {
         correctedRegion = r.humanRegionId;
         phaseLabel.setText("Bot corrected your move on Region " + r.humanRegionId);
         statusLabel.setText("Conflict corrected!");
     } else {
         phaseLabel.setText("Bot checked constraints & future moves");
         if (r.botRegionId != null) {
             statusLabel.setText("Bot colored Region " + r.botRegionId);
         } else {
             statusLabel.setText("Your turn!");
         }
     }

     statsLabel.setText(statsText());
     mapPanel.repaint();

     if (bot.isPuzzleSolved()) {
         showVictory();
         return;
     }

     // hide overlay after delay
     Timer t = new Timer(1500, e -> {
         showOverlay = false;
         correctedRegion = -1;
         mapPanel.repaint();
     });
     t.setRepeats(false);
     t.start();

     isHumanTurn = true;
 }

 private String statsText() {
     int total = graph.getRegions().size();
     int locked = countLocked();
     int colored = 0;
     int conflicts = 0;

     for (Region r : graph.getRegions()) {
         if (r.color != -1) {
             colored++;
             if (graph.inConflict(r.id)) conflicts++;
         }
     }
     return colored + "/" + total + " colored | " + conflicts + " conflicts";
 }

 private void drawMap(Graphics g) {
     Graphics2D g2 = (Graphics2D) g;
     g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

     int ox = 50, oy = 50;

     for (Region r : graph.getRegions()) {
         Color fill = (r.color == -1) ? new Color(218, 218, 218) : COLORS[r.color];

         if (r.id == hoveredRegion && isHumanTurn && selectedColor != -1) fill = fill.brighter();

         for (Cell c : r.cells) {
             g2.setColor(fill);
             g2.fillRect(ox + c.col * cellSize, oy + c.row * cellSize, cellSize, cellSize);
         }
     }

     if (showOverlay) {
         for (Region r : graph.getRegions()) {
             Color tint = null;
             if (hlSeam.contains(r.id)) tint = TINT_SEAM;
             
             if (tint != null) {
                 g2.setColor(tint);
                 for (Cell c : r.cells) {
                     g2.fillRect(ox + c.col * cellSize, oy + c.row * cellSize, cellSize, cellSize);
                 }
             }
         }
     }

     g2.setColor(Color.BLACK);
     g2.setStroke(new BasicStroke(2));
     
     // draw grid lines for region boundaries
     for (int row = 0; row < gridRows; row++) {
         for (int col = 0; col < gridCols; col++) {
             int x = ox + col * cellSize;
             int y = oy + row * cellSize;
             Cell cell = grid[row][col];
             
             if (col < gridCols - 1 && grid[row][col + 1].regionId != cell.regionId) {
                 g2.drawLine(x + cellSize, y, x + cellSize, y + cellSize);
             }
             if (row < gridRows - 1 && grid[row + 1][col].regionId != cell.regionId) {
                 g2.drawLine(x, y + cellSize, x + cellSize, y + cellSize);
             }
         }
     }

     // draw text
     g2.setFont(new Font("Arial", Font.BOLD, 10));
     for (Region r : graph.getRegions()) {
         Point cen = r.getCentroid();
         int x = ox + cen.x * cellSize + cellSize / 2;
         int y = oy + cen.y * cellSize + cellSize / 2;

         if (r.isLocked) {
             g2.setFont(new Font("Arial", Font.BOLD, 13));
             g2.setColor(Color.BLACK);
             g2.drawString("L", x - 4, y + 5);
             g2.setFont(new Font("Arial", Font.BOLD, 10));
         } else if (r.color == -1) {
             g2.setColor(Color.DARK_GRAY);
             g2.drawString(String.valueOf(r.id), x - 5, y + 5);
         } else if (graph.inConflict(r.id)) {
             g2.setColor(Color.RED);
             g2.setFont(new Font("Arial", Font.BOLD, 15));
             g2.drawString("X", x - 4, y + 5);
             g2.setFont(new Font("Arial", Font.BOLD, 10));
         } else if (r.id == correctedRegion) {
             g2.setColor(new Color(140, 0, 210));
             g2.setFont(new Font("Arial", Font.BOLD, 13));
             g2.drawString("ok", x - 7, y + 5);
             g2.setFont(new Font("Arial", Font.BOLD, 10));
         }
     }
 }

 private void showVictory() {
     statusLabel.setText("Puzzle solved!");
     phaseLabel.setText("Great job!");
     JOptionPane.showMessageDialog(this, "Congrats Game Finished Successfully!", "FINISHED!", JOptionPane.INFORMATION_MESSAGE);
 }
}