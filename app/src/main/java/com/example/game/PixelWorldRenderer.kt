package com.example.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.data.VehicleSpec
import com.example.ui.theme.PixelDisplayFont
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun DrawScope.drawMushroomWorld(
  engine: GameEngine,
  textMeasurer: TextMeasurer,
  animClockSec: Float,
  questionTextLayout: TextLayoutResult? = null,
  playerBitmap: ImageBitmap? = null,
) {
  if (size.width <= 1f || size.height <= 1f) return
  val scale = size.height / LevelGenerator.WORLD_HEIGHT
  if (!scale.isFinite() || scale <= 0f) return
  val viewW = size.width / scale
  if (!viewW.isFinite() || viewW <= 0f) return
  val camX = engine.cameraX

  drawSkyAndParallax(engine.worldTheme, camX, viewW, scale, animClockSec)

  withTransform({
    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    translate(left = -camX, top = 0f)
  }) {
    // 1. Ground & Pits
    drawGroundSegments(engine, camX, viewW, animClockSec)

    // 2. Flagpole & Brick Castle
    drawCastleAndFlagpole(LevelGenerator.FLAGPOLE_X, animClockSec)

    // 3. Warp Pipes
    for (pipe in engine.pipes) {
      if (pipe.x + pipe.width < camX - 80f || pipe.x > camX + viewW + 80f) continue
      drawWarpPipe(pipe)
    }

    // 4. Brick & Question Blocks
    for (block in engine.blocks) {
      if (block.broken) continue
      if (block.x + block.size < camX - 60f || block.x > camX + viewW + 60f) continue
      drawBlockTile(block, textMeasurer, animClockSec, questionTextLayout)
    }

    // 5. Pickups
    for (pickup in engine.pickups) {
      if (pickup.collected) continue
      if (pickup.x < camX - 60f || pickup.x > camX + viewW + 60f) continue
      drawPickup(pickup, animClockSec)
    }

    // 6. Unoccupied & Police Cars
    for (car in engine.cars) {
      if (car.wrecked || car.isOccupiedByPlayer) continue
      if (car.x + car.width < camX - 120f || car.x > camX + viewW + 120f) continue
      drawPixelCar(car, animClockSec, hasPlayerInside = false)
    }

    // 7. Enemies
    for (enemy in engine.enemies) {
      if (!enemy.alive) continue
      if (enemy.x + enemy.width < camX - 100f || enemy.x > camX + viewW + 260f) continue
      drawEnemy(enemy, animClockSec)
    }

    // 8. Driven Car OR On-Foot Hero + Active Power-Up Auras
    val drivenCar = engine.cars.firstOrNull { it.id == engine.player.drivenCarId && !it.wrecked }
    val pCenterX = engine.currentPlayerCenterX()
    val pCenterY = engine.currentPlayerCenterY()
    if (engine.player.starPowerTimer > 0f) {
      val rainbowColors = listOf(Color(0xFFFBD000), Color(0xFFFF3B30), Color(0xFF00E5FF), Color(0xFF43B047))
      val auraColor = rainbowColors[((animClockSec * 14f).toInt()) % rainbowColors.size]
      val auraRadius = (if (drivenCar != null) 66f else 36f) + sin(animClockSec * 18f) * 4f
      drawCircle(color = auraColor.copy(alpha = 0.28f), radius = auraRadius, center = Offset(pCenterX, pCenterY))
      drawCircle(color = auraColor, radius = auraRadius, center = Offset(pCenterX, pCenterY), style = Stroke(3.5f))
    }
    if (engine.player.shieldCharges > 0) {
      val shieldR = if (drivenCar != null) 60f else 33f
      drawCircle(color = Color(0x3300E5FF), radius = shieldR, center = Offset(pCenterX, pCenterY))
      drawCircle(color = Color(0xFF00E5FF), radius = shieldR, center = Offset(pCenterX, pCenterY), style = Stroke(2.5f))
    }
    if (drivenCar != null) {
      drawPixelCar(drivenCar, animClockSec, hasPlayerInside = true, playerBitmap = playerBitmap)
    } else {
      drawPixelHero(engine.player, animClockSec, playerBitmap = playerBitmap)
    }

    // 9. Long-Range Sniper Sightline & Target Reticle
    val tx = engine.lockedTargetX
    val ty = engine.lockedTargetY
    if (tx != null && ty != null) {
      val ox = engine.currentPlayerCenterX() + if (engine.player.facingRight) 24f else -24f
      val oy = engine.currentPlayerCenterY() - 6f
      drawLine(
        color = Color(0xAA00E5FF),
        start = Offset(ox, oy),
        end = Offset(tx, ty),
        strokeWidth = 2.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), animClockSec * 80f),
      )
      val pulse = 24f + sin(animClockSec * 10f) * 3f
      drawCircle(
        color = Color(0xFFFF3B30),
        radius = pulse,
        center = Offset(tx, ty),
        style = Stroke(width = 3f),
      )
      drawLine(Color(0xFFFFD600), Offset(tx - pulse - 8f, ty), Offset(tx + pulse + 8f, ty), 2.5f)
      drawLine(Color(0xFFFFD600), Offset(tx, ty - pulse - 8f), Offset(tx, ty + pulse + 8f), 2.5f)
    }

    // 10. Projectiles
    for (proj in engine.projectiles) {
      drawProjectile(proj, animClockSec)
    }

    // 11. Particles & Floating Score Popups
    for (pt in engine.particles) {
      val alpha = (pt.life / pt.maxLife).coerceIn(0f, 1f)
      val col = Color(pt.colorArgb).copy(alpha = alpha)
      if (pt.text != null) {
        val measured =
          textMeasurer.measure(
            text = pt.text,
            style =
              TextStyle(
                fontFamily = PixelDisplayFont,
                fontSize = 12.sp,
                color = col,
                fontWeight = FontWeight.Bold,
              ),
          )
        drawText(
          textLayoutResult = measured,
          topLeft = Offset(pt.x, pt.y),
        )
      } else {
        drawRect(
          color = col,
          topLeft = Offset(pt.x - pt.size * 0.5f, pt.y - pt.size * 0.5f),
          size = Size(pt.size, pt.size),
        )
      }
    }
  }
}

