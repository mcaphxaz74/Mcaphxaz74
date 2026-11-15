package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.audio.RetroAudioEngine
import com.example.data.VehicleSpec
import com.example.data.WeaponSpec
import com.example.game.GameEngine
import com.example.game.GameOutcome
import com.example.game.LevelGenerator
import com.example.game.WeaponPowerUp
import com.example.game.WorldTheme
import com.example.game.drawMushroomWorld
import com.example.ui.theme.AsphaltCard
import com.example.ui.theme.AsphaltDark
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CoinGold
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.MarioRed
import com.example.ui.theme.PipeGreen
import com.example.ui.theme.PixelDisplayFont
import com.example.ui.theme.StarAmber
import com.example.ui.theme.ViceCyan
import kotlin.math.abs

@Composable
fun GameScreen(
  worldTheme: WorldTheme,
  equippedVehicle: VehicleSpec,
  equippedWeapon: WeaponSpec,
  onRunFinished: (score: Int, coins: Int, enemies: Int, maxWanted: Int, cleared: Boolean) -> Unit,
  onExitToHub: () -> Unit,
) {
  var restartNonce by remember { mutableStateOf(0) }
  val audioEngine = remember(worldTheme) { RetroAudioEngine(worldTheme) }
  var isMuted by rememberSaveable { mutableStateOf(false) }
  val engine =
    remember(worldTheme, equippedVehicle, equippedWeapon, restartNonce, audioEngine) {
      GameEngine(
        worldTheme = worldTheme,
        equippedVehicle = equippedVehicle,
        weaponSpec = equippedWeapon,
        soundListener = audioEngine,
      )
    }

  var canvasPixelSize by remember { mutableStateOf(IntSize(1080, 720)) }
  var animClockSec by remember { mutableFloatStateOf(0f) }
  var resultRecorded by remember(restartNonce) { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(audioEngine) {
    onDispose { audioEngine.release() }
  }

  DisposableEffect(engine) {
    onDispose { engine.clearInputs() }
  }

  LaunchedEffect(isMuted, audioEngine) {
    audioEngine.setMuted(isMuted)
  }

  LaunchedEffect(engine) {
    runCatching { focusRequester.requestFocus() }
  }

  BackHandler {
    if (engine.hudSnapshot.outcome == GameOutcome.PLAYING) {
      engine.togglePause()
    } else {
      onExitToHub()
    }
  }

  // 60fps Compose frame loop
  LaunchedEffect(engine) {
    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameTimeNanos ->
        if (lastNanos == 0L) {
          lastNanos = frameTimeNanos
        }
        val dt = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastNanos = frameTimeNanos
        animClockSec += dt
        val h = canvasPixelSize.height.coerceAtLeast(1).toFloat()
        val w = canvasPixelSize.width.coerceAtLeast(1).toFloat()
        val viewportWorldWidth = (w / h) * LevelGenerator.WORLD_HEIGHT
        engine.step(dt, viewportWorldWidth)
      }
    }
  }

  val hud = engine.hudSnapshot
  val latestOutcome by rememberUpdatedState(hud.outcome)

  DisposableEffect(lifecycleOwner, audioEngine) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
          engine.clearInputs()
          audioEngine.setPaused(true)
        }
        Lifecycle.Event.ON_RESUME -> {
          audioEngine.setPaused(latestOutcome != GameOutcome.PLAYING)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(hud.outcome, hud.starSecondsLeft, audioEngine) {
    audioEngine.setPaused(hud.outcome != GameOutcome.PLAYING)
    audioEngine.setStarPower(hud.starSecondsLeft > 0)
  }

  LaunchedEffect(hud.outcome, resultRecorded) {
    if (!resultRecorded && (hud.outcome == GameOutcome.VICTORY || hud.outcome == GameOutcome.BUSTED)) {
      resultRecorded = true
      onRunFinished(
        hud.score,
        hud.coins,
        hud.enemiesDefeated,
        hud.maxWantedStarsReached,
        hud.outcome == GameOutcome.VICTORY,
      )
    }
  }

  val textMeasurer = rememberTextMeasurer()
  val playerSprite = ImageBitmap.imageResource(R.drawable.sprite_player_hero)
  val questionTextLayout =
    remember(textMeasurer) {
      textMeasurer.measure(
        text = "?",
        style =
          TextStyle(
            fontFamily = PixelDisplayFont,
            fontSize = 20.sp,
            color = Color(0xFF5A2B00),
          ),
      )
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(AsphaltDark)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { keyEvent ->
          val isDown = keyEvent.type == KeyEventType.KeyDown
          val isUp = keyEvent.type == KeyEventType.KeyUp
          if (!isDown && !isUp) return@onPreviewKeyEvent false
          when (keyEvent.key) {
            Key.DirectionLeft, Key.A -> {
              engine.inputLeft = isDown
              true
            }
            Key.DirectionRight, Key.D -> {
              engine.inputRight = isDown
              true
            }
            Key.DirectionUp, Key.W -> {
              engine.inputUp = isDown
              if (isDown) engine.queueJump()
              true
            }
            Key.DirectionDown, Key.S -> {
              engine.inputDown = isDown
              true
            }
            Key.Spacebar, Key.F -> {
              engine.inputFireHeld = isDown
              true
            }
            Key.E -> {
              if (isDown) engine.toggleEnterExitCar()
              true
            }
            Key.P, Key.Escape -> {
              if (isDown) engine.togglePause()
              true
            }
            else -> false
          }
        }
  ) {
    // Top Retro Arcade HUD
    GameTopHud(
      hud = hud,
      isMuted = isMuted,
      onMuteToggle = { isMuted = !isMuted },
      onPauseToggle = { engine.togglePause() },
      modifier = Modifier.fillMaxWidth().background(AsphaltDark).statusBarsPadding(),
    )

    // Center Full-Bleed Canvas Viewport (Tap anywhere to snipe from far away!)
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Canvas(
        modifier =
          Modifier.fillMaxSize()
            .onSizeChanged { canvasPixelSize = it }
            .pointerInput(engine) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val h = size.height.coerceAtLeast(1).toFloat()
                val scale = h / LevelGenerator.WORLD_HEIGHT
                val worldX = engine.cameraX + down.position.x / scale
                val worldY = down.position.y / scale
                engine.fireAtScreenWorldPoint(worldX, worldY)
              }
            }
      ) {
        // Read frameTick so Canvas invalidates smoothly every frame
        engine.frameTick
        drawMushroomWorld(engine, textMeasurer, animClockSec, questionTextLayout, playerSprite)
      }

      // Active Power-Up Status Pills
      if (hud.outcome == GameOutcome.PLAYING) {
        ActivePowerUpBadges(
          hud = hud,
          modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp),
        )
      }

      // Transient Event Banner
      if (hud.statusBanner != null && hud.outcome == GameOutcome.PLAYING) {
        Box(
          modifier =
            Modifier.align(Alignment.TopCenter)
              .padding(top = 10.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(AsphaltDark.copy(alpha = 0.86f))
              .border(2.dp, CoinGold, RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Text(
            text = hud.statusBanner,
            style = MaterialTheme.typography.labelLarge,
            color = CoinGold,
          )
        }
      }

      // Tap-to-snipe tip pill
      if (hud.outcome == GameOutcome.PLAYING) {
        Box(
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(bottom = 6.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(AsphaltDark.copy(alpha = 0.65f))
              .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
          Text(
            text =
              if (hud.hasTargetLock) stringResource(R.string.hud_target_locked)
              else stringResource(R.string.hud_tap_to_snipe),
            style = MaterialTheme.typography.labelSmall,
            color = if (hud.hasTargetLock) ViceCyan else Color.White.copy(alpha = 0.85f),
          )
        }
      }

      // Pause / Victory / Busted Overlay
      if (hud.outcome != GameOutcome.PLAYING) {
        GameOutcomeOverlay(
          hud = hud,
          onResume = { engine.togglePause() },
          onRestart = { restartNonce++ },
          onExit = onExitToHub,
        )
      }
    }

    // Bottom Multi-Touch 4-Way D-Pad & Action Deck (isolated from 60ms score/progress updates)
    ArcadeControlDeck(
      engineKey = engine,
      inCar = hud.inCar,
      canEnterCar = hud.canEnterCar,
      nearPoliceCar = hud.nearPoliceCar,
      onDirectionsChanged = { left, right, up, down ->
        engine.inputLeft = left
        engine.inputRight = right
        engine.inputUp = up
        engine.inputDown = down
      },
      onJumpHoldChanged = { pressed ->
        engine.inputJumpHeld = pressed
        if (pressed) engine.queueJump()
      },
      onFireChanged = { engine.inputFireHeld = it },
      onCarAction = { engine.toggleEnterExitCar() },
      modifier =
        Modifier.fillMaxWidth()
          .background(AsphaltSurface)
          .navigationBarsPadding()
          .padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

@Composable
private fun ActivePowerUpBadges(
  hud: com.example.game.HudSnapshot,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (hud.activeWeaponPowerUp != WeaponPowerUp.NONE && hud.weaponPowerUpSecondsLeft > 0) {
      val badgeColor =
        when (hud.activeWeaponPowerUp) {
          WeaponPowerUp.MISSILE_LAUNCHER -> DangerCrimson
          WeaponPowerUp.SPREAD_SHOTGUN -> StarAmber
          WeaponPowerUp.RAPID_MINIGUN -> ViceCyan
          WeaponPowerUp.FIRE_OVERDRIVE -> MarioRed
          WeaponPowerUp.NONE -> Color.White
        }
      PowerUpPill(
        text = "${hud.activeWeaponPowerUp.label} ${hud.weaponPowerUpSecondsLeft}s",
        accent = badgeColor,
      )
    }
    if (hud.starSecondsLeft > 0) {
      PowerUpPill(text = "STAR ${hud.starSecondsLeft}s", accent = CoinGold)
    }
    if (hud.shieldCharges > 0) {
      PowerUpPill(text = "SHIELD x${hud.shieldCharges}", accent = ViceCyan)
    }
    if (hud.nitroSecondsLeft > 0) {
      PowerUpPill(text = "NITRO ${hud.nitroSecondsLeft}s", accent = PipeGreen)
    }
  }
}

@Composable
private fun PowerUpPill(text: String, accent: Color) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(AsphaltDark.copy(alpha = 0.84f))
        .border(1.5.dp, accent, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accent,
    )
  }
}

