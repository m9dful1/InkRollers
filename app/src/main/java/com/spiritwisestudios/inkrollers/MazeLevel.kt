package com.spiritwisestudios.inkrollers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random
import java.util.Stack
import android.util.Log

/**
 * Perfect maze level implementation using depth-first search generation with 180-degree rotational symmetry.
 * 
 * Generates a rectangular grid maze where exactly one path exists between any two cells, then applies
 * selective braiding to create multiple paths while maintaining symmetry. Handles coordinate conversion
 * between screen space and normalized coordinates for multiplayer synchronization. Provides collision
 * detection, zone definitions for game modes, and player spawn positioning.
 */
class MazeLevel(
    private val screenW: Int,
    private val screenH: Int,
    private val _fixedCellsX_default: Int = 12,
    private val _fixedCellsY_default: Int = 20,
    private val wallThickness: Float = 12f,
    private val seed: Long = System.currentTimeMillis(), // Use provided seed or current time as fallback
    private val complexity: String = HomeActivity.COMPLEXITY_HIGH,

    /**
     * Top margin (in pixels) reserved for overlay UI such as the coverage HUD.
     * The maze will be scaled and positioned within the remaining space.
     */
    private val topMargin: Int = 0 // Pixels to offset the maze from the top (e.g. HUD height)
  
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
    private var cellSize: Float = 0f
    private var viewportOffsetX: Float = 0f
    private var viewportOffsetY: Float = 0f
    private var mazeWidth: Float = 0f
    private var mazeHeight: Float = 0f
    private var scale: Float = 1f

    companion object {
        private const val TAG = "MazeLevel"
    }

    init {
        // Log orientation and cell dimensions
        val orientationStr = if (screenW > screenH) "landscape" else "portrait"
        Log.d(TAG, "Creating maze in $orientationStr orientation with cells: ${this.cellsX}x${this.cellsY} for screen ${screenW}x${screenH}, complexity: $complexity")
        
        calculateScaling()
        generateGrids()
        buildWallRects()
    }

    /** Calculates scaling and positioning to center the maze within available screen space. */
    private fun calculateScaling() {
        val safetyMargin = 0.9f
        val availableHeight = (screenH - topMargin).toFloat()
        val cellW = (screenW.toFloat() * safetyMargin) / cellsX
        val cellH = (availableHeight * safetyMargin) / cellsY
        
        cellSize = minOf(cellW, cellH)
        mazeWidth = cellSize * cellsX
        mazeHeight = cellSize * cellsY
        viewportOffsetX = (screenW - mazeWidth) / 2
        viewportOffsetY = topMargin + (availableHeight - mazeHeight) / 2
        scale = minOf(maxOf(cellSize / 64f, 0.5f), 2.0f)
        
        Log.d("MazeLevel", "Scaling: cellSize=$cellSize, offset=($viewportOffsetX,$viewportOffsetY), scale=$scale, maze size=${mazeWidth}x${mazeHeight}")
    }

    /** Initializes wall grids and generates the complete maze structure. */
    private fun generateGrids() {
        horizontalWalls = Array(cellsY + 1) { BooleanArray(cellsX) { true } }
        verticalWalls   = Array(cellsY)     { BooleanArray(cellsX + 1) { true } }
        generateMaze()
        ensureMultiplePaths(minPaths = 3)
    }

    /**
     * Generates perfect maze using depth-first search with 180-degree rotational symmetry.
     * Every wall removal operation is mirrored to maintain symmetrical maze structure.
     */
    private fun generateMaze() {
        fun oppositeDir(d: Int): Int = when (d) { 0 -> 1; 1 -> 0; 2 -> 3; else -> 2 }

        fun knockDownWall(x: Int, y: Int, dir: Int) {
            when (dir) {
                0 -> horizontalWalls[y][x] = false
                1 -> horizontalWalls[y + 1][x] = false
                2 -> verticalWalls[y][x + 1] = false
                3 -> verticalWalls[y][x] = false
            }

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
            val nbrs = mutableListOf<Triple<Int, Int, Int>>()
            if (cy > 0 && !visited[cy - 1][cx]) nbrs += Triple(0, -1, 0)
            if (cy < cellsY - 1 && !visited[cy + 1][cx]) nbrs += Triple(0, 1, 1)
            if (cx < cellsX - 1 && !visited[cy][cx + 1]) nbrs += Triple(1, 0, 2)
            if (cx > 0 && !visited[cy][cx - 1]) nbrs += Triple(-1, 0, 3)

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
     * Creates multiple paths by selectively removing walls while maintaining symmetry.
     * Uses path counting and braiding to ensure at least the specified number of distinct paths exist.
     */
    private fun ensureMultiplePaths(minPaths: Int) {
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
                val dirs = listOf(
                    Triple(0, -1, 0),
                    Triple(0, 1, 1),
                    Triple(1, 0, 2),
                    Triple(-1, 0, 3)
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

        fun isRemovableWall(x: Int, y: Int, dir: Int): Boolean {
            return when (dir) {
                0 -> y > 0 && horizontalWalls[y][x]
                1 -> y < cellsY - 1 && horizontalWalls[y + 1][x]
                2 -> x < cellsX - 1 && verticalWalls[y][x + 1]
                3 -> x > 0 && verticalWalls[y][x]
                else -> false
            }
        }

        val rnd = Random(seed + 42)
        val candidateWalls = mutableListOf<Triple<Int, Int, Int>>()
        for (y in 0 until cellsY) {
            for (x in 0 until cellsX) {
                for (dir in 0..3) {
                    if (isRemovableWall(x, y, dir)) {
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

    /** Converts wall grid data into screen-space rectangles for collision detection and rendering. */
    private fun buildWallRects() {
        val scaledWallThickness = wallThickness * scale
        val halfThick = scaledWallThickness / 2f

        wallRects.clear()

        for (row in 0..cellsY) {
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

        for (row in 0 until cellsY) {
            for (col in 0..cellsX) {
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

    override fun update(): Boolean = false

    override fun draw(canvas: Canvas) {
        wallRects.forEach { canvas.drawRect(it, paint) }
    }

    /** Returns all wall rectangles for advanced collision queries. */
    fun getWalls(): List<RectF> = wallRects

    /** Delegates coverage calculation to CoverageCalculator with optimized sampling. */
    override fun calculateCoverage(paintSurface: PaintSurface): Map<Int, Float> {
        return CoverageCalculator.calculate(this, paintSurface, sampleStep = (cellSize / 4).toInt().coerceAtLeast(1))
    }

    /** Converts screen coordinates to normalized maze coordinates for multiplayer synchronization. */
    fun screenToMazeCoord(x: Float, y: Float): Pair<Float, Float> {
        val relX = (x - viewportOffsetX) / mazeWidth
        val relY = (y - viewportOffsetY) / mazeHeight
        return Pair(relX, relY)
    }

    /** Converts normalized maze coordinates to screen coordinates for rendering and input. */
    fun mazeToScreenCoord(relX: Float, relY: Float): Pair<Float, Float> {
        val screenX = viewportOffsetX + (relX * mazeWidth)
        val screenY = viewportOffsetY + (relY * mazeHeight)
        return Pair(screenX, screenY)
    }

    /** Returns true if the specified point intersects any wall rectangle. */
    override fun checkCollision(x: Float, y: Float): Boolean {
        for (rect in wallRects) {
            if (rect.contains(x, y)) return true
        }
        return false
    }

    /** Returns starting screen coordinates for players at maze corners. */
    override fun getPlayerStartPosition(playerIndex: Int): Pair<Float, Float> {
        val position = when (playerIndex % 4) {
            0 -> ENTRANCE
            1 -> EXIT
            2 -> Pair(cellsX - 1, 0)
            3 -> Pair(0, cellsY - 1)
            else -> ENTRANCE
        }
        
        val (cx, cy) = position
        val px = viewportOffsetX + cx * cellSize + cellSize / 2
        val py = viewportOffsetY + cy * cellSize + cellSize / 2
        
        Log.d("MazeLevel", "Player $playerIndex start position: ($px, $py) from cell ($cx, $cy)")
        return px to py
    }
    
    /** Returns viewport offset for coordinate system alignment. */
    fun getViewportOffset(): Pair<Float, Float> = viewportOffsetX to viewportOffsetY
    
    /** Returns current scale factor used for proportional rendering. */
    fun getScale(): Float = scale
    
    /** Returns normalized zone boundaries for Zones game mode in a 2x3 grid layout. */
    override fun getZones(): List<RectF> {
        val zones = mutableListOf<RectF>()
        val rows = 2
        val cols = 3
        val zoneWidth = 1.0f / cols
        val zoneHeight = 1.0f / rows
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val left = col * zoneWidth
                val top = row * zoneHeight
                val right = left + zoneWidth
                val bottom = top + zoneHeight
                
                zones.add(RectF(left, top, right, bottom))
            }
        }
        
        Log.d(TAG, "Generated ${zones.size} zones for maze level")
        return zones
    }
}