private fun DrawScope.drawSkyAndParallax(
  theme: WorldTheme,
  camX: Float,
  viewW: Float,
  scale: Float,
  clock: Float,
) {
  val skyColors =
    when (theme) {
      WorldTheme.TOADSTOOL_STRIP -> listOf(Color(0xFF4C86F7), Color(0xFF8AB8FF))
      WorldTheme.KOOPA_VICE_HWY -> listOf(Color(0xFF1D184A), Color(0xFF7B2B72), Color(0xFFFF6B7A))
      WorldTheme.BOWSER_BLVD -> listOf(Color(0xFF190A14), Color(0xFF471222), Color(0xFF8F221C))
    }
  drawRect(brush = Brush.verticalGradient(skyColors), size = size)

  withTransform({
    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
  }) {
    // Sun / Retro Moon
    val sunColor =
      when (theme) {
        WorldTheme.TOADSTOOL_STRIP -> Color(0xFFFFF39E)
        WorldTheme.KOOPA_VICE_HWY -> Color(0xFFFFB347)
        WorldTheme.BOWSER_BLVD -> Color(0xFFFF4D2E)
      }
    drawCircle(color = sunColor.copy(alpha = 0.9f), radius = 48f, center = Offset(viewW * 0.8f, 110f))

    // Distant Vice-style pixel city skyline (slow parallax)
    val skylineOffset = -(camX * 0.14f) % 420f
    val buildingColor =
      when (theme) {
        WorldTheme.TOADSTOOL_STRIP -> Color(0x44203864)
        WorldTheme.KOOPA_VICE_HWY -> Color(0x99191438)
        WorldTheme.BOWSER_BLVD -> Color(0xAA210C15)
      }
    val windowColor =
      when (theme) {
        WorldTheme.TOADSTOOL_STRIP -> Color(0x55FFF59D)
        WorldTheme.KOOPA_VICE_HWY -> Color(0xBB00E5FF)
        WorldTheme.BOWSER_BLVD -> Color(0xBBFF6D00)
      }
    var bx = skylineOffset - 420f
    var bIdx = 0
    while (bx < viewW + 420f) {
      val bh = 110f + ((bIdx * 53) % 140)
      val bw = 64f + ((bIdx * 29) % 36)
      val topY = LevelGenerator.GROUND_Y - bh
      drawRect(color = buildingColor, topLeft = Offset(bx, topY), size = Size(bw, bh))
      // Lit pixel windows
      var wy = topY + 14f
      while (wy < LevelGenerator.GROUND_Y - 24f) {
        var wx = bx + 10f
        while (wx < bx + bw - 14f) {
          if ((wx.toInt() + wy.toInt() + bIdx) % 3 != 0) {
            drawRect(color = windowColor, topLeft = Offset(wx, wy), size = Size(8f, 10f))
          }
          wx += 16f
        }
        wy += 22f
      }
      bx += bw + 14f
      bIdx++
    }

    // Classic Mario Smiling Hills (medium parallax)
    val hillColor =
      when (theme) {
        WorldTheme.TOADSTOOL_STRIP -> Color(0xFF39A844)
        WorldTheme.KOOPA_VICE_HWY -> Color(0xFF286073)
        WorldTheme.BOWSER_BLVD -> Color(0xFF47212E)
      }
    val hillOffset = -(camX * 0.32f) % 480f
    var hx = hillOffset - 480f
    while (hx < viewW + 480f) {
      drawOval(
        color = hillColor,
        topLeft = Offset(hx, LevelGenerator.GROUND_Y - 120f),
        size = Size(230f, 220f),
      )
      // Cute pixel eyes on hills
      drawRect(Color(0xCC111111), Offset(hx + 88f, LevelGenerator.GROUND_Y - 76f), Size(8f, 16f))
      drawRect(Color(0xCC111111), Offset(hx + 116f, LevelGenerator.GROUND_Y - 76f), Size(8f, 16f))
      hx += 480f
    }

    // Fluffy pixel clouds
    val cloudOffset = -(camX * 0.22f + clock * 14f) % 380f
    val cloudColor =
      when (theme) {
        WorldTheme.TOADSTOOL_STRIP -> Color.White.copy(alpha = 0.92f)
        WorldTheme.KOOPA_VICE_HWY -> Color(0xCCFFB8D2)
        WorldTheme.BOWSER_BLVD -> Color(0x99783B4A)
      }
    var cx = cloudOffset - 380f
    while (cx < viewW + 380f) {
      drawRoundRect(
        color = cloudColor,
        topLeft = Offset(cx, 78f),
        size = Size(110f, 34f),
        cornerRadius = CornerRadius(14f, 14f),
      )
      drawCircle(color = cloudColor, radius = 24f, center = Offset(cx + 38f, 76f))
      drawCircle(color = cloudColor, radius = 20f, center = Offset(cx + 72f, 80f))
      cx += 380f
    }
  }
}