@Composable
private fun GameTopHud(
  hud: com.example.game.HudSnapshot,
  isMuted: Boolean,
  onMuteToggle: () -> Unit,
  onPauseToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column {
        Text(
          text = hud.worldTheme.title.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = ViceCyan,
        )
        Text(
          text = "SCORE ${hud.score}",
          style = MaterialTheme.typography.labelLarge,
          color = Color.White,
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "COINS ${hud.coins}",
          style = MaterialTheme.typography.labelLarge,
          color = CoinGold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          for (starIdx in 1..5) {
            Icon(
              imageVector =
                if (starIdx <= hud.wantedStars) Icons.Default.Star else Icons.Default.StarOutline,
              contentDescription = null,
              tint = if (starIdx <= hud.wantedStars) CoinGold else Color.Gray,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        // Health Hearts + Car Armor
        Column(horizontalAlignment = Alignment.End) {
          Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            for (h in 1..hud.maxHp) {
              Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = if (h <= hud.hp) MarioRed else Color(0xFF3A4157),
                modifier = Modifier.size(13.dp),
              )
            }
          }
          if (hud.carHp != null && hud.carMaxHp != null) {
            Text(
              text = "${hud.carName ?: "CAR"} ${hud.carHp}/${hud.carMaxHp}",
              style = MaterialTheme.typography.labelSmall,
              color = PipeGreen,
            )
          } else {
            val activeWeaponLabel =
              if (hud.activeWeaponPowerUp != WeaponPowerUp.NONE) hud.activeWeaponPowerUp.label
              else hud.weaponSpec.displayName
            Text(
              text = activeWeaponLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (hud.activeWeaponPowerUp != WeaponPowerUp.NONE) CoinGold else Color.LightGray,
            )
          }
        }
        Spacer(modifier = Modifier.width(2.dp))
        IconButton(
          onClick = onMuteToggle,
          modifier = Modifier.size(48.dp).testTag("mute_audio_button"),
        ) {
          Icon(
            imageVector =
              if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription =
              if (isMuted) stringResource(R.string.unmute_audio)
              else stringResource(R.string.mute_audio),
            tint = if (isMuted) Color.Gray else CoinGold,
          )
        }
        IconButton(
          onClick = onPauseToggle,
          modifier = Modifier.size(48.dp).testTag("pause_game_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Pause,
            contentDescription = stringResource(R.string.pause_game),
            tint = Color.White,
          )
        }
      }
    }

    LinearProgressIndicator(
      progress = { hud.progressFraction },
      color = PipeGreen,
      trackColor = AsphaltCard,
      modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
    )
  }
}

