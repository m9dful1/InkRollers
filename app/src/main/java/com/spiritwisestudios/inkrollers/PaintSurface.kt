package com.spiritwisestudios.inkrollers
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log

/**
 * Manages an off-screen bitmap surface for paint operations in the game.
 * This surface stores all painted pixels and provides methods for drawing, querying,
 * and clearing paint. Used by Player objects to paint and by coverage/zone calculators
 * to analyze painted areas.
 */
class PaintSurface(val w:Int,val h:Int, existingBitmap: Bitmap? = null){
  private val bmp=existingBitmap ?: Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
  private val cvs=Canvas(bmp)
  private val paint=Paint().apply{isAntiAlias=true}
  private val clearPaint = Paint().apply{
    isAntiAlias = true
    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
  }
  
  /** Paints a circle at the specified coordinates with the given color. Used by Player movement. */
  fun paintAt(x:Float,y:Float,color:Int){ paint.color=color; cvs.drawCircle(x,y,20f,paint) }
  
  /** Returns the color of the pixel at the given coordinates. Used for ink refill detection. */
  fun getPixelColor(x:Int,y:Int):Int = bmp.getPixel(x,y)
  
  /** Erases paint at the specified coordinates (currently unused). */
  fun eraseAt(x:Float,y:Float){ cvs.drawCircle(x,y,20f,clearPaint) }
  
  /** Renders the paint surface onto the provided canvas. Called by GameView during draw cycle. */
  fun drawTo(c:Canvas){ 
    c.drawBitmap(bmp,0f,0f,null) 
  }
  
  /** Clears all paint from the surface. Used when starting new matches. */
  fun clear() { cvs.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR) }
  
  /** Returns a defensive copy of the bitmap for persistence across surface recreation. */
  fun getBitmapCopy(): Bitmap = bmp.copy(bmp.config, true)
  
  /** Returns direct access to the bitmap for performance-critical operations like zone calculation. */
  fun getBitmap(): Bitmap = bmp
}