private fun DrawScope.drawGroundSegments(
  engine: GameEngine,
  camX: Float,
  viewW: Float,
  clock: Float,
) {
  // Draw lava/water shimmer inside pits
  val pitColor =
    if (engine.worldTheme == WorldTheme.BOWSER_BLVD) Color(0xFFFF4500) else Color(0xFF0E2A47)
  drawRect(
    color = pitColor,
    topLeft = Offset(camX - 40f, LevelGenerator.GROUND_Y + 46f),
    size = Size(viewW + 80f, LevelGenerator.WORLD_HEIGHT - LevelGenerator.GROUND_Y),
  )

  val grassColor =
    when (engine.worldTheme) {
      WorldTheme.TOADSTOOL_STRIP -> Color(0xFF43B047)
      WorldTheme.KOOPA_VICE_HWY -> Color(0xFF00B8A9)
      WorldTheme.BOWSER_BLVD -> Color(0xFF6B2D3E)
    }
  val roadAsphalt = Color(0xFF282E3E)

  for (seg in engine.groundSegments) {
    if (seg.endX < camX - 60f || seg.startX > camX + viewW + 60f) continue
    val w = seg.endX - seg.startX
    // Asphalt road deck with dashed yellow highway center line
    drawRect(
      color = roadAsphalt,
      topLeft = Offset(seg.startX, LevelGenerator.GROUND_Y),
      size = Size(w, 140f),
    )
    // Grass/curb strip along road top
    drawRect(
      color = grassColor,
      topLeft = Offset(seg.startX, LevelGenerator.GROUND_Y),
      size = Size(w, 12f),
    )
    // Dashed highway stripes
    var sx = seg.startX + 20f
    while (sx < seg.endX - 30f) {
      if (sx > camX - 60f && sx < camX + viewW + 60f) {
        drawRect(
          color = Color(0xFFFBD000),
          topLeft = Offset(sx, LevelGenerator.GROUND_Y + 34f),
          size = Size(34f, 6f),
        )
      }
      sx += 78f
    }
    // Lower Mario brick foundation
    drawRect(
      color = Color(0xFF8C3B14),
      topLeft = Offset(seg.startX, LevelGenerator.GROUND_Y + 62f),
      size = Size(w, 78f),
    )
    var tx = seg.startX
    while (tx < seg.endX) {
      if (tx > camX - 60f && tx < camX + viewW + 60f) {
        drawLine(
          color = Color(0xFF592208),
          start = Offset(tx, LevelGenerator.GROUND_Y + 62f),
          end = Offset(tx, LevelGenerator.GROUND_Y + 140f),
          strokeWidth = 2f,
        )
      }
      tx += 48f
    }
  }
}

private fun DrawScope.drawWarpPipe(pipe: WarpPipe) {
  val topY = LevelGenerator.GROUND_Y - pipe.height
  val lipH = 22f
  // Pipe body
  drawRect(
    color = Color(0xFF2FA836),
    topLeft = Offset(pipe.x + 6f, topY + lipH),
    size = Size(pipe.width - 12f, pipe.height - lipH),
  )
  drawRect(
    color = Color(0xFF6EEA73),
    topLeft = Offset(pipe.x + 14f, topY + lipH),
    size = Size(12f, pipe.height - lipH),
  )
  drawRect(
    color = Color(0xFF1B6E20),
    topLeft = Offset(pipe.x + pipe.width - 18f, topY + lipH),
    size = Size(12f, pipe.height - lipH),
  )
  // Pipe lip
  drawRect(
    color = Color(0xFF39C642),
    topLeft = Offset(pipe.x, topY),
    size = Size(pipe.width, lipH),
  )
  drawRect(
    color = Color(0xFF89F78E),
    topLeft = Offset(pipe.x + 8f, topY + 2f),
    size = Size(14f, lipH - 4f),
  )
  drawRect(
    color = Color(0xFF135017),
    topLeft = Offset(pipe.x, topY),
    size = Size(pipe.width, lipH),
    style = Stroke(width = 3f),
  )
  if (pipe.isRamp) {
    // Yellow chevron ramp indicator on ramp pipes
    drawCircle(color = Color(0xFFFBD000), radius = 6f, center = Offset(pipe.x + pipe.width * 0.5f, topY + 11f))
  }
}

