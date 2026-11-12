package com.example.counterflame

import android.graphics.*
import kotlin.random.Random

enum class PatternType { SOLID, GRADIENT, MARBLE, STRIPES, BLUE_GOLD, COSMIC, RUST }

object Patterns {

    fun shader(w: Int, h: Int, skin: Skin): Shader {
        return when (skin.pattern) {
            PatternType.SOLID -> LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                skin.primary, skin.primary, Shader.TileMode.CLAMP
            )
            PatternType.GRADIENT -> LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(skin.primary, skin.secondary, skin.accent),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            PatternType.MARBLE -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = skin.primary
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                val rnd = Random(skin.id.hashCode())
                for (i in 0 until 40) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val r = 8f + rnd.nextFloat() * 35f
                    p.color = if (i % 3 == 0) skin.accent else skin.secondary
                    p.alpha = 110 + rnd.nextInt(90)
                    c.drawCircle(x, y, r, p)
                }
                p.alpha = 255
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            PatternType.STRIPES -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = skin.primary
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.color = skin.secondary
                var x = -h.toFloat()
                while (x < w + h) {
                    val path = Path()
                    path.moveTo(x, 0f)
                    path.lineTo(x + h * 0.4f, 0f)
                    path.lineTo(x + h * 0.4f + h, h.toFloat())
                    path.lineTo(x + h, h.toFloat())
                    path.close()
                    c.drawPath(path, p)
                    x += h * 0.9f
                }
                p.color = skin.accent; p.alpha = 60
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.alpha = 255
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            PatternType.BLUE_GOLD -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    skin.primary, skin.secondary, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null
                val rnd = Random(skin.id.hashCode())
                for (i in 0 until 25) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val r = 12f + rnd.nextFloat() * 40f
                    p.color = skin.accent; p.alpha = 120 + rnd.nextInt(100)
                    c.drawCircle(x, y, r, p)
                }
                p.alpha = 255
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            PatternType.COSMIC -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = RadialGradient(
                    w / 2f, h / 2f, maxOf(w, h) * 0.7f,
                    intArrayOf(skin.accent, skin.primary, skin.secondary),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = null; p.color = Color.WHITE
                val rnd = Random(skin.id.hashCode())
                for (i in 0 until 60) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val r = 0.5f + rnd.nextFloat() * 2.5f
                    p.alpha = 150 + rnd.nextInt(105)
                    c.drawCircle(x, y, r, p)
                }
                p.alpha = 255
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            PatternType.RUST -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = skin.primary
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                val rnd = Random(skin.id.hashCode())
                for (i in 0 until 120) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h
                    val r = 2f + rnd.nextFloat() * 12f
                    p.color = if (i % 2 == 0) skin.secondary else skin.accent
                    p.alpha = 80 + rnd.nextInt(120)
                    c.drawCircle(x, y, r, p)
                }
                p.alpha = 255
                BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }
    }
}
