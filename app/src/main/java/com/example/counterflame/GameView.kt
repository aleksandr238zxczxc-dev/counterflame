package com.example.counterflame

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class GameView(ctx: Context) : View(ctx) {

    private val activity = ctx as android.app.Activity
    private val prefs = ctx.getSharedPreferences("cf", Context.MODE_PRIVATE)
    private val store = Store(activity, prefs)
    private val sound = SoundManager()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 44f; typeface = Typeface.DEFAULT_BOLD
    }
    private val skyPaint = Paint()
    private val floorPaint = Paint()
    private var lastTime = 0L

    // Сеть
    private val SERVER_URL = "wss://counterflame.onrender.com"
    private var net: NetworkClient? = null
    private var connected = false
    private var myId = ""
    private var remotePlayers: List<RemotePlayer> = emptyList()
    private var remoteEnemies: List<RemoteEnemy> = emptyList()
    private var netMoveAccum = 0f

    // Карта
    private val map = arrayOf(
        "1111111111111111",
        "1..............1",
        "1..22....22....1",
        "1..2......2....1",
        "1..............1",
        "1....3333......1",
        "1....3..3......1",
        "1....3..3......1",
        "1....3333......1",
        "1..............1",
        "1..2......2....1",
        "1..22....22....1",
        "1..............1",
        "1..............1",
        "1..............1",
        "1111111111111111"
    )
    private val mw = map[0].length
    private val mh = map.size

    private fun wallAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= mw || y >= mh) return 1
        val c = map[y][x]
        return if (c == '.') 0 else (c - '0')
    }

    // Игрок
    private var px = 2.5f
    private var py = 2.5f
    private var pa = 0.4f
    private var health = 100
    private var score = 0
    private var ammoMag = Weapons.byId(prefs.getString("weapon", "rifle") ?: "rifle").magSize
    private var reloading = 0f
    private var muzzle = 0f
    private var hurt = 0f
    private var shootCd = 0f
    private var currentSkin = Skins.byId(prefs.getString("selected", "default") ?: "default")

    // Оружие
    private var currentWeapon = Weapons.byId(prefs.getString("weapon", "rifle") ?: "rifle")
    private val ownedWeapons: MutableSet<String> = mutableSetOf("pistol", "smg", "rifle", "rifle2")
    private var useWeapon = true

    // Ножи
    private val ownedKnives: MutableSet<String> = mutableSetOf("flip")
    private var currentKnife: KnifeDef? = null
    private var swing = 0f

    // Кэш паттернов
    private val patternCache = HashMap<String, Shader>()

    // FOV
    private val fov = Math.toRadians(70.0).toFloat()
    private val halfFov = fov / 2f
    private val tanHalf = tan(halfFov)
    private val moveSpeed = 3.2f

    // Локальные враги
    private class Enemy(var x: Float, var y: Float) {
        var hp = 100f; var alive = true
        var cd = 1.5f + Random.nextFloat() * 1.5f
        var respawn = 0f
    }
    private val enemies = ArrayList<Enemy>()

    // Экран
    private var w = 1; private var h = 1
    private var focal = 1f
    private var zbuf = FloatArray(1)
    private val info = IntArray(2)

    // Ввод
    private var joyId = -1
    private var joyBx = 0f; private var joyBy = 0f
    private var joyDx = 0f; private var joyDy = 0f
    private val joyR = 120f
    private var lookId = -1
    private var lookPrevX = 0f
    private var fireId = -1
    private var fireCx = 0f; private var fireCy = 0f
    private val fireR = 95f
    private var firing = false

    // Кнопки меню
    private var menuY = 100f
    private var menuR = 55f
    private var menuXs = floatArrayOf(80f, 210f, 340f, 470f)

    // Магазин
    private var storeOpen = false
    private val storeItems = ArrayList<RectF>()
    private var closeStoreRect = RectF()

    // Оружие
    private var weaponsOpen = false
    private val weaponItems = ArrayList<RectF>()
    private var closeWeaponsRect = RectF()

    // Ножи
    private var knivesOpen = false
    private val knifeItems = ArrayList<RectF>()
    private var closeKnivesRect = RectF()
    private var backToWeaponRect = RectF()

    // Трейд
    private var tradeOpen = false
    private var pendingOffer: Triple<String, String, String>? = null
    private var closeTradeRect = RectF()
    private var acceptTradeRect = RectF()
    private var declineTradeRect = RectF()
    private var tradeOfferStatus = ""
    private var tradeOfferTimer = 0f

    init {
        val savedKnife = prefs.getString("knife", null)
        if (savedKnife != null && savedKnife in ownedKnives) {
            currentKnife = Knives.byId(savedKnife)
            useWeapon = false
        }
        prefs.edit().putString("weapons", ownedWeapons.joinToString(",")).apply()
        prefs.edit().putString("knives", ownedKnives.joinToString(",")).apply()

        repeat(5) {
            val e = Enemy(8f, 8f); placeEnemy(e); enemies.add(e)
        }
        store.initBilling { }
        connect()
    }

    private fun connect() {
        net = NetworkClient(SERVER_URL, object : NetworkClient.Listener {
            override fun onConnected(myId: String) {
                this@GameView.myId = myId
                connected = true
                net?.sendSkin(currentSkin.id)
            }
            override fun onState(players: List<RemotePlayer>, enemies: List<RemoteEnemy>) {
                remotePlayers = players
                remoteEnemies = enemies
                players.firstOrNull { it.id == myId }?.let {
                    health = it.hp; score = it.score
                }
            }
            override fun onDisconnected() { connected = false }
            override fun onTradeIncoming(offerId: String, fromId: String, give: String, want: String) {
                pendingOffer = Triple(offerId, give, want)
                sound.tradeOffer()
            }
            override fun onTradeResult(offerId: String, ok: Boolean) {
                tradeOfferStatus = if (ok) "Трейд принят" else "Трейд отклонён"
                tradeOfferTimer = 3f
            }
        }).also { it.connect() }
    }

    private fun placeEnemy(e: Enemy) {
        var tries = 0
        while (tries++ < 300) {
            val x = 1 + Random.nextFloat() * (mw - 2)
            val y = 1 + Random.nextFloat() * (mh - 2)
            if (wallAt(x.toInt(), y.toInt()) != 0) continue
            if (hypot(x - px, y - py) < 4.5f) continue
            e.x = x; e.y = y; return
        }
        e.x = 8f; e.y = 8f
    }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw; h = nh
        focal = (w / 2f) / tanHalf
        zbuf = FloatArray(w)
        fireCx = w - 160f; fireCy = h - 150f
        menuY = 90f
        menuR = 55f
        menuXs = floatArrayOf(w * 0.06f, w * 0.14f, w * 0.22f, w * 0.30f)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h / 2f,
            Color.rgb(18, 22, 38), Color.rgb(78, 96, 138), Shader.TileMode.CLAMP)
        floorPaint.shader = LinearGradient(
            0f, h / 2f, 0f, h.toFloat(),
            Color.rgb(46, 46, 52), Color.rgb(10, 10, 12), Shader.TileMode.CLAMP)
    }

    private fun castRay(ox: Float, oy: Float, dx: Float, dy: Float, maxD: Float): Float {
        var mapX = ox.toInt(); var mapY = oy.toInt()
        val ddx = if (dx == 0f) 1e30f else abs(1f / dx)
        val ddy = if (dy == 0f) 1e30f else abs(1f / dy)
        val stepX: Int; val stepY: Int
        var sdx: Float; var sdy: Float
        if (dx < 0) { stepX = -1; sdx = (ox - mapX) * ddx }
        else { stepX = 1; sdx = (mapX + 1f - ox) * ddx }
        if (dy < 0) { stepY = -1; sdy = (oy - mapY) * ddy }
        else { stepY = 1; sdy = (mapY + 1f - oy) * ddy }
        var side = 0; var t = 0f; var guard = 0
        while (guard++ < 512) {
            if (sdx < sdy) { t = sdx; sdx += ddx; mapX += stepX; side = 0 }
            else { t = sdy; sdy += ddy; mapY += stepY; side = 1 }
            if (t > maxD) break
            if (wallAt(mapX, mapY) != 0) {
                info[0] = side; info[1] = wallAt(mapX, mapY); return t
            }
        }
        info[0] = -1; info[1] = 1; return maxD
    }

    private fun lineOfSight(tx: Float, ty: Float): Boolean {
        val dx = tx - px; val dy = ty - py
        val d = hypot(dx, dy); if (d < 0.001f) return true
        return castRay(px, py, dx / d, dy / d, 40f) >= d - 0.15f
    }

    private fun shoot() {
        // Нож
        if (!useWeapon && currentKnife != null) {
            if (shootCd > 0f) return
            shootCd = currentKnife!!.fireRate
            swing = 1f
            sound.knifeSwing()
            var best: Enemy? = null; var bestD = 2.5f
            for (e in enemies) {
                if (!e.alive) continue
                val d = hypot(e.x - px, e.y - py)
                if (d < bestD && lineOfSight(e.x, e.y)) { bestD = d; best = e }
            }
            best?.let {
                it.hp -= currentKnife!!.damage
                sound.knifeHit()
                if (it.hp <= 0f) { it.alive = false; it.respawn = 2.5f; score++ }
            }
            return
        }
        // Огнестрел
        if (shootCd > 0f || reloading > 0f) return
        if (ammoMag <= 0) { reloading = currentWeapon.reloadTime; return }
        ammoMag--; shootCd = currentWeapon.fireRate; muzzle = 1f
        sound.shoot()
        if (connected) { net?.sendShoot(); return }
        var best: Enemy? = null; var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - px; val dy = e.y - py
            val d = hypot(dx, dy); if (d > currentWeapon.range) continue
            var rel = atan2(dy, dx) - pa
            while (rel > Math.PI.toFloat()) rel -= (2 * Math.PI).toFloat()
            while (rel < -Math.PI.toFloat()) rel += (2 * Math.PI).toFloat()
            if (abs(rel) > 0.12f) continue
            if (d < bestD && lineOfSight(e.x, e.y)) { bestD = d; best = e }
        }
        best?.let {
            it.hp -= currentWeapon.damage
            sound.hit()
            if (it.hp <= 0f) { it.alive = false; it.respawn = 2.5f; score++ }
        }
    }

    private fun update(dt: Float) {
        if (reloading > 0f) {
            reloading -= dt
            if (reloading <= 0f) { reloading = 0f; ammoMag = currentWeapon.magSize }
        }
        muzzle = max(0f, muzzle - dt * 6f)
        hurt = max(0f, hurt - dt * 1.5f)
        shootCd = max(0f, shootCd - dt)
        swing = max(0f, swing - dt * 4f)
        if (tradeOfferTimer > 0f) tradeOfferTimer -= dt
        if (firing && fireId != -1) shoot()

        val fwd = -joyDy; val str = joyDx
        val sp = moveSpeed * dt
        val nx = px + (cos(pa) * fwd - sin(pa) * str) * sp
        val ny = py + (sin(pa) * fwd + cos(pa) * str) * sp
        if (wallAt(nx.toInt(), py.toInt()) == 0) px = nx
        if (wallAt(px.toInt(), ny.toInt()) == 0) py = ny

        if (connected) {
            netMoveAccum += dt
            if (netMoveAccum >= 0.05f) {
                netMoveAccum = 0f
                net?.sendMove(px, py, pa)
            }
        } else {
            updateLocalEnemies(dt)
        }
    }

    private fun updateLocalEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) {
                e.respawn -= dt
                if (e.respawn <= 0f) { placeEnemy(e); e.alive = true; e.hp = 100f; e.cd = 1.5f }
                continue
            }
            val ex = px - e.x; val ey = py - e.y
            val d = hypot(ex, ey).coerceAtLeast(0.001f)
            if (d > 1.5f) {
                val s = 1.5f * dt
                val mx = e.x + ex / d * s; val my = e.y + ey / d * s
                if (wallAt(mx.toInt(), e.y.toInt()) == 0) e.x = mx
                if (wallAt(e.x.toInt(), my.toInt()) == 0) e.y = my
            }
            e.cd -= dt
            if (e.cd <= 0f && d < 9f) {
                e.cd = 1.2f + Random.nextFloat() * 1.6f
                if (lineOfSight(e.x, e.y)) {
                    health -= 7; hurt = 1f; sound.hurt()
                    if (health <= 0) {
                        health = 100; score = max(0, score - 2)
                        px = 2.5f; py = 2.5f; sound.die()
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        var dt = if (lastTime == 0L) 0f else (now - lastTime) / 1_000_000_000f
        lastTime = now
        dt = dt.coerceAtMost(0.05f)
        update(dt)
        render(canvas)
        invalidate()
    }

    private fun render(c: Canvas) {
        c.drawRect(0f, 0f, w.toFloat(), h / 2f, skyPaint)
        c.drawRect(0f, h / 2f, w.toFloat(), h.toFloat(), floorPaint)

        val dirX = cos(pa); val dirY = sin(pa)
        val planeX = -dirY * tanHalf; val planeY = dirX * tanHalf
        for (x in 0 until w) {
            val camX = 2f * x / w - 1f
            val rx = dirX + planeX * camX; val ry = dirY + planeY * camX
            var t = castRay(px, py, rx, ry, 40f)
            if (t < 0.05f) t = 0.05f
            zbuf[x] = t
            val lineH = focal / t
            val top = h / 2f - lineH / 2f
            val bot = h / 2f + lineH / 2f
            val base = when (info[1]) {
                2 -> Color.rgb(126, 74, 52)
                3 -> Color.rgb(72, 104, 72)
                else -> Color.rgb(92, 92, 104)
            }
            val shade = if (info[0] == 1) 0.72f else 1f
            val fog = (1f - t / 22f).coerceIn(0.12f, 1f)
            paint.color = Color.rgb(
                (Color.red(base) * shade * fog).toInt().coerceIn(0, 255),
                (Color.green(base) * shade * fog).toInt().coerceIn(0, 255),
                (Color.blue(base) * shade * fog).toInt().coerceIn(0, 255)
            )
            c.drawRect(x.toFloat(), top, x + 1f, bot, paint)
        }

        for (rp in remotePlayers) {
            if (rp.id == myId) continue
            val sk = Skins.byId(rp.skin)
            drawSprite(c, rp.x, rp.y, sk.primary, sk.secondary)
        }
        for (re in remoteEnemies) {
            drawSprite(c, re.x, re.y, 0xFFBC2E2E.toInt(), 0xFFE2BE9E.toInt())
        }
        if (!connected) for (e in enemies) {
            if (!e.alive) continue
            drawSprite(c, e.x, e.y, currentSkin.primary, currentSkin.secondary)
        }

        drawWeaponInHands(c)

        if (muzzle > 0f) {
            paint.color = Color.argb((muzzle * 200).toInt(), 255, 220, 120)
            c.drawCircle(w * 0.72f, h * 0.92f, 60f * muzzle, paint)
        }

        paint.color = Color.WHITE; paint.strokeWidth = 4f
        c.drawLine(w / 2f - 28, h / 2f, w / 2f - 9, h / 2f, paint)
        c.drawLine(w / 2f + 9, h / 2f, w / 2f + 28, h / 2f, paint)
        c.drawLine(w / 2f, h / 2f - 28, w / 2f, h / 2f - 9, paint)
        c.drawLine(w / 2f, h / 2f + 9, w / 2f, h / 2f + 28, paint)

        if (joyId != -1) {
            paint.color = Color.argb(90, 255, 255, 255)
            c.drawCircle(joyBx, joyBy, joyR, paint)
            paint.color = Color.argb(190, 255, 255, 255)
            c.drawCircle(joyBx + joyDx * joyR, joyBy + joyDy * joyR, joyR * 0.38f, paint)
        }

        paint.color = Color.argb(if (firing) 210 else 120, 220, 40, 40)
        c.drawCircle(fireCx, fireCy, fireR, paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 34f
        c.drawText("FIRE", fireCx, fireCy + 12f, text)

        // Кнопки меню
        drawMenuButton(c, 0, "SHOP", Color.rgb(40, 140, 220))
        drawMenuButton(c, 1, "GUNS", Color.rgb(40, 180, 100))
        drawMenuButton(c, 2, "KNIFE", Color.rgb(200, 80, 200))
        drawMenuButton(c, 3, "TRADE", Color.rgb(240, 200, 40))

        // HUD
        text.textAlign = Paint.Align.LEFT; text.textSize = 44f; text.color = Color.WHITE
        c.drawText("HP $health", 40f, h - 40f, text)
        text.textAlign = Paint.Align.RIGHT
        val ammoLabel = if (!useWeapon) "MELEE" else if (reloading > 0f) "RELOAD…" else "$ammoMag / ${currentWeapon.magSize}"
        c.drawText(ammoLabel, w - 40f, h - 40f, text)
        text.textAlign = Paint.Align.CENTER
        c.drawText("SCORE $score", w / 2f, 70f, text)
        text.textSize = 28f
        text.color = if (connected) Color.rgb(100, 220, 100) else Color.rgb(220, 100, 100)
        c.drawText(if (connected) "ONLINE (${remotePlayers.size})" else "OFFLINE", w / 2f, 110f, text)
        text.color = Color.WHITE

        if (hurt > 0f) {
            paint.color = Color.argb((hurt * 110).toInt(), 200, 0, 0)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }

        if (tradeOfferTimer > 0f) {
            text.textSize = 32f; text.color = Color.rgb(100, 220, 100)
            c.drawText(tradeOfferStatus, w / 2f, h - 200f, text)
            text.color = Color.WHITE
        }

        if (storeOpen) renderStore(c)
        if (weaponsOpen) renderWeaponsMenu(c)
        if (knivesOpen) renderKnivesMenu(c)
        if (tradeOpen) renderTradeMenu(c)
    }

    private fun drawMenuButton(c: Canvas, idx: Int, label: String, color: Int) {
        paint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
        c.drawCircle(menuXs[idx], menuY, menuR, paint)
        text.textAlign = Paint.Align.CENTER
        text.textSize = 22f; text.color = Color.WHITE
        c.drawText(label, menuXs[idx], menuY + 8f, text)
    }

    private fun drawSprite(c: Canvas, ex: Float, ey: Float, bodyColor: Int, headColor: Int) {
        val dx = ex - px; val dy = ey - py
        val d = hypot(dx, dy); if (d < 0.25f) return
        var rel = atan2(dy, dx) - pa
        while (rel > Math.PI.toFloat()) rel -= (2 * Math.PI).toFloat()
        while (rel < -Math.PI.toFloat()) rel += (2 * Math.PI).toFloat()
        if (abs(rel) > halfFov + 0.6f) return
        val sx = w / 2f + tan(rel) * focal
        val col = sx.toInt().coerceIn(0, w - 1)
        if (d > zbuf[col] + 0.25f) return
        val feet = h / 2f + focal / (2f * d)
        val sh = focal * 0.95f / d
        val sw = sh * 0.42f
        val top = feet - sh
        paint.color = bodyColor
        rect.set(sx - sw / 2f, top + sh * 0.28f, sx + sw / 2f, feet)
        c.drawRoundRect(rect, sw * 0.2f, sw * 0.2f, paint)
        paint.color = headColor
        c.drawCircle(sx, top + sh * 0.15f, sw * 0.30f, paint)
    }

    private fun drawWeaponInHands(c: Canvas) {
        val cx = w * 0.72f
        val cy = h * 0.90f
        val bob = sin(System.nanoTime() / 100_000_000.0).toFloat() * 6f *
                  hypot(joyDx.toDouble(), joyDy.toDouble()).toFloat()
        val y = cy + bob

        val shader = patternCache.getOrPut(currentSkin.id) {
            Patterns.shader(180, 60, currentSkin)
        }

        if (!useWeapon && currentKnife != null) {
            drawKnife(c, cx, y, currentKnife!!, shader, swing)
            return
        }

        paint.shader = shader
        paint.color = currentSkin.primary
        when (currentWeapon.sprite) {
            0 -> {
                rect.set(cx - 30f, y - 40f, cx + 30f, y + 30f)
                c.drawRoundRect(rect, 8f, 8f, paint)
                paint.shader = null; paint.color = currentSkin.accent
                rect.set(cx - 12f, y - 70f, cx + 12f, y - 40f); c.drawRect(rect, paint)
            }
            1 -> {
                rect.set(cx - 50f, y - 30f, cx + 40f, y + 20f)
                c.drawRoundRect(rect, 6f, 6f, paint)
                paint.shader = null; paint.color = currentSkin.accent
                rect.set(cx - 20f, y + 15f, cx + 10f, y + 70f); c.drawRect(rect, paint)
                            }
            2, 3 -> {
                rect.set(cx - 90f, y - 25f, cx + 60f, y + 20f)
                c.drawRoundRect(rect, 5f, 5f, paint)
                paint.shader = null; paint.color = currentSkin.accent
                rect.set(cx - 10f, y + 15f, cx + 25f, y + 90f); c.drawRect(rect, paint)
                rect.set(cx + 55f, y - 8f, cx + 120f, y + 12f); c.drawRect(rect, paint)
            }
            4 -> {
                rect.set(cx - 120f, y - 22f, cx + 100f, y + 18f)
                c.drawRoundRect(rect, 5f, 5f, paint)
                paint.shader = null; paint.color = Color.rgb(40, 40, 40)
                rect.set(cx - 40f, y - 45f, cx + 20f, y - 20f); c.drawRect(rect, paint)
                paint.color = currentSkin.accent
                rect.set(cx + 90f, y - 10f, cx + 140f, y + 10f); c.drawRect(rect, paint)
            }
        }
        paint.shader = null
    }

    private fun drawKnife(c: Canvas, cx: Float, cy: Float, knife: KnifeDef, shader: Shader, swingAnim: Float) {
        val angle = -swingAnim * 55f
        val pivotX = cx - 20f; val pivotY = cy + 30f
        c.save()
        c.rotate(angle, pivotX, pivotY)

        val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 26, 24) }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        paint.shader = shader
        val blade = Path()
        when (knife.shape) {
            0 -> {
                blade.moveTo(cx - 95f, cy + 4f)
                blade.cubicTo(cx - 60f, cy - 30f, cx - 10f, cy - 26f, cx + 40f, cy - 12f)
                blade.quadTo(cx + 62f, cy - 6f, cx + 66f, cy)
                blade.cubicTo(cx + 48f, cy + 8f, cx + 20f, cy + 16f, cx - 30f, cy + 14f)
                blade.quadTo(cx - 70f, cy + 12f, cx - 95f, cy + 4f)
                blade.close()
                c.drawPath(blade, paint); c.drawPath(blade, edge)
                val h = Path().apply {
                    moveTo(cx - 155f, cy - 14f)
                    cubicTo(cx - 130f, cy - 22f, cx - 100f, cy - 18f, cx - 95f, cy - 6f)
                    lineTo(cx - 95f, cy + 12f)
                    cubicTo(cx - 110f, cy + 22f, cx - 140f, cy + 18f, cx - 155f, cy + 14f)
                    close()
                }
                c.drawPath(h, handle)
            }
            1 -> {
                blade.moveTo(cx - 55f, cy + 4f)
                blade.cubicTo(cx + 5f, cy - 35f, cx + 70f, cy - 30f, cx + 108f, cy - 12f)
                blade.lineTo(cx + 118f, cy - 2f)
                blade.cubicTo(cx + 90f, cy + 6f, cx + 40f, cy + 18f, cx - 10f, cy + 16f)
                blade.quadTo(cx - 45f, cy + 12f, cx - 55f, cy + 4f)
                blade.close()
                c.drawPath(blade, paint); c.drawPath(blade, edge)
                val h = Path().apply {
                    moveTo(cx - 150f, cy - 10f)
                    cubicTo(cx - 120f, cy - 18f, cx - 80f, cy - 16f, cx - 55f, cy - 4f)
                    lineTo(cx - 55f, cy + 10f)
                    cubicTo(cx - 85f, cy + 20f, cx - 125f, cy + 16f, cx - 150f, cy + 10f)
                    close()
                }
                c.drawPath(h, handle)
            }
            2 -> {
                blade.moveTo(cx - 40f, cy + 2f)
                blade.cubicTo(cx + 10f, cy - 80f, cx + 90f, cy - 60f, cx + 120f, cy - 18f)
                blade.quadTo(cx + 122f, cy - 8f, cx + 112f, cy - 4f)
                blade.cubicTo(cx + 70f, cy - 2f, cx + 40f, cy + 12f, cx + 10f, cy + 20f)
                blade.lineTo(cx - 40f, cy + 16f)
                blade.close()
                c.drawPath(blade, paint); c.drawPath(blade, edge)
                val h = Path().apply {
                    moveTo(cx - 95f, cy - 8f)
                    cubicTo(cx - 70f, cy - 22f, cx - 40f, cy - 18f, cx - 40f, cy + 2f)
                    lineTo(cx - 40f, cy + 16f)
                    cubicTo(cx - 60f, cy + 24f, cx - 85f, cy + 18f, cx - 95f, cy + 8f)
                    close()
                }
                c.drawPath(h, handle)
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = currentSkin.accent; style = Paint.Style.STROKE; strokeWidth = 6f
                }
                c.drawCircle(cx - 78f, cy + 5f, 15f, ring)
            }
            3 -> {
                blade.moveTo(cx - 100f, cy + 2f)
                blade.cubicTo(cx - 50f, cy - 30f, cx + 5f, cy - 28f, cx + 55f, cy - 10f)
                blade.quadTo(cx + 70f, cy - 4f, cx + 72f, cy)
                blade.cubicTo(cx + 40f, cy + 10f, cx + 5f, cy + 18f, cx - 40f, cy + 16f)
                blade.quadTo(cx - 80f, cy + 14f, cx - 100f, cy + 2f)
                blade.close()
                c.drawPath(blade, paint); c.drawPath(blade, edge)
                val h1 = Path().apply {
                    moveTo(cx - 180f, cy - 16f)
                    cubicTo(cx - 155f, cy - 22f, cx - 115f, cy - 20f, cx - 100f, cy - 8f)
                    lineTo(cx - 100f, cy - 2f)
                    lineTo(cx - 180f, cy - 6f)
                    close()
                }
                c.drawPath(h1, handle)
                val h2 = Path().apply {
                    moveTo(cx - 180f, cy + 2f)
                    lineTo(cx - 100f, cy + 6f)
                    lineTo(cx - 100f, cy + 14f)
                    cubicTo(cx - 120f, cy + 24f, cx - 160f, cy + 22f, cx - 180f, cy + 16f)
                    close()
                }
                c.drawPath(h2, handle)
            }
            4 -> {
                blade.moveTo(cx - 50f, cy + 12f)
                blade.cubicTo(cx - 20f, cy - 60f, cx + 60f, cy - 70f, cx + 118f, cy - 42f)
                blade.quadTo(cx + 122f, cy - 34f, cx + 116f, cy - 28f)
                blade.cubicTo(cx + 80f, cy - 12f, cx + 40f, cy + 8f, cx + 5f, cy + 22f)
                blade.lineTo(cx - 50f, cy + 22f)
                blade.close()
                c.drawPath(blade, paint); c.drawPath(blade, edge)
                val h = Path().apply {
                    moveTo(cx - 105f, cy - 4f)
                    cubicTo(cx - 78f, cy - 18f, cx - 55f, cy - 12f, cx - 50f, cy + 6f)
                    lineTo(cx - 50f, cy + 20f)
                    cubicTo(cx - 70f, cy + 28f, cx - 95f, cy + 24f, cx - 105f, cy + 12f)
                    close()
                }
                c.drawPath(h, handle)
            }
        }
        paint.shader = null
        c.restore()
    }

    private fun renderStore(c: Canvas) {
        paint.color = Color.argb(230, 10, 10, 20)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 48f; text.color = Color.WHITE
        c.drawText("МАГАЗИН СКИНОВ", w / 2f, 70f, text)

        storeItems.clear()
        val cols = 3
        val cardW = (w - 120f) / cols
        val cardH = 180f
        val startY = 110f
        for ((i, skin) in Skins.all.withIndex()) {
            val col = i % cols; val row = i / cols
            val l = 40f + col * cardW + 10f
            val t = startY + row * (cardH + 15f)
            val r = l + cardW - 20f
            val b = t + cardH
            val rr = RectF(l, t, r, b)
            storeItems.add(rr)
            paint.color = Color.argb(200, 30, 30, 46)
            c.drawRoundRect(rr, 20f, 20f, paint)
            paint.color = skin.primary
            c.drawCircle(l + 45f, t + 45f, 25f, paint)
            paint.color = skin.secondary
            c.drawCircle(l + 45f, t + 100f, 20f, paint)
            text.textAlign = Paint.Align.LEFT
            text.textSize = 26f; text.color = Color.WHITE
            c.drawText(skin.name, l + 85f, t + 45f, text)
            text.textSize = 18f; text.color = Color.GRAY
            c.drawText(skin.description, l + 85f, t + 72f, text)
            val owned = store.isOwned(skin.id)
            val selected = store.selectedSkin() == skin.id
            text.textSize = 22f
            text.color = when {
                selected -> Color.rgb(100, 220, 100)
                owned -> Color.rgb(80, 160, 240)
                else -> Color.rgb(255, 200, 60)
            }
            c.drawText(
                when {
                    selected -> "✓ ВЫБРАН"
                    owned -> "ВЫБРАТЬ"
                    skin.priceRub == 0 -> "БЕСПЛАТНО"
                    else -> "${skin.priceRub} ₽"
                },
                l + 85f, t + 130f, text
            )
        }
        closeStoreRect = RectF(w / 2f - 120f, h - 110f, w / 2f + 120f, h - 40f)
        paint.color = Color.rgb(180, 40, 40)
        c.drawRoundRect(closeStoreRect, 30f, 30f, paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 30f; text.color = Color.WHITE
        c.drawText("ЗАКРЫТЬ", w / 2f, h - 68f, text)
    }

    private fun renderWeaponsMenu(c: Canvas) {
        paint.color = Color.argb(230, 10, 10, 20)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 48f; text.color = Color.WHITE
        c.drawText("ОРУЖИЕ", w / 2f, 70f, text)
        weaponItems.clear()
        val cols = 3
        val cardW = (w - 120f) / cols
        val cardH = 180f
        val startY = 110f
        for ((i, wp) in Weapons.all.withIndex()) {
            val col = i % cols; val row = i / cols
            val l = 40f + col * cardW + 10f
            val t = startY + row * (cardH + 15f)
            val r = l + cardW - 20f
            val b = t + cardH
            val rr = RectF(l, t, r, b)
            weaponItems.add(rr)
            paint.color = Color.argb(200, 30, 30, 46)
            c.drawRoundRect(rr, 20f, 20f, paint)
            paint.color = currentSkin.primary
            rect.set(l + 15f, t + 25f, r - 15f, t + 65f)
            c.drawRoundRect(rect, 5f, 5f, paint)
            text.textAlign = Paint.Align.LEFT
            text.textSize = 26f; text.color = Color.WHITE
            c.drawText(wp.name, l + 15f, t + 100f, text)
            text.textSize = 18f; text.color = Color.GRAY
            c.drawText("Урон ${wp.damage.toInt()} • ${wp.magSize} патр.", l + 15f, t + 125f, text)
            val owned = wp.id in ownedWeapons
            val selected = currentWeapon.id == wp.id && useWeapon
            text.textSize = 22f
            text.color = when {
                selected -> Color.rgb(100, 220, 100)
                owned -> Color.rgb(80, 160, 240)
                else -> Color.rgb(255, 200, 60)
            }
            c.drawText(
                when { selected -> "✓ В РУКАХ"; owned -> "ВЗЯТЬ"; else -> "ЗАКРЫТО" },
                l + 15f, t + 165f, text
            )
        }
        closeWeaponsRect = RectF(w / 2f - 120f, h - 110f, w / 2f + 120f, h - 40f)
        paint.color = Color.rgb(180, 40, 40)
        c.drawRoundRect(closeWeaponsRect, 30f, 30f, paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 30f; text.color = Color.WHITE
        c.drawText("ЗАКРЫТЬ", w / 2f, h - 68f, text)
    }

    private fun renderKnivesMenu(c: Canvas) {
        paint.color = Color.argb(230, 10, 10, 20)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 48f; text.color = Color.WHITE
        c.drawText("НОЖИ", w / 2f, 70f, text)
        knifeItems.clear()
        val cols = 3
        val cardW = (w - 120f) / cols
        val cardH = 180f
        val startY = 110f
        for ((i, k) in Knives.all.withIndex()) {
            val col = i % cols; val row = i / cols
            val l = 40f + col * cardW + 10f
            val t = startY + row * (cardH + 15f)
            val r = l + cardW - 20f
            val b = t + cardH
            val rr = RectF(l, t, r, b)
            knifeItems.add(rr)
            paint.color = Color.argb(200, 30, 30, 46)
            c.drawRoundRect(rr, 20f, 20f, paint)
            paint.color = currentSkin.primary
            rect.set(l + 15f, t + 25f, r - 15f, t + 65f)
            c.drawRoundRect(rect, 5f, 5f, paint)
            text.textAlign = Paint.Align.LEFT
            text.textSize = 26f; text.color = Color.WHITE
            c.drawText(k.name, l + 15f, t + 100f, text)
            text.textSize = 18f; text.color = Color.GRAY
            c.drawText("Урон ${k.damage.toInt()}", l + 15f, t + 125f, text)
            val owned = k.id in ownedKnives
            val selected = currentKnife?.id == k.id && !useWeapon
            text.textSize = 22f
            text.color = when {
                selected -> Color.rgb(100, 220, 100)
                owned -> Color.rgb(80, 160, 240)
                else -> Color.rgb(255, 200, 60)
            }
            c.drawText(
                when {
                    selected -> "✓ В РУКАХ"
                    owned -> "ВЗЯТЬ"
                    k.priceRub == 0 -> "БЕСПЛАТНО"
                    else -> "${k.priceRub} ₽"
                },
                l + 15f, t + 165f, text
            )
        }
        backToWeaponRect = RectF(40f, h - 110f, 340f, h - 40f)
        paint.color = Color.rgb(40, 140, 220)
        c.drawRoundRect(backToWeaponRect, 30f, 30f, paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 24f; text.color = Color.WHITE
        c.drawText("В РУКИ ОРУЖИЕ", backToWeaponRect.centerX(), backToWeaponRect.centerY() + 8f, text)
        closeKnivesRect = RectF(w - 280f, h - 110f, w - 40f, h - 40f)
        paint.color = Color.rgb(180, 40, 40)
        c.drawRoundRect(closeKnivesRect, 30f, 30f, paint)
        c.drawText("ЗАКРЫТЬ", closeKnivesRect.centerX(), closeKnivesRect.centerY() + 8f, text)
    }

    private fun renderTradeMenu(c: Canvas) {
        paint.color = Color.argb(230, 10, 10, 20)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        text.textAlign = Paint.Align.CENTER; text.textSize = 48f; text.color = Color.WHITE
        c.drawText("ТРЕЙД", w / 2f, 70f, text)

        if (pendingOffer != null) {
            val (_, give, want) = pendingOffer!!
            text.textSize = 28f; text.color = Color.rgb(200, 200, 200)
            c.drawText("Входящее предложение", w / 2f, 160f, text)
            text.textSize = 32f; text.color = Color.WHITE
            c.drawText("Дают: $give", w / 2f, 220f, text)
            c.drawText("Хотят: $want", w / 2f, 270f, text)
            acceptTradeRect = RectF(w / 2f - 380f, h - 160f, w / 2f - 40f, h - 60f)
            paint.color = Color.rgb(60, 180, 90)
            c.drawRoundRect(acceptTradeRect, 30f, 30f, paint)
            text.textSize = 32f; text.color = Color.WHITE
            c.drawText("ПРИНЯТЬ", acceptTradeRect.centerX(), acceptTradeRect.centerY() + 12f, text)
            declineTradeRect = RectF(w / 2f + 40f, h - 160f, w / 2f + 380f, h - 60f)
            paint.color = Color.rgb(180, 50, 50)
            c.drawRoundRect(declineTradeRect, 30f, 30f, paint)
            c.drawText("ОТКЛОНИТЬ", declineTradeRect.centerX(), declineTradeRect.centerY() + 12f, text)
        } else {
            text.textSize = 26f; text.color = Color.GRAY
            c.drawText("Здесь появятся входящие предложения.", w / 2f, 200f, text)
        }

        closeTradeRect = RectF(w / 2f - 140f, h - 110f, w / 2f + 140f, h - 40f)
        paint.color = Color.rgb(60, 60, 80)
        c.drawRoundRect(closeTradeRect, 30f, 30f, paint)
        text.textSize = 30f; text.color = Color.WHITE
        c.drawText("ЗАКРЫТЬ", closeTradeRect.centerX(), closeTradeRect.centerY() + 10f, text)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (storeOpen) return handleStoreTouch(ev)
        if (weaponsOpen) return handleWeaponsTouch(ev)
        if (knivesOpen) return handleKnivesTouch(ev)
        if (tradeOpen) return handleTradeTouch(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val x = ev.getX(i); val y = ev.getY(i)
                val id = ev.getPointerId(i)
                for (mi in 0..3) {
                    if (hypot(x - menuXs[mi], y - menuY) < menuR * 1.2f) {
                        when (mi) {
                            0 -> storeOpen = true
                            1 -> weaponsOpen = true
                            2 -> knivesOpen = true
                            3 -> tradeOpen = true
                        }
                        return true
                    }
                }
                if (hypot(x - fireCx, y - fireCy) < fireR * 1.35f && fireId == -1) {
                    fireId = id; firing = true; shoot()
                } else if (x < w * 0.55f && y > h * 0.35f && joyId == -1) {
                    joyId = id; joyBx = x; joyBy = y; joyDx = 0f; joyDy = 0f
                } else if (lookId == -1) {
                    lookId = id; lookPrevX = x
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    val x = ev.getX(i); val y = ev.getY(i)
                    if (id == joyId) {
                        var dx = x - joyBx; var dy = y - joyBy
                        val len = hypot(dx, dy)
                        if (len > joyR) { dx = dx / len * joyR; dy = dy / len * joyR }
                        joyDx = dx / joyR; joyDy = dy / joyR
                    } else if (id == lookId) {
                        pa += (x - lookPrevX) * 0.005f; lookPrevX = x
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                if (id == joyId) { joyId = -1; joyDx = 0f; joyDy = 0f }
                if (id == lookId) lookId = -1
                if (id == fireId) { fireId = -1; firing = false }
            }
            MotionEvent.ACTION_CANCEL -> {
                joyId = -1; lookId = -1; fireId = -1
                joyDx = 0f; joyDy = 0f; firing = false
            }
        }
        return true
    }

    private fun handleStoreTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_UP) return true
        val x = ev.x; val y = ev.y
        if (closeStoreRect.contains(x, y)) { storeOpen = false; return true }
        for ((i, r) in storeItems.withIndex()) {
            if (!r.contains(x, y)) continue
            val skin = Skins.all[i]
            if (store.isOwned(skin.id)) {
                store.selectSkin(skin.id)
                currentSkin = skin
                net?.sendSkin(skin.id)
                sound.buy()
            } else if (skin.priceRub == 0) {
                store.devUnlock(skin.id); store.selectSkin(skin.id)
                currentSkin = skin; net?.sendSkin(skin.id); sound.buy()
            } else {
                store.devUnlock(skin.id); sound.buy()
            }
            return true
        }
        return true
    }

    private fun handleWeaponsTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_UP) return true
        val x = ev.x; val y = ev.y
        if (closeWeaponsRect.contains(x, y)) { weaponsOpen = false; return true }
        for ((i, r) in weaponItems.withIndex()) {
            if (!r.contains(x, y)) continue
            val wp = Weapons.all[i]
            if (wp.id !in ownedWeapons) { sound.hurt(); return true }
            currentWeapon = wp
            ammoMag = wp.magSize
            reloading = 0f
            useWeapon = true
            prefs.edit().putString("weapon", wp.id).apply()
            sound.buy()
            weaponsOpen = false
            return true
        }
        return true
    }

    private fun handleKnivesTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_UP) return true
        val x = ev.x; val y = ev.y
        if (closeKnivesRect.contains(x, y)) { knivesOpen = false; return true }
        if (backToWeaponRect.contains(x, y)) {
            useWeapon = true
                      sound.buy()
            knivesOpen = false
            return true
        }
        for ((i, r) in knifeItems.withIndex()) {
            if (!r.contains(x, y)) continue
            val k = Knives.all[i]
            if (k.id !in ownedKnives) {
                if (k.priceRub == 0) {
                    ownedKnives.add(k.id)
                    prefs.edit().putString("knives", ownedKnives.joinToString(",")).apply()
                } else { sound.hurt(); return true }
            }
            currentKnife = k
            useWeapon = false
            prefs.edit().putString("knife", k.id).apply()
            sound.buy()
            knivesOpen = false
            return true
        }
        return true
    }

    private fun handleTradeTouch(ev: MotionEvent): Boolean {
        if (ev.actionMasked != MotionEvent.ACTION_UP) return true
        val x = ev.x; val y = ev.y
        if (closeTradeRect.contains(x, y)) { tradeOpen = false; return true }
        val offer = pendingOffer
        if (offer != null) {
            if (acceptTradeRect.contains(x, y)) {
                net?.sendTradeAccept(offer.first)
                pendingOffer = null
                sound.buy()
                return true
            }
            if (declineTradeRect.contains(x, y)) {
                net?.sendTradeDecline(offer.first)
                pendingOffer = null
                sound.hurt()
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        net?.close()
        store.destroy()
        sound.release()
    }
}
