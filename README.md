Cooperative Map Coloring with Divide & Conquer Bot

A Java Swing-based visualization of the Map Coloring Constraint Satisfaction Problem (CSP). This project features an interactive game where a human player cooperates with an Bot to color a planar map using only 4 colors, ensuring no two adjacent regions share the same color.

The Bot uses a Divide and Conquer algorithm combined with Backtracking and Heuristics (MRV) to validate moves and solve the puzzle in real-time.

Features

Divide & Conquer Algorithm: Geometrically partitions the map into clusters to solve them independently before merging.

Cooperative Bot: The Bot runs a background solver after every human move to detect "future dead ends" and correct them.

Conflict Resolution: Visual feedback when the Bot detects a constraint violation or an unsolvable state.

Failure Case Demo: Includes a specific scenario demonstration in the GUI (Start-Up Deadlock).

Algorithms Used

Geometric Partitioning (Divide): Uses BFS to identify two distant "seed" regions and splits the graph into two partitions based on proximity.

Backtracking (Conquer): Recursively assigns colors. Used as the base case for small partitions (< 6 regions).

Seam Merging (Combine): Identifies conflicting regions on the partition boundary and re-solves them locally.

Minimum Remaining Values (MRV): A heuristic that prioritizes coloring the most constrained regions first to fail fast.

File Structure

src/game1/MapColoring.java: The main application source code containing the algorithmic logic (DivideAndConquerBot, GameGraph, etc.) and the main entry point.

src/game1/GameGUI.java: The GUI implementation, which forces a "Victim Region" scenario (Deadlock) to demonstrate algorithm limitations during presentations.

Created for Algorithms Evaluation - Divide & Conquer Implementation