private fun DrawScope.drawBlockTile(
  block: BlockTile,
  textMeasurer: TextMeasurer,
  clock: Float,
  cachedQuestionLayout: TextLayoutResult? = null,
) {
  val y = block.y + block.bumpOffset
  val s = block.size
  when (block.kind) {
    BlockKind.BRICK -> {
      drawRect(color = Color(0xFFC84C0C), topLeft = Offset(block.x, y), size = Size(s, s))
      drawRect(color = Color(0xFFE8702A), topLeft = Offset(block.x + 3f, y + 3f), size = Size(s - 6f, 6f))
      drawLine(Color(0xFF6B2404), Offset(block.x, y + s * 0.5f), Offset(block.x + s, y + s * 0.5f), 3f)
      drawLine(Color(0xFF6B2404), Offset(block.x + s * 0.5f, y), Offset(block.x + s * 0.5f, y + s * 0.5f), 3f)
      drawRect(Color(0xFF3D1402), Offset(block.x, y), Size(s, s), style = Stroke(2.5f))
    }
    BlockKind.SOLID_STEP -> {
      drawRect(color = Color(0xFF9E5A3C), topLeft = Offset(block.x, y), size = Size(s, s))
      drawRect(color = Color(0xFFD68D6B), topLeft = Offset(block.x + 4f, y + 4f), size = Size(s - 8f, 8f))
      drawRect(Color(0xFF3A1F14), Offset(block.x, y), Size(s, s), style = Stroke(3f))
    }
    BlockKind.QUESTION_COIN,
    BlockKind.QUESTION_SHROOM,
    BlockKind.QUESTION_STAR,
    BlockKind.QUESTION_FIRE,
    BlockKind.QUESTION_SHIELD,
    BlockKind.QUESTION_MISSILE,
    BlockKind.QUESTION_SHOTGUN,
    BlockKind.QUESTION_MINIGUN,
    BlockKind.QUESTION_NITRO -> {
      val bg =
        when {
          block.used -> Color(0xFF7D5D3B)
          block.kind == BlockKind.QUESTION_MISSILE -> if (sin(clock * 8f) > 0f) Color(0xFFFF5252) else Color(0xFFFF8A65)
          block.kind == BlockKind.QUESTION_SHOTGUN -> if (sin(clock * 8f) > 0f) Color(0xFFFFB300) else Color(0xFFFF8F00)
          block.kind == BlockKind.QUESTION_MINIGUN -> if (sin(clock * 8f) > 0f) Color(0xFF00E5FF) else Color(0xFF00B8D4)
          block.kind == BlockKind.QUESTION_SHIELD -> Color(0xFF40C4FF)
          sin(clock * 6f) > 0f -> Color(0xFFFBD000)
          else -> Color(0xFFFFB300)
        }
      drawRect(color = bg, topLeft = Offset(block.x, y), size = Size(s, s))
      drawRect(color = Color(0xFF4A2B04), topLeft = Offset(block.x, y), size = Size(s, s), style = Stroke(3f))
      // Corner rivets
      drawRect(Color(0xFF4A2B04), Offset(block.x + 5f, y + 5f), Size(4f, 4f))
      drawRect(Color(0xFF4A2B04), Offset(block.x + s - 9f, y + 5f), Size(4f, 4f))
      drawRect(Color(0xFF4A2B04), Offset(block.x + 5f, y + s - 9f), Size(4f, 4f))
      drawRect(Color(0xFF4A2B04), Offset(block.x + s - 9f, y + s - 9f), Size(4f, 4f))
      if (!block.used) {
        val questionLayout =
          cachedQuestionLayout
            ?: textMeasurer.measure(
              text = "?",
              style =
                TextStyle(
                  fontFamily = PixelDisplayFont,
                  fontSize = 20.sp,
                  color = Color(0xFF5A2B00),
                ),
            )
        drawText(
          textLayoutResult = questionLayout,
          topLeft = Offset(block.x + 15f, y + 12f),
        )
      }
    }
  }
}

private fun DrawScope.drawCastleAndFlagpole(flagX: Float, clock: Float) {
  // Flagpole
  drawRect(color = Color(0xFF282E3E), topLeft = Offset(flagX - 18f, LevelGenerator.GROUND_Y - 32f), size = Size(36f, 32f))
  drawRect(color = Color(0xFF55E65D), topLeft = Offset(flagX - 4f, LevelGenerator.GROUND_Y - 290f), size = Size(8f, 258f))
  drawCircle(color = Color(0xFFFBD000), radius = 12f, center = Offset(flagX, LevelGenerator.GROUND_Y - 298f))
  // Checkered GTA x Mario flag
  val wave = sin(clock * 5f) * 4f
  val flagPath =
    Path().apply {
      moveTo(flagX + 4f, LevelGenerator.GROUND_Y - 280f)
      lineTo(flagX + 74f, LevelGenerator.GROUND_Y - 262f + wave)
      lineTo(flagX + 4f, LevelGenerator.GROUND_Y - 234f)
      close()
    }
  drawPath(path = flagPath, color = Color(0xFFE52521))
  drawCircle(color = Color(0xFFFBD000), radius = 8f, center = Offset(flagX + 28f, LevelGenerator.GROUND_Y - 258f + wave * 0.4f))

  // Classic Brick Castle behind flagpole
  val cx = flagX + 115f
  val cy = LevelGenerator.GROUND_Y - 190f
  drawRect(color = Color(0xFFB54B2A), topLeft = Offset(cx, cy + 40f), size = Size(190f, 150f))
  drawRect(color = Color(0xFFD15E38), topLeft = Offset(cx + 38f, cy), size = Size(114f, 60f))
  // Battlements
  for (i in 0 until 5) {
    drawRect(color = Color(0xFF8C3318), topLeft = Offset(cx + i * 40f, cy + 26f), size = Size(24f, 18f))
  }
  // Castle arch gate
  drawRoundRect(
    color = Color(0xFF141824),
    topLeft = Offset(cx + 68f, LevelGenerator.GROUND_Y - 84f),
    size = Size(54f, 84f),
    cornerRadius = CornerRadius(24f, 24f),
  )
}

