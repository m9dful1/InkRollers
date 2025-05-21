package com.spiritwisestudios.inkrollers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random
import java.util.Stack
import android.util.Log

/**
 * A perfect maze: exactly one path between any two cells.
 * Generates a rectangular grid of cells, builds wall rectangles for collision,
 * and exposes simple draw() / update() hooks.
 */
class MazeLevel(
    private val screenW: Int,
    private val screenH: Int,
    // Default values for fixedCellsX and fixedCellsY are no longer used directly here,
    // but kept for potential reference or other constructors if added later.
    private val _fixedCellsX_default: Int = 12, // Renamed to avoid confusion
    private val _fixedCellsY_default: Int = 20, // Renamed to avoid confusion
    private val wallThickness: Float = 12f,
    private val seed: Long = System.currentTimeMillis(), // Use provided seed or current time as fallback
    private val complexity: String = HomeActivity.COMPLEXITY_HIGH, // Added complexity parameter
    /**
     * Top margin (in pixels) reserved for overlay UI such as the coverage HUD.
     * The maze will be scaled and positioned within the remaining space.
     */
    private val topMargin: Int = 0
) : Level {

    private val calculatedCellDimensions: Pair<Int, Int> = run {
        val baseCellsX: Int
        val baseCellsY: Int
        when (complexity) {
            HomeActivity.COMPLEXITY_LOW -> { baseCellsX = 8; baseCellsY = 12 }
            HomeActivity.COMPLEXITY_MEDIUM -> { baseCellsX = 10; baseCellsY = 16 }
            HomeActivity.COMPLEXITY_HIGH -> { baseCellsX = 12; baseCellsY = 20 }
            else -> { baseCellsX = 12; baseCellsY = 20 } // Default to high
        }
        val cX = if (screenW > screenH) maxOf(baseCellsX, baseCellsY) else minOf(baseCellsX, baseCellsY)
        val cY = if (screenW > screenH) minOf(baseCellsX, baseCellsY) else maxOf(baseCellsX, baseCellsY)
        Pair(cX, cY)
    }

    private val cellsX: Int = calculatedCellDimensions.first
    private val cellsY: Int = calculatedCellDimensions.second

    private val ENTRANCE by lazy { 0 to 0 }
    private val EXIT     by lazy { (cellsX - 1) to (cellsY - 1) }

    private lateinit var horizontalWalls: Array<BooleanArray>
    private lateinit var verticalWalls: Array<BooleanArray>

    private val wallRects = mutableListOf<RectF>()
    private val paint = Paint().apply { color = Color.parseColor("#C0C0C0") }
    
    // Viewport variables to handle proper scaling and positioning
    private var cellSize: Float = 0f   // Size of one cell in pixels
    private var viewportOffsetX: Float = 0f    // X offset to center the maze
    private var viewportOffsetY: Float = 0f    // Y offset to center the maze
    private var mazeWidth: Float = 0f  // Total maze width in pixels
    private var mazeHeight: Float = 0f // Total maze height in pixels
    private var scale: Float = 1f      // Uniform scale factor

    companion object {
        private const val TAG = "MazeLevel"
    }

    init {
        // Log orientation and cell dimensions
        val orientationStr = if (screenW > screenH) "landscape" else "portrait"
        Log.d(TAG, "Creating maze in $orientationStr orientation with cells: ${this.cellsX}x${this.cellsY} for screen ${screenW}x${screenH}, complexity: $complexity")
        
        calculateScaling()
        generateGrids()      // build wall arrays & maze
        buildWallRects()
    }

    /**
     * Calculate scaling factors to ensure proper proportions
     * while ensuring the entire maze is visible
     */
    private fun calculateScaling() {
        // Calculate cell size based on screen dimensions and desired cell count
        // Apply a safety margin (0.9) to ensure the maze doesn't touch screen edges
        val safetyMargin = 0.9f
        // Account for reserved top margin so the maze fits below overlay UI.
        val availableHeight = (screenH - topMargin).toFloat()
        val cellW = (screenW.toFloat() * safetyMargin) / cellsX
        val cellH = (availableHeight * safetyMargin) / cellsY
        
        // Use the smaller dimension to ensure square cells and full visibility
        cellSize = minOf(cellW, cellH)
        
        // Calculate the actual maze dimensions after scaling
        mazeWidth = cellSize * cellsX
        mazeHeight = cellSize * cellsY
        
        // Center the maze on screen
        viewportOffsetX = (screenW - mazeWidth) / 2
        viewportOffsetY = topMargin + (availableHeight - mazeHeight) / 2
        
        // Scale walls thickness proportionally but with a min/max bound
        scale = minOf(maxOf(cellSize / 64f, 0.5f), 2.0f) // Keep scale between 0.5 and 2.0
        
        Log.d("MazeLevel", "Scaling: cellSize=$cellSize, offset=($viewportOffsetX,$viewportOffsetY), scale=$scale, maze size=${mazeWidth}x${mazeHeight}")
    }

    private fun generateGrids() {
        // allocate wall grids with the now‑known sizes
        horizontalWalls = Array(cellsY + 1) { BooleanArray(cellsX) { true } }
        verticalWalls   = Array(cellsY)     { BooleanArray(cellsX + 1) { true } }
        generateMaze()
        ensureMultiplePaths(minPaths = 3)
    }

    /* -------------------------------------------------------------------- */
    /*                            Maze Generation                            */
    /* -------------------------------------------------------------------- */

    private fun generateMaze() {
        /*
         * 180-degree rotational symmetry:
         * Every time we remove a wall between (x,y) and its neighbour in direction dir,
         * we also remove the opposite wall of the rotated cell (rx,ry).
         * We mark both cells as visited together so the DFS never carves them twice.
         */

        fun oppositeDir(d: Int): Int = when (d) { 0 -> 1; 1 -> 0; 2 -> 3; else -> 2 }

        // helper that knocks down chosen wall + its rotated counterpart
        fun knockDownWall(x: Int, y: Int, dir: Int) {
            // original
            when (dir) {
                0 -> horizontalWalls[y][x] = false            // north
                1 -> horizontalWalls[y + 1][x] = false        // south
                2 -> verticalWalls[y][x + 1] = false          // east
                3 -> verticalWalls[y][x] = false              // west
            }

            // rotated counterpart
            val rx = cellsX - 1 - x
            val ry = cellsY - 1 - y
            val rDir = oppositeDir(dir)
            when (rDir) {
                0 -> horizontalWalls[ry][rx] = false
                1 -> horizontalWalls[ry + 1][rx] = false
                2 -> verticalWalls[ry][rx + 1] = false
                3 -> verticalWalls[ry][rx] = false
            }
        }

        val visited = Array(cellsY) { BooleanArray(cellsX) }
        fun markVisited(x: Int, y: Int) {
            if (!visited[y][x]) visited[y][x] = true
            val rx = cellsX - 1 - x
            val ry = cellsY - 1 - y
            if (!visited[ry][rx]) visited[ry][rx] = true
        }

        val stack = Stack<Pair<Int, Int>>()
        var (cx, cy) = ENTRANCE
        markVisited(cx, cy)

        val rnd = Random(seed)

        while (visited.any { row -> row.any { !it } }) {
            // collect unvisited neighbours of (cx,cy)
            val nbrs = mutableListOf<Triple<Int, Int, Int>>() // dx,dy,dir
            if (cy > 0 && !visited[cy - 1][cx]) nbrs += Triple(0, -1, 0) // north
            if (cy < cellsY - 1 && !visited[cy + 1][cx]) nbrs += Triple(0, 1, 1) // south
            if (cx < cellsX - 1 && !visited[cy][cx + 1]) nbrs += Triple(1, 0, 2) // east
            if (cx > 0 && !visited[cy][cx - 1]) nbrs += Triple(-1, 0, 3) // west

            if (nbrs.isNotEmpty()) {
                val (dx, dy, dir) = nbrs.random(rnd)
                val nx = cx + dx
                val ny = cy + dy

                knockDownWall(cx, cy, dir)

                stack.push(cx to cy)
                cx = nx
                cy = ny
                markVisited(cx, cy)
            } else if (stack.isNotEmpty()) {
                val (bx, by) = stack.pop()
                cx = bx
                cy = by
            } else {
                // find any unvisited cell to continue
                outer@ for (y in 0 until cellsY) {
                    for (x in 0 until cellsX) {
                        if (!visited[y][x]) { cx = x; cy = y; break@outer }
                    }
                }
                markVisited(cx, cy)
            }
        }
    }

    /**
     * Ensures there are at least [minPaths] distinct, mirrored paths from entrance to exit.
     * Uses a braiding approach: after generating a perfect maze, selectively removes walls (and their mirrored counterparts)
     * to create additional connections, while maintaining 180-degree rotational symmetry.
     */
    private fun ensureMultiplePaths(minPaths: Int) {
        // Helper to count all unique non-cyclic paths from ENTRANCE to EXIT (DFS, with early exit if over minPaths)
        fun countPaths(maxCount: Int = Int.MAX_VALUE): Int {
            val visited = Array(cellsY) { BooleanArray(cellsX) }
            var pathCount = 0
            fun dfs(x: Int, y: Int) {
                if (x == EXIT.first && y == EXIT.second) {
                    pathCount++
                    return
                }
                if (pathCount >= maxCount) return
                visited[y][x] = true
                // For each direction, check if wall is open and cell is not visited
                val dirs = listOf(
                    Triple(0, -1, 0), // north
                    Triple(0, 1, 1),  // south
                    Triple(1, 0, 2),  // east
                    Triple(-1, 0, 3)  // west
                )
                for ((dx, dy, dir) in dirs) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until cellsX && ny in 0 until cellsY && !visited[ny][nx]) {
                        val open = when (dir) {
                            0 -> !horizontalWalls[y][x]
                            1 -> !horizontalWalls[y + 1][x]
                            2 -> !verticalWalls[y][x + 1]
                            3 -> !verticalWalls[y][x]
                            else -> false
                        }
                        if (open) {
                            dfs(nx, ny)
                            if (pathCount >= maxCount) return
                        }
                    }
                }
                visited[y][x] = false
            }
            dfs(ENTRANCE.first, ENTRANCE.second)
            return pathCount
        }

        // Helper to check if a wall is removable (not on boundary, not already open)
        fun isRemovableWall(x: Int, y: Int, dir: Int): Boolean {
            // Don't braid outer boundary
            return when (dir) {
                0 -> y > 0 && horizontalWalls[y][x] // north
                1 -> y < cellsY - 1 && horizontalWalls[y + 1][x] // south
                2 -> x < cellsX - 1 && verticalWalls[y][x + 1] // east
                3 -> x > 0 && verticalWalls[y][x] // west
                else -> false
            }
        }

        // Try to braid until at least minPaths exist, or a reasonable number of attempts
        val rnd = Random(seed + 42) // Different seed for braiding
        val candidateWalls = mutableListOf<Triple<Int, Int, Int>>()
        for (y in 0 until cellsY) {
            for (x in 0 until cellsX) {
                for (dir in 0..3) {
                    if (isRemovableWall(x, y, dir)) {
                        // Only consider one of each mirrored pair
                        val rx = cellsX - 1 - x
                        val ry = cellsY - 1 - y
                        if (y < ry || (y == ry && x <= rx)) {
                            candidateWalls.add(Triple(x, y, dir))
                        }
                    }
                }
            }
        }
        candidateWalls.shuffle(rnd)
        var attempts = 0
        val maxAttempts = candidateWalls.size * 2
        while (countPaths(minPaths) < minPaths && attempts < maxAttempts && candidateWalls.isNotEmpty()) {
            val (x, y, dir) = candidateWalls.removeAt(0)
            // Remove wall and its mirrored counterpart
            fun knockDownWallPair(x: Int, y: Int, dir: Int) {
                when (dir) {
                    0 -> horizontalWalls[y][x] = false
                    1 -> horizontalWalls[y + 1][x] = false
                    2 -> verticalWalls[y][x + 1] = false
                    3 -> verticalWalls[y][x] = false
                }
                val rx = cellsX - 1 - x
                val ry = cellsY - 1 - y
                val rDir = when (dir) { 0 -> 1; 1 -> 0; 2 -> 3; else -> 2 }
                when (rDir) {
                    0 -> horizontalWalls[ry][rx] = false
                    1 -> horizontalWalls[ry + 1][rx] = false
                    2 -> verticalWalls[ry][rx + 1] = false
                    3 -> verticalWalls[ry][rx] = false
                }
            }
            knockDownWallPair(x, y, dir)
            attempts++
        }
    }

    /* -------------------------------------------------------------------- */
    /*                        Wall Rectangle Builder                         */
    /* -------------------------------------------------------------------- */

    private fun buildWallRects() {
        // Use scaled cell size for all calculations
        val scaledWallThickness = wallThickness * scale
        val halfThick = scaledWallThickness / 2f

        wallRects.clear() // Clear existing rects if any (e.g. if called multiple times)

        // horizontal walls
        for (row in 0..cellsY) {            // note: horizontalWalls size is cellsY+1
            for (col in 0 until cellsX) {
                if (horizontalWalls[row][col]) {
                    val yCenter = viewportOffsetY + row * cellSize
                    val xStart = viewportOffsetX + col * cellSize
                    val xEnd = viewportOffsetX + (col + 1) * cellSize
                    
                    val left = xStart - halfThick
                    val top = yCenter - halfThick
                    val right = xEnd + halfThick
                    val bottom = yCenter + halfThick
                    wallRects += RectF(left, top, right, bottom)
                }
            }
        }

        // vertical walls
        for (row in 0 until cellsY) {
            for (col in 0..cellsX) {        // verticalWalls size is cellsX+1
                if (verticalWalls[row][col]) {
                    val xCenter = viewportOffsetX + col * cellSize
                    val yStart = viewportOffsetY + row * cellSize
                    val yEnd = viewportOffsetY + (row + 1) * cellSize

                    val left = xCenter - halfThick
                    val top = yStart - halfThick
                    val right = xCenter + halfThick
                    val bottom = yEnd + halfThick
                    wallRects += RectF(left, top, right, bottom)
                }
            }
        }
    }

    /* -------------------------------------------------------------------- */
    /*                             Level API                                 */
    /* -------------------------------------------------------------------- */

    override fun update(): Boolean {
        // no dynamic behaviour yet
        return false
    }

    override fun draw(canvas: Canvas) {
        // Log.d(TAG, "MazeLevel.draw() called. Number of wallRects: ${wallRects.size}")
        if (wallRects.isNotEmpty()) {
            // Log.d(TAG, "MazeLevel.draw() - First wall rect: ${wallRects.first()}")
        }
        wallRects.forEach { canvas.drawRect(it, paint) }
        // Log.d(TAG, "MazeLevel.draw() - Finished drawing walls.")
    }

    /* Helper for collision queries */
    fun getWalls(): List<RectF> = wallRects

    /** 
     * Returns per‑color coverage; Maze itself has no paint so we delegate later.
     * For now this satisfies the Level interface.
     */
    override fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float> {
        // Delegate to CoverageCalculator with default sampling step
        return CoverageCalculator.calculate(this, paintSurface, sampleStep = (cellSize / 4).toInt().coerceAtLeast(1))
    }

    /**
     * Convert screen coordinates to maze-relative coordinates (normalized 0-1 range)
     * This ensures coordinates can be properly synchronized across different devices
     */
    fun screenToMazeCoord(x: Float, y: Float): Pair<Float, Float> {
        // Subtract viewport offset before normalizing
        val relX = (x - viewportOffsetX) / mazeWidth
        val relY = (y - viewportOffsetY) / mazeHeight
        return Pair(relX, relY)
    }

    /**
     * Convert maze-relative coordinates (normalized 0-1 range) to screen coordinates
     * This allows consistent positioning across different screen sizes and orientations
     */
    fun mazeToScreenCoord(relX: Float, relY: Float): Pair<Float, Float> {
        // Add viewport offset after denormalizing
        val screenX = viewportOffsetX + (relX * mazeWidth)
        val screenY = viewportOffsetY + (relY * mazeHeight)
        return Pair(screenX, screenY)
    }

    /**
     * Simple point‑vs‑wall test used by GameView/Player.
     * Returns true if (x,y) intersects any wall rectangle.
     */
    override fun checkCollision(x: Float, y: Float): Boolean {
        for (rect in wallRects) {
            if (rect.contains(x, y)) return true
        }
        return false
    }

    /**
     * Returns the starting (x,y) in screen pixels for each player.
     * Player 0 starts at the entrance (NW), player 1 at the exit (SE).
     * Additional players are placed at different corners.
     */
    override fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float> {
        // Use different corners for up to 4 players
        val position = when (playerIndex % 4) {
            0 -> ENTRANCE // Top-left
            1 -> EXIT     // Bottom-right
            2 -> Pair(cellsX - 1, 0) // Top-right
            3 -> Pair(0, cellsY - 1) // Bottom-left
            else -> ENTRANCE // Fallback
        }
        
        val (cx, cy) = position
        // centre of the target cell
        val px = viewportOffsetX + cx * cellSize + cellSize / 2
        val py = viewportOffsetY + cy * cellSize + cellSize / 2
        
        Log.d("MazeLevel", "Player $playerIndex start position: ($px, $py) from cell ($cx, $cy)")
        return px to py
    }
    
    /**
     * Returns the viewport offset to ensure correct painting coordinates
     */
    fun getViewportOffset(): Pair<Float, Float> {
        return viewportOffsetX to viewportOffsetY
    }
    
    /**
     * Returns the scale factor being used
     */
    fun getScale(): Float {
        return scale
    }
}