@Composable
private fun ArcadeControlDeck(
  engineKey: Any,
  inCar: Boolean,
  canEnterCar: Boolean,
  nearPoliceCar: Boolean,
  onDirectionsChanged: (left: Boolean, right: Boolean, up: Boolean, down: Boolean) -> Unit,
  onJumpHoldChanged: (Boolean) -> Unit,
  onFireChanged: (Boolean) -> Unit,
  onCarAction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left 4-Way Arcade D-Pad (Supports instant taps, thumb sliding, and wide diagonals!)
    FourWayDPad(
      engineKey = engineKey,
      onDirectionsChanged = onDirectionsChanged,
    )

    // Center Enter/Exit/Hijack Car Action
    val carEnabled = inCar || canEnterCar
    val carBg =
      when {
        inCar -> MarioRed
        nearPoliceCar -> ViceCyan
        canEnterCar -> CoinGold
        else -> AsphaltCard.copy(alpha = 0.5f)
      }
    val carTextColor = if (!inCar && canEnterCar) AsphaltDark else Color.White
    Button(
      onClick = onCarAction,
      enabled = carEnabled,
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = carBg,
          contentColor = carTextColor,
          disabledContainerColor = AsphaltCard.copy(alpha = 0.45f),
          disabledContentColor = Color.Gray,
        ),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.height(54.dp).testTag("car_action_button"),
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
          text =
            when {
              inCar -> "EXIT"
              nearPoliceCar -> "HIJACK"
              canEnterCar -> "DRIVE"
              else -> "CAR"
            },
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }

    // Right Fire + Jump / Hydraulic Hop
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      HoldableControlPad(
        engineKey = engineKey,
        label = "FIRE",
        bgColor = MarioRed,
        borderColor = CoinGold,
        sizeDp = 60.dp,
        onHoldChanged = onFireChanged,
        testTag = "fire_button",
      ) {
        Icon(
          imageVector = Icons.Default.GpsFixed,
          contentDescription = stringResource(R.string.control_fire),
          tint = CoinGold,
          modifier = Modifier.size(24.dp),
        )
      }
      HoldableControlPad(
        engineKey = engineKey,
        label = if (inCar) "HOP" else "JUMP",
        bgColor = PipeGreen,
        borderColor = Color.White.copy(alpha = 0.65f),
        sizeDp = 64.dp,
        onHoldChanged = onJumpHoldChanged,
        testTag = "jump_button",
      ) {
        Icon(
          imageVector = Icons.Default.ArrowUpward,
          contentDescription = stringResource(R.string.control_jump),
          tint = Color.White,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

@Composable
private fun FourWayDPad(
  engineKey: Any,
  onDirectionsChanged: (left: Boolean, right: Boolean, up: Boolean, down: Boolean) -> Unit,
) {
  val latestOnDirections by rememberUpdatedState(onDirectionsChanged)
  val haptics = LocalHapticFeedback.current

  var activeLeft by remember(engineKey) { mutableStateOf(false) }
  var activeRight by remember(engineKey) { mutableStateOf(false) }
  var activeUp by remember(engineKey) { mutableStateOf(false) }
  var activeDown by remember(engineKey) { mutableStateOf(false) }

  val upDesc = stringResource(R.string.control_up)
  val downDesc = stringResource(R.string.control_down)
  val leftDesc = stringResource(R.string.control_left)
  val rightDesc = stringResource(R.string.control_right)

  Box(
    modifier =
      Modifier.size(128.dp)
        .clip(CircleShape)
        .background(AsphaltDark.copy(alpha = 0.85f))
        .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        .testTag("four_way_dpad")
        .pointerInput(engineKey) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              val pressed = event.changes.filter { it.pressed }
              var nextL = false
              var nextR = false
              var nextU = false
              var nextD = false

              if (pressed.isNotEmpty()) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val deadzone = (size.width.coerceAtMost(size.height) * 0.5f) * 0.11f

                for (change in pressed) {
                  change.consume()
                  val dx = change.position.x - cx
                  val dy = change.position.y - cy
                  val adx = abs(dx)
                  val ady = abs(dy)
                  // Generous 0.28f diagonal factor so sliding Up+Right easily jumps & moves forward
                  if (dx < -deadzone && adx >= ady * 0.28f) nextL = true
                  if (dx > deadzone && adx >= ady * 0.28f) nextR = true
                  if (dy < -deadzone && ady >= adx * 0.28f) nextU = true
                  if (dy > deadzone && ady >= adx * 0.28f) nextD = true
                }
              }

              if (nextL != activeLeft || nextR != activeRight || nextU != activeUp || nextD != activeDown) {
                if ((nextL && !activeLeft) || (nextR && !activeRight) || (nextU && !activeUp) || (nextD && !activeDown)) {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                activeLeft = nextL
                activeRight = nextR
                activeUp = nextU
                activeDown = nextD
                latestOnDirections(nextL, nextR, nextU, nextD)
              }
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    // UP Arm
    DpadArm(
      active = activeUp,
      activeColor = PipeGreen,
      icon = Icons.Default.ArrowUpward,
      contentDescription = upDesc,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).testTag("dpad_up"),
    )
    // DOWN Arm
    DpadArm(
      active = activeDown,
      activeColor = CoinGold,
      icon = Icons.Default.ArrowDownward,
      contentDescription = downDesc,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).testTag("dpad_down"),
    )
    // LEFT Arm
    DpadArm(
      active = activeLeft,
      activeColor = ViceCyan,
      icon = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = leftDesc,
      modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp).testTag("move_left_button"),
    )
    // RIGHT Arm
    DpadArm(
      active = activeRight,
      activeColor = ViceCyan,
      icon = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = rightDesc,
      modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).testTag("move_right_button"),
    )
    // Center Pivot Cap
    Box(
      modifier =
        Modifier.size(26.dp)
          .clip(CircleShape)
          .background(AsphaltSurface)
          .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
    )
  }
}