private fun DrawScope.drawPixelCar(
  car: WorldCar,
  clock: Float,
  hasPlayerInside: Boolean,
  playerBitmap: ImageBitmap? = null,
) {
  val bodyColor =
    when {
      car.isPolice -> Color(0xFF1E222D)
      car.spec == VehicleSpec.KART_RED -> Color(0xFFE52521)
      car.spec == VehicleSpec.MUSCLE_GOLD -> Color(0xFFFBD000)
      else -> Color(0xFF2E8B57)
    }
  val trimColor =
    when {
      car.isPolice -> Color.White
      car.spec == VehicleSpec.MUSCLE_GOLD -> Color(0xFF00E5FF)
      else -> Color.White
    }

  val dir = if (car.facingRight) 1f else -1f
  val cx = car.x + car.width * 0.5f

  // Shadow underneath car
  drawOval(
    color = Color(0x66000000),
    topLeft = Offset(car.x + 8f, car.y + car.height - 6f),
    size = Size(car.width - 16f, 10f),
  )

  // Headlight beam
  val beamStart = if (car.facingRight) car.x + car.width else car.x
  drawRect(
    color = Color(0x33FFF59D),
    topLeft = Offset(if (car.facingRight) beamStart else beamStart - 110f, car.y + 18f),
    size = Size(110f, 22f),
  )

  // Driver inside open convertible / cabin!
  if (hasPlayerInside && playerBitmap != null) {
    val headX = cx - dir * 6f
    val headY = car.y - 14f
    val cropH = (playerBitmap.height * 0.34f).toInt().coerceAtLeast(1)
    val dstW = 28
    val dstH = 32
    val dstLeft = (headX - dstW * 0.5f).roundToInt()
    val dstTop = headY.roundToInt()
    withTransform({
      if (!car.facingRight) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(headX, headY + dstH * 0.5f))
      }
    }) {
      drawImage(
        image = playerBitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(playerBitmap.width, cropH),
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(dstW, dstH),
        filterQuality = FilterQuality.None,
      )
    }
  } else if (hasPlayerInside || car.hasPoliceDriver) {
    val headX = cx - dir * 6f
    val headY = car.y - 4f
    // Face
    drawRect(Color(0xFFFFCC99), Offset(headX - 8f, headY), Size(16f, 14f))
    // Cap
    val capCol = if (car.hasPoliceDriver) Color(0xFF2962FF) else Color(0xFFE52521)
    drawRect(capCol, Offset(headX - 10f, headY - 7f), Size(20f, 8f))
    drawRect(capCol, Offset(if (car.facingRight) headX else headX - 14f, headY - 3f), Size(14f, 4f))
  }

  // Lower chassis
  drawRoundRect(
    color = bodyColor,
    topLeft = Offset(car.x, car.y + 16f),
    size = Size(car.width, car.height - 26f),
    cornerRadius = CornerRadius(10f, 10f),
  )
  // Upper cabin / spoiler
  drawRoundRect(
    color = if (car.isPolice) Color.White else bodyColor.copy(alpha = 0.9f),
    topLeft = Offset(car.x + 22f, car.y + 4f),
    size = Size(car.width - 48f, 16f),
    cornerRadius = CornerRadius(6f, 6f),
  )
  // Racing stripe / Police door panel
  drawRect(
    color = trimColor,
    topLeft = Offset(car.x + 20f, car.y + 24f),
    size = Size(car.width - 40f, 8f),
  )
  // Windshield
  val windX = if (car.facingRight) car.x + car.width - 38f else car.x + 18f
  drawRect(color = Color(0xFF81D4FA), topLeft = Offset(windX, car.y + 6f), size = Size(18f, 12f))

  // Flashing Police Siren Lightbar
  if (car.isPolice) {
    val flashRed = sin(clock * 18f) > 0f
    drawRect(
      color = if (flashRed) Color(0xFFFF1744) else Color(0xFF2979FF),
      topLeft = Offset(cx - 10f, car.y - 4f),
      size = Size(10f, 6f),
    )
    drawRect(
      color = if (!flashRed) Color(0xFFFF1744) else Color(0xFF2979FF),
      topLeft = Offset(cx, car.y - 4f),
      size = Size(10f, 6f),
    )
  }

  // Spinning Pixel Wheels
  val wheelPositions = listOf(car.x + 22f, car.x + car.width - 22f)
  for (wx in wheelPositions) {
    val wy = car.y + car.height - 12f
    drawCircle(color = Color(0xFF11131A), radius = 13f, center = Offset(wx, wy))
    drawCircle(color = Color(0xFFBDC3C7), radius = 6f, center = Offset(wx, wy))
    val spokeX = cos(car.wheelSpin) * 5f
    val spokeY = sin(car.wheelSpin) * 5f
    drawLine(Color(0xFF2C3E50), Offset(wx - spokeX, wy - spokeY), Offset(wx + spokeX, wy + spokeY), 2.5f)
  }

  // Damage smoke if low HP
  if (car.hp <= 2) {
    drawCircle(
      color = Color(0x99555555),
      radius = 8f + sin(clock * 9f) * 3f,
      center = Offset(car.x + 16f, car.y + 4f),
    )
  }
}

