package com.spiritwisestudios.inkrollers
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log

class PaintSurface(val w:Int,val h:Int){
  private val bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
  private val cvs=Canvas(bmp)
  private val paint=Paint().apply{isAntiAlias=true}
  private val clearPaint = Paint().apply{
    isAntiAlias = true
    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
  }
  fun paintAt(x:Float,y:Float,color:Int){ paint.color=color; cvs.drawCircle(x,y,20f,paint) }
  fun getPixelColor(x:Int,y:Int):Int = bmp.getPixel(x,y)
  fun eraseAt(x:Float,y:Float){ cvs.drawCircle(x,y,20f,clearPaint) }
  fun drawTo(c:Canvas){ 
    Log.d("PaintSurface", "drawTo() called. Bitmap w: ${bmp.width}, h: ${bmp.height}")
    c.drawBitmap(bmp,0f,0f,null) 
  }
  /** Clear the entire paint surface. */
  fun clear() { cvs.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR) }
}