@Composable
private fun DpadArm(
  active: Boolean,
  activeColor: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(48.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (active) activeColor.copy(alpha = 0.88f) else AsphaltCard)
        .border(
          width = if (active) 2.dp else 1.5.dp,
          color = if (active) Color.White else Color.White.copy(alpha = 0.22f),
          shape = RoundedCornerShape(12.dp),
        )
        .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (active) AsphaltDark else Color.White,
      modifier = Modifier.size(22.dp),
    )
  }
}

@Composable
private fun HoldableControlPad(
  engineKey: Any,
  label: String,
  bgColor: Color,
  borderColor: Color,
  sizeDp: androidx.compose.ui.unit.Dp,
  onHoldChanged: (Boolean) -> Unit,
  testTag: String,
  iconContent: @Composable () -> Unit,
) {
  val latestOnHold by rememberUpdatedState(onHoldChanged)
  val haptics = LocalHapticFeedback.current
  var pressed by remember(engineKey) { mutableStateOf(false) }

  Box(
    modifier =
      Modifier.size(sizeDp)
        .clip(CircleShape)
        .background(if (pressed) bgColor.copy(alpha = 0.72f) else bgColor)
        .border(2.dp, if (pressed) Color.White else borderColor, CircleShape)
        .pointerInput(engineKey) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              var isDown = false
              for (change in event.changes) {
                if (change.pressed) {
                  change.consume()
                  isDown = true
                }
              }
              if (isDown != pressed) {
                if (isDown) {
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                pressed = isDown
                latestOnHold(isDown)
              }
            }
          }
        }
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      iconContent()
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
      )
    }
  }
}