private fun DrawScope.drawPixelHero(
  player: PlayerState,
  clock: Float,
  playerBitmap: ImageBitmap? = null,
) {
  if (player.invulnTimer > 0f && sin(clock * 32f) > 0f) return
  val x = player.x
  val crouchOffset = if (player.isCrouching) 12f else 0f
  val y = player.y + crouchOffset

  if (playerBitmap != null) {
    val bottomY = player.y + player.height
    val centerX = player.x + player.width * 0.5f
    val drawH = if (player.isCrouching) 64f else 84f
    val aspect = playerBitmap.width.toFloat() / playerBitmap.height.toFloat().coerceAtLeast(1f)
    val drawW = (drawH * aspect * 1.15f).coerceAtLeast(32f)
    val bobY = if (abs(player.vx) > 20f && player.onGround) -abs(sin(player.walkAnim)) * 4f else 0f
    val leanDeg =
      if (abs(player.vx) > 40f && !player.isCrouching) {
        if (player.facingRight) 4f else -4f
      } else 0f

    withTransform({
      if (!player.facingRight) {
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(centerX, bottomY - drawH * 0.5f))
      }
      if (leanDeg != 0f) {
        rotate(degrees = if (player.facingRight) leanDeg else -leanDeg, pivot = Offset(centerX, bottomY))
      }
    }) {
      drawImage(
        image = playerBitmap,
        dstOffset = IntOffset((centerX - drawW * 0.5f).roundToInt(), (bottomY - drawH + bobY).roundToInt()),
        dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
        filterQuality = FilterQuality.None,
      )
    }

    // Long-Range Blaster / Active Power-Up Barrel
    val gunColor =
      when (player.activeWeaponPowerUp) {
        WeaponPowerUp.MISSILE_LAUNCHER -> Color(0xFFFF5252)
        WeaponPowerUp.SPREAD_SHOTGUN -> Color(0xFFFFB300)
        WeaponPowerUp.RAPID_MINIGUN -> Color(0xFF00E5FF)
        WeaponPowerUp.FIRE_OVERDRIVE -> Color(0xFFFF6D00)
        WeaponPowerUp.NONE -> Color(0xFF00E5FF)
      }
    val gunY = bottomY - drawH * 0.58f + bobY
    val gunX = if (player.facingRight) centerX + 10f else centerX - 28f
    drawRect(Color(0xFF37474F), Offset(gunX, gunY), Size(18f, 6f))
    drawRect(gunColor, Offset(if (player.facingRight) gunX + 13f else gunX, gunY), Size(5f, 6f))
    return
  }

  val stride = if (abs(player.vx) > 20f && player.onGround) sin(player.walkAnim) * 5f else 0f

  // Fallback pixel hero
  val bootHeight = if (player.isCrouching) 8f else 12f
  val bootY = player.y + player.height - bootHeight
  drawRect(Color(0xFF5D2906), Offset(x + 6f + stride, bootY), Size(11f, bootHeight))
  drawRect(Color(0xFF5D2906), Offset(x + 21f - stride, bootY), Size(11f, bootHeight))
  drawRect(Color(0xFF1565C0), Offset(x + 6f, y + 24f), Size(26f, if (player.isCrouching) 12f else 18f))
  drawRect(Color(0xFFFBD000), Offset(x + 10f, y + 27f), Size(4f, 4f))
  drawRect(Color(0xFFFBD000), Offset(x + 24f, y + 27f), Size(4f, 4f))
  drawRect(Color(0xFFE52521), Offset(x + 4f, y + 18f), Size(30f, 8f))
  drawRect(Color(0xFFFFCC99), Offset(x + 8f, y + 6f), Size(22f, 14f))
  val faceX = if (player.facingRight) x + 22f else x + 10f
  drawRect(Color(0xFF3E2723), Offset(faceX, y + 14f), Size(8f, 3f))
  drawRect(Color(0xFF111111), Offset(faceX + (if (player.facingRight) 1f else 2f), y + 9f), Size(3f, 4f))
  drawRect(Color(0xFFE52521), Offset(x + 6f, y), Size(26f, 7f))
  drawRect(
    Color(0xFFE52521),
    Offset(if (player.facingRight) x + 18f else x, y + 4f),
    Size(18f, 4f),
  )
  val gunX = if (player.facingRight) x + 28f else x - 14f
  drawRect(Color(0xFFCFD8DC), Offset(gunX, y + 20f), Size(18f, 6f))
  drawRect(Color(0xFF00E5FF), Offset(if (player.facingRight) gunX + 14f else gunX, y + 20f), Size(4f, 6f))
}

