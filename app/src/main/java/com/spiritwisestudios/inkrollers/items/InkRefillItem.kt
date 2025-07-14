package com.spiritwisestudios.inkrollers.items

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.spiritwisestudios.inkrollers.Player
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ink refill item that restores player's ink when collected
 */
class InkRefillItem(
    x: Float,
    y: Float,
    private val refillAmount: Float = Player.MAX_INK * 0.5f, // Default to 50% refill
    private val playerManager: PlayerManager? = null
) : BaseItem(ItemType.INK_REFILL, x, y, 25f) {
    
    companion object {
        private const val TAG = "InkRefillItem"
        private const val DROPLET_COUNT = 8
        private const val DROPLET_SIZE = 4f
        private const val WAVE_SPEED = 2f
        private const val WAVE_AMPLITUDE = 8f
    }
    
    // Visual properties
    private val inkPaint = Paint().apply {
        color = Color.parseColor("#1E90FF") // Dodger Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#87CEEB") // Sky Blue
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }
    
    private val dropletPaint = Paint().apply {
        color = Color.parseColor("#0066CC") // Darker blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private var animationTime = 0f
    
    override fun onUpdate(deltaTime: Float) {
        animationTime += deltaTime
    }
    
    override fun onDraw(canvas: Canvas) {
        val centerX = position.first
        val centerY = position.second
        
        // Draw floating droplets around the main item
        for (i in 0 until DROPLET_COUNT) {
            val angle = (i * 360f / DROPLET_COUNT) + (animationTime * 45f) // Rotating droplets
            val distance = 35f + sin(animationTime * WAVE_SPEED + i) * WAVE_AMPLITUDE
            
            val dropletX = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * distance
            val dropletY = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * distance
            
            canvas.drawCircle(dropletX, dropletY, DROPLET_SIZE, dropletPaint)
        }
        
        // Draw main ink bottle/container
        drawInkBottle(canvas, centerX, centerY)
    }
    
    private fun drawInkBottle(canvas: Canvas, centerX: Float, centerY: Float) {
        val radius = 20f
        val pulseScale = getPulseScale()
        
        // Draw main body (bottle)
        canvas.drawCircle(centerX, centerY, radius * pulseScale, inkPaint)
        
        // Draw highlight on the bottle
        val highlightRadius = radius * 0.6f * pulseScale
        canvas.drawCircle(
            centerX - radius * 0.3f * pulseScale,
            centerY - radius * 0.3f * pulseScale,
            highlightRadius,
            highlightPaint
        )
        
        // Draw ink level indicator lines
        val lineWidth = radius * 1.2f * pulseScale
        val lineHeight = 2f
        
        for (i in 0 until 3) {
            val yOffset = (i - 1) * 8f * pulseScale
            canvas.drawRect(
                centerX - lineWidth / 2,
                centerY + yOffset - lineHeight / 2,
                centerX + lineWidth / 2,
                centerY + yOffset + lineHeight / 2,
                highlightPaint
            )
        }
        
        // Draw cap/nozzle
        val capWidth = radius * 0.4f * pulseScale
        val capHeight = radius * 0.6f * pulseScale
        canvas.drawRect(
            centerX - capWidth / 2,
            centerY - radius * pulseScale - capHeight,
            centerX + capWidth / 2,
            centerY - radius * pulseScale,
            inkPaint
        )
    }
    
    override fun onItemCollected(playerId: String): Boolean {
        Log.d(TAG, "Ink refill item collected by player: $playerId")
        
        // Get the player and refill their ink
        val player = playerManager?.getPlayer(playerId)
        if (player != null) {
            val oldInk = player.ink
            player.ink = (player.ink + refillAmount).coerceAtMost(Player.MAX_INK)
            val actualRefill = player.ink - oldInk
            
            Log.d(TAG, "Player $playerId ink refilled by $actualRefill (from $oldInk to ${player.ink})")
            
            // Could trigger sound effect or visual feedback here
            return true
        } else {
            Log.w(TAG, "Could not find player $playerId to refill ink")
            return false
        }
    }
    
    override fun onItemUsed(playerId: String): Boolean {
        // Ink refill items are consumed on collection, not used separately
        return onItemCollected(playerId)
    }
    
    override fun getEffectDescription(): String {
        val refillPercent = ((refillAmount / Player.MAX_INK) * 100).toInt()
        return "Restores $refillPercent% ink"
    }
    
    /**
     * Interface for managing players - allows decoupling from specific game classes
     */
    interface PlayerManager {
        fun getPlayer(playerId: String): Player?
        fun getAllPlayers(): List<Player>
    }
} 