@Composable
private fun GameOutcomeOverlay(
  hud: com.example.game.HudSnapshot,
  onResume: () -> Unit,
  onRestart: () -> Unit,
  onExit: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().background(AsphaltDark.copy(alpha = 0.82f)).padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
      modifier = Modifier.fillMaxWidth().border(3.dp, CoinGold, RoundedCornerShape(20.dp)),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val title =
          when (hud.outcome) {
            GameOutcome.PAUSED -> "PAUSED"
            GameOutcome.VICTORY -> "COURSE CLEAR!"
            GameOutcome.BUSTED -> "BUSTED!"
            GameOutcome.PLAYING -> ""
          }
        val titleColor =
          when (hud.outcome) {
            GameOutcome.VICTORY -> CoinGold
            GameOutcome.BUSTED -> DangerCrimson
            else -> Color.White
          }

        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          color = titleColor,
          textAlign = TextAlign.Center,
        )
        Text(
          text = hud.worldTheme.title,
          style = MaterialTheme.typography.titleMedium,
          color = ViceCyan,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          StatChip("SCORE", "${hud.score}", Color.White)
          StatChip("COINS", "+${hud.coins}", CoinGold)
          StatChip("KO'S", "${hud.enemiesDefeated}", PipeGreen)
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (hud.outcome == GameOutcome.PAUSED) {
          Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(containerColor = PipeGreen),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("resume_button"),
          ) {
            Text(text = stringResource(R.string.resume_game), style = MaterialTheme.typography.labelLarge)
          }
        }
        Button(
          onClick = onRestart,
          colors = ButtonDefaults.buttonColors(containerColor = CoinGold, contentColor = AsphaltDark),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("replay_button"),
        ) {
          Text(
            text =
              if (hud.outcome == GameOutcome.PAUSED) stringResource(R.string.restart_level)
              else stringResource(R.string.play_again),
            style = MaterialTheme.typography.labelLarge,
          )
        }
        OutlinedButton(
          onClick = onExit,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("exit_to_hub_button"),
        ) {
          Text(text = stringResource(R.string.back_to_hub), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
    Text(text = value, style = MaterialTheme.typography.titleLarge, color = valueColor)
  }
}