private fun DrawScope.drawEnemy(enemy: EnemyEntity, clock: Float) {
  val flash = enemy.hitFlash > 0f
  when (enemy.kind) {
    EnemyKind.GOOMBA -> {
      val capCol = if (flash) Color.White else Color(0xFF9E4B1A)
      // Mushroom cap
      drawRoundRect(
        color = capCol,
        topLeft = Offset(enemy.x, enemy.y),
        size = Size(enemy.width, 22f),
        cornerRadius = CornerRadius(12f, 12f),
      )
      // Stem
      drawRect(Color(0xFFFFDFBA), Offset(enemy.x + 8f, enemy.y + 20f), Size(enemy.width - 16f, 12f))
      // Angry eyes
      drawRect(Color.Black, Offset(enemy.x + 10f, enemy.y + 9f), Size(5f, 7f))
      drawRect(Color.Black, Offset(enemy.x + enemy.width - 15f, enemy.y + 9f), Size(5f, 7f))
      // Feet waddle
      val waddle = sin(enemy.animTime * 10f) * 3f
      drawRect(Color(0xFF3B1A06), Offset(enemy.x + 4f + waddle, enemy.y + 30f), Size(12f, 8f))
      drawRect(Color(0xFF3B1A06), Offset(enemy.x + enemy.width - 16f - waddle, enemy.y + 30f), Size(12f, 8f))
    }
    EnemyKind.KOOPA -> {
      val shellCol = if (flash) Color.White else Color(0xFF2FA836)
      // Shell
      drawRoundRect(
        color = shellCol,
        topLeft = Offset(enemy.x + 4f, enemy.y + 14f),
        size = Size(enemy.width - 8f, 26f),
        cornerRadius = CornerRadius(10f, 10f),
      )
      // Head
      drawRect(Color(0xFFFBD000), Offset(enemy.x + 10f, enemy.y + 2f), Size(18f, 14f))
      // Sunglasses (Vice Koopa!)
      drawRect(Color(0xFF111111), Offset(enemy.x + 11f, enemy.y + 6f), Size(16f, 5f))
      // Boots
      drawRect(Color(0xFFE52521), Offset(enemy.x + 6f, enemy.y + 40f), Size(12f, 8f))
      drawRect(Color(0xFFE52521), Offset(enemy.x + enemy.width - 18f, enemy.y + 40f), Size(12f, 8f))
    }
    EnemyKind.PARATROOPA -> {
      val shellCol = if (flash) Color.White else Color(0xFFE52521)
      val flap = sin(enemy.animTime * 14f) * 8f
      // White wings
      drawOval(Color.White, Offset(enemy.x - 10f, enemy.y + 4f + flap), Size(18f, 12f))
      drawOval(Color.White, Offset(enemy.x + enemy.width - 8f, enemy.y + 4f + flap), Size(18f, 12f))
      drawRoundRect(
        color = shellCol,
        topLeft = Offset(enemy.x + 4f, enemy.y + 10f),
        size = Size(enemy.width - 8f, 26f),
        cornerRadius = CornerRadius(10f, 10f),
      )
      drawRect(Color(0xFFFBD000), Offset(enemy.x + 12f, enemy.y), Size(18f, 12f))
    }
    EnemyKind.CHOPPER_BOSS -> {
      val hullCol = if (flash) Color.White else Color(0xFF3E2723)
      // Rotor blade
      val rotorW = 36f + abs(sin(clock * 28f)) * 48f
      drawRect(Color(0xFFCFD8DC), Offset(enemy.x + enemy.width * 0.5f - rotorW * 0.5f, enemy.y - 8f), Size(rotorW, 5f))
      // Armored Koopa Chopper body
      drawRoundRect(
        color = hullCol,
        topLeft = Offset(enemy.x, enemy.y + 6f),
        size = Size(enemy.width, enemy.height - 6f),
        cornerRadius = CornerRadius(14f, 14f),
      )
      drawRect(Color(0xFFFF5252), Offset(enemy.x + 14f, enemy.y + 16f), Size(enemy.width - 28f, 12f))
      // HP bar above chopper
      val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
      drawRect(Color(0xAA000000), Offset(enemy.x, enemy.y - 18f), Size(enemy.width, 6f))
      drawRect(Color(0xFFFF3B30), Offset(enemy.x, enemy.y - 18f), Size(enemy.width * hpRatio, 6f))
    }
  }
}

private fun DrawScope.drawPickup(pickup: WorldPickup, clock: Float) {
  val bob = sin(clock * 5f + pickup.id) * 4f
  val px = pickup.x
  val py = pickup.y + bob
  when (pickup.kind) {
    PickupKind.COIN -> {
      val spinW = 6f + abs(cos(clock * 7f + pickup.id)) * 10f
      drawOval(
        color = Color(0xFFFBD000),
        topLeft = Offset(px - spinW, py - 14f),
        size = Size(spinW * 2f, 28f),
      )
      drawOval(
        color = Color(0xFFFFF59D),
        topLeft = Offset(px - spinW * 0.45f, py - 8f),
        size = Size(spinW * 0.9f, 16f),
      )
    }
    PickupKind.SUPER_SHROOM -> {
      drawRoundRect(Color(0xFFE52521), Offset(px - 15f, py - 14f), Size(30f, 18f), CornerRadius(12f, 12f))
      drawCircle(Color.White, 4f, Offset(px - 6f, py - 6f))
      drawCircle(Color.White, 4f, Offset(px + 6f, py - 6f))
      drawRect(Color(0xFFFFE0B2), Offset(px - 9f, py + 4f), Size(18f, 12f))
    }
    PickupKind.CLEAR_WANTED_STAR -> {
      val glow = if (sin(clock * 12f) > 0f) Color(0xFF00E5FF) else Color(0xFFFBD000)
      drawCircle(color = glow, radius = 15f, center = Offset(px, py))
      drawCircle(color = Color.White, radius = 7f, center = Offset(px, py))
    }
    PickupKind.TURBO_NITRO -> {
      drawRoundRect(Color(0xFFFF5E7E), Offset(px - 11f, py - 15f), Size(22f, 30f), CornerRadius(6f, 6f))
      drawRect(Color(0xFF00E5FF), Offset(px - 7f, py - 6f), Size(14f, 12f))
    }
    PickupKind.STAR_POWER -> {
      val pulse = if (sin(clock * 16f) > 0f) Color(0xFFFBD000) else Color(0xFFFF6D00)
      drawCircle(color = pulse, radius = 15f, center = Offset(px, py))
      drawRect(Color.White, Offset(px - 5f, py - 5f), Size(3f, 7f))
      drawRect(Color.White, Offset(px + 2f, py - 5f), Size(3f, 7f))
    }
    PickupKind.ARMOR_SHIELD -> {
      drawCircle(Color(0xFF00E5FF), radius = 16f, center = Offset(px, py))
      drawCircle(Color(0xFF0D47A1), radius = 10f, center = Offset(px, py))
      drawRect(Color.White, Offset(px - 5f, py - 2f), Size(10f, 4f))
      drawRect(Color.White, Offset(px - 2f, py - 5f), Size(4f, 10f))
    }
    PickupKind.FIRE_FLOWER -> {
      drawRect(Color(0xFF43B047), Offset(px - 4f, py + 2f), Size(8f, 13f))
      drawOval(Color(0xFF43B047), Offset(px - 13f, py + 6f), Size(10f, 6f))
      drawOval(Color(0xFF43B047), Offset(px + 3f, py + 6f), Size(10f, 6f))
      drawCircle(Color(0xFFFF5722), radius = 12f, center = Offset(px, py - 6f))
      drawCircle(Color(0xFFFBD000), radius = 6f, center = Offset(px, py - 6f))
    }
    PickupKind.MISSILE_LAUNCHER -> {
      // RPG / Bullet-Bill Launcher crate icon
      drawRoundRect(Color(0xFF2E3440), Offset(px - 17f, py - 12f), Size(34f, 24f), CornerRadius(6f, 6f))
      drawRect(Color(0xFFFF5252), Offset(px - 13f, py - 6f), Size(20f, 12f))
      drawCircle(Color(0xFFFBD000), radius = 5f, center = Offset(px + 9f, py))
      drawRect(Color(0xFF43B047), Offset(px - 17f, py - 12f), Size(34f, 24f), style = Stroke(2f))
    }
    PickupKind.SPREAD_SHOTGUN -> {
      // Twin-barrel golden-orange pixel shotgun pickup
      drawRoundRect(Color(0xFF4E342E), Offset(px - 17f, py - 10f), Size(34f, 20f), CornerRadius(5f, 5f))
      drawRect(Color(0xFFFFB300), Offset(px - 12f, py - 6f), Size(24f, 5f))
      drawRect(Color(0xFFCFD8DC), Offset(px - 8f, py), Size(20f, 4f))
    }
    PickupKind.RAPID_MINIGUN -> {
      // Multi-barrel cyan rotary minigun pickup
      drawRoundRect(Color(0xFF1A237E), Offset(px - 17f, py - 11f), Size(34f, 22f), CornerRadius(6f, 6f))
      drawRect(Color(0xFF00E5FF), Offset(px - 12f, py - 6f), Size(26f, 3f))
      drawRect(Color(0xFF00E5FF), Offset(px - 12f, py - 1f), Size(26f, 3f))
      drawRect(Color(0xFF00E5FF), Offset(px - 12f, py + 4f), Size(26f, 3f))
    }
  }
}

private fun DrawScope.drawProjectile(proj: Projectile, clock: Float) {
  when (proj.style) {
    ProjectileStyle.FIREBALL -> {
      drawCircle(Color(0xFFFF6D00), radius = 9f, center = Offset(proj.x, proj.y))
      drawCircle(Color(0xFFFFEE58), radius = 5f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.STAR_BOLT -> {
      drawCircle(Color(0xFF00E5FF), radius = 11f, center = Offset(proj.x, proj.y))
      drawCircle(Color.White, radius = 5f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.SHELL_ORB -> {
      drawCircle(Color(0xFF43B047), radius = 9f, center = Offset(proj.x, proj.y))
      drawCircle(Color(0xFFFBD000), radius = 4f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.MISSILE -> {
      // Rocket warhead with glowing flame plume
      drawCircle(Color(0xFFFF6D00), radius = 14f, center = Offset(proj.x, proj.y))
      drawCircle(Color(0xFF212121), radius = 9f, center = Offset(proj.x, proj.y))
      drawCircle(Color(0xFFFF1744), radius = 4.5f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.SHOTGUN_PELLET -> {
      drawCircle(Color(0xFFFFB300), radius = 6.5f, center = Offset(proj.x, proj.y))
      drawCircle(Color.White, radius = 2.8f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.MINIGUN_ROUND -> {
      drawCircle(Color(0xFF00E5FF), radius = 6f, center = Offset(proj.x, proj.y))
      drawCircle(Color.White, radius = 3f, center = Offset(proj.x, proj.y))
    }
    ProjectileStyle.ENEMY_BOLT -> {
      drawCircle(Color(0xFFFF1744), radius = 8f, center = Offset(proj.x, proj.y))
      drawCircle(Color(0xFFFFD180), radius = 3.5f, center = Offset(proj.x, proj.y))
    }
  }
}
