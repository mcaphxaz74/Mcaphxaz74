package com.example.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.audio.GameSoundListener
import com.example.audio.NoOpGameSoundListener
import com.example.data.VehicleSpec
import com.example.data.WeaponSpec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameEngine(
  val worldTheme: WorldTheme,
  private val equippedVehicle: VehicleSpec,
  private val weaponSpec: WeaponSpec,
  private val soundListener: GameSoundListener = NoOpGameSoundListener,
) {
  private val level = LevelGenerator.buildLevel(worldTheme, equippedVehicle)

  val groundSegments: List<GroundSegment> = level.groundSegments
  val pipes: List<WarpPipe> = level.pipes
  val blocks: MutableList<BlockTile> = level.blocks
  val cars: MutableList<WorldCar> = level.cars
  val enemies: MutableList<EnemyEntity> = level.enemies
  val pickups: MutableList<WorldPickup> = level.pickups
  val projectiles: MutableList<Projectile> = mutableListOf()
  val particles: MutableList<Particle> = mutableListOf()

  val player = PlayerState()

  var cameraX: Float = 0f
    private set

  var score: Int = 0
    private set

  var coins: Int = 0
    private set

  var enemiesDefeated: Int = 0
    private set

  var wantedHeat: Float = 0f
    private set

  var wantedStars: Int = 0
    private set

  var maxWantedStarsReached: Int = 0
    private set

  var outcome: GameOutcome = GameOutcome.PLAYING
    private set

  var lockedEnemyId: Int? = null
    private set

  var lockedTargetX: Float? = null
    private set

  var lockedTargetY: Float? = null
    private set

  var manualAimX: Float? = null
    private set

  var manualAimY: Float? = null
    private set

  private var manualAimTimer: Float = 0f

  // Input flags updated by multi-touch 4-way D-pad & action controls
  var inputLeft: Boolean = false
  var inputRight: Boolean = false
  var inputUp: Boolean = false
  var inputDown: Boolean = false
  var inputJumpHeld: Boolean = false
  var inputFireHeld: Boolean = false
  private var prevInputUp: Boolean = false
  private var prevInputJump: Boolean = false
  private var jumpBufferTimer: Float = 0f
  private var coyoteTimer: Float = 0f
  private var fireCooldownRemaining: Float = 0f
  private var policeSpawnTimer: Float = 5.5f
  private var nextEntityId: Int = 1000
  private var statusBanner: String? = "HOP IN YOUR CAR & BLAST PATROLS!"
  private var statusBannerTimer: Float = 3.2f

  // Frame counter ticks Compose Canvas redraw at 60fps
  var frameTick by mutableIntStateOf(0)
    private set

  var hudSnapshot by mutableStateOf(buildHudSnapshot())
    private set

  private var hudSyncTimer: Float = 0f

  fun queueJump() {
    jumpBufferTimer = 0.20f
  }

  fun clearInputs() {
    inputLeft = false
    inputRight = false
    inputUp = false
    inputDown = false
    inputJumpHeld = false
    inputFireHeld = false
    prevInputUp = false
    prevInputJump = false
    jumpBufferTimer = 0f
  }

  fun togglePause() {
    if (outcome == GameOutcome.PLAYING) {
      outcome = GameOutcome.PAUSED
      hudSnapshot = buildHudSnapshot()
    } else if (outcome == GameOutcome.PAUSED) {
      outcome = GameOutcome.PLAYING
      hudSnapshot = buildHudSnapshot()
    }
  }

  fun toggleEnterExitCar() {
    if (outcome != GameOutcome.PLAYING) return
    val currentCarId = player.drivenCarId
    if (currentCarId != null) {
      val car = cars.firstOrNull { it.id == currentCarId }
      if (car != null) {
        car.isOccupiedByPlayer = false
        player.drivenCarId = null
        player.x = car.x + car.width * 0.35f
        player.y = car.y - player.height - 4f
        player.vy = -340f
        player.vx = car.vx * 0.35f
        player.onGround = false
        showBanner("EXITED ${car.spec.displayName.uppercase()}")
        hudSnapshot = buildHudSnapshot()
      }
      return
    }

    val nearest = findNearestEnterableCar() ?: return
    if (nearest.hasPoliceDriver) {
      nearest.hasPoliceDriver = false
      addWantedHeat(1.4f)
      score += 250
      spawnFloatingText(nearest.x + 40f, nearest.y - 20f, "+250 HIJACK!", 0xFFFFD600L)
      showBanner("TOAD CRUISER COMMANDEERED!")
    } else {
      showBanner("DRIVING ${nearest.spec.displayName.uppercase()}!")
    }
    nearest.isOccupiedByPlayer = true
    player.drivenCarId = nearest.id
    player.facingRight = nearest.facingRight
    soundListener.onCarEnter()
    hudSnapshot = buildHudSnapshot()
  }

  fun fireAtScreenWorldPoint(worldX: Float, worldY: Float) {
    if (outcome != GameOutcome.PLAYING) return
    manualAimX = worldX
    manualAimY = worldY
    manualAimTimer = 0.45f
    val originX = currentPlayerCenterX()
    player.facingRight = worldX >= originX
    val driven = cars.firstOrNull { it.id == player.drivenCarId }
    if (driven != null) driven.facingRight = player.facingRight
    tryShoot(explicitTargetX = worldX, explicitTargetY = worldY)
  }

  fun step(dtRaw: Float, viewportWorldWidth: Float) {
    if (outcome != GameOutcome.PLAYING) return
    val dt = dtRaw.coerceIn(0.001f, 0.04f)
    val safeViewportW = if (viewportWorldWidth.isFinite() && viewportWorldWidth > 100f) viewportWorldWidth else 1280f

    if (statusBannerTimer > 0f) {
      statusBannerTimer -= dt
      if (statusBannerTimer <= 0f) statusBanner = null
    }
    if (manualAimTimer > 0f) {
      manualAimTimer -= dt
      if (manualAimTimer <= 0f) {
        manualAimX = null
        manualAimY = null
      }
    }
    if (player.invulnTimer > 0f) player.invulnTimer -= dt
    if (player.turboTimer > 0f) player.turboTimer -= dt
    if (player.starPowerTimer > 0f) {
      player.starPowerTimer = (player.starPowerTimer - dt).coerceAtLeast(0f)
    }
    if (player.shieldTimer > 0f) {
      player.shieldTimer = (player.shieldTimer - dt).coerceAtLeast(0f)
      if (player.shieldTimer <= 0f) player.shieldCharges = 0
    }
    if (player.weaponPowerUpTimer > 0f) {
      player.weaponPowerUpTimer = (player.weaponPowerUpTimer - dt).coerceAtLeast(0f)
      if (player.weaponPowerUpTimer <= 0f) {
        player.activeWeaponPowerUp = WeaponPowerUp.NONE
      }
    }
    if (fireCooldownRemaining > 0f) fireCooldownRemaining -= dt
    if (jumpBufferTimer > 0f) jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)
    if (coyoteTimer > 0f) coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)

    // Rising-edge or held UP / JUMP button queues jump cleanly while moving forward
    val jumpHeldNow = inputUp || inputJumpHeld
    if ((inputUp && !prevInputUp) || (inputJumpHeld && !prevInputJump)) {
      queueJump()
    } else if (jumpHeldNow && isGroundedNow()) {
      queueJump()
    }
    prevInputUp = inputUp
    prevInputJump = inputJumpHeld

    updatePlayerOrDrivenCar(dt)
    updateUnoccupiedCars(dt)
    updateEnemies(dt)
    updateLongRangeTargetLock(safeViewportW)

    if (inputFireHeld && fireCooldownRemaining <= 0f) {
      tryShoot(explicitTargetX = null, explicitTargetY = null)
    }

    updateProjectiles(dt, safeViewportW)
    updatePickups(dt)
    updateParticles(dt)
    updateWantedSystem(dt, safeViewportW)

    // Smooth camera follow with forward look-ahead
    val focusX = currentPlayerCenterX() + if (player.facingRight) 140f else -80f
    val maxCamX = (LevelGenerator.WORLD_LENGTH - safeViewportW).coerceAtLeast(0f)
    val targetCamX = (focusX - safeViewportW * 0.36f).coerceIn(0f, maxCamX)
    cameraX += (targetCamX - cameraX) * (dt * 7.5f).coerceAtMost(1f)

    // Victory check at flagpole
    if (currentPlayerCenterX() >= LevelGenerator.FLAGPOLE_X - 18f) {
      score += 1000 + player.hp * 200
      outcome = GameOutcome.VICTORY
      showBanner("COURSE CLEAR!")
      soundListener.onVictory()
      hudSnapshot = buildHudSnapshot()
    }

    hudSyncTimer += dt
    if (hudSyncTimer >= 0.06f) {
      hudSyncTimer = 0f
      hudSnapshot = buildHudSnapshot()
    }
    frameTick++
  }

  private fun isGroundedNow(): Boolean {
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    return car?.onGround ?: player.onGround
  }

  private fun updatePlayerOrDrivenCar(dt: Float) {
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    if (car != null) {
      player.isCrouching = false
      if (car.onGround) {
        coyoteTimer = 0.16f
      }
      val dir =
        when {
          inputRight && !inputLeft -> 1f
          inputLeft && !inputRight -> -1f
          else -> 0f
        }
      val turboMul =
        when {
          player.starPowerTimer > 0f && player.turboTimer > 0f -> 1.5f
          player.turboTimer > 0f -> 1.35f
          player.starPowerTimer > 0f -> 1.28f
          else -> 1f
        }
      val maxSpd = car.spec.maxSpeed * turboMul
      val accel = car.spec.acceleration * turboMul * (if (car.onGround) 1f else 1.2f)

      if (dir != 0f) {
        // Instant reverse snap boost if switching direction on ground so driving feels snappy
        val turnMul = if (car.vx * dir < 0f) 2.2f else 1f
        car.vx += dir * accel * turnMul * dt
        car.facingRight = dir > 0f
        player.facingRight = car.facingRight
        if (car.onGround && Random.nextFloat() < 0.35f) {
          spawnSmoke(
            x = if (car.facingRight) car.x + 12f else car.x + car.width - 12f,
            y = car.y + car.height - 6f,
          )
        }
      } else {
        val brakeDrag = if (inputDown) 2600f * dt else 780f * dt
        if (abs(car.vx) <= brakeDrag) car.vx = 0f else car.vx -= kotlin.math.sign(car.vx) * brakeDrag
      }
      if (player.turboTimer > 0f && abs(car.vx) > 120f && Random.nextFloat() < 0.45f) {
        spawnSparks(
          if (car.facingRight) car.x + 4f else car.x + car.width - 4f,
          car.y + car.height - 14f,
          0xFFFF6D00L,
          2,
        )
      }
      car.vx = car.vx.coerceIn(-maxSpd, maxSpd)

      if (jumpBufferTimer > 0f && (car.onGround || coyoteTimer > 0f)) {
        jumpBufferTimer = 0f
        coyoteTimer = 0f
        car.vy = car.spec.jumpImpulse - 25f
        if (dir != 0f && abs(car.vx) < 220f) {
          car.vx = dir * 240f
        }
        car.onGround = false
        soundListener.onJump()
        spawnSmoke(car.x + car.width * 0.5f, car.y + car.height)
      }

      // Down while airborne performs fast-fall slam
      if (inputDown && !car.onGround && car.vy < 620f) {
        car.vy = (car.vy + 2600f * dt).coerceAtMost(660f)
      } else {
        car.vy += 1180f * dt
      }

      // Apply vertical displacement BEFORE horizontal pipe resolution so jumping near pipes never gets trapped
      val prevBottom = car.y + car.height
      car.y += car.vy * dt
      car.x = (car.x + car.vx * dt).coerceIn(12f, LevelGenerator.WORLD_LENGTH - car.width - 20f)
      resolveHorizontalPipeCollision(car)

      car.onGround = false

      // Ground collision
      if (hasGroundBelow(car.x + 12f, car.x + car.width - 12f) && car.y + car.height >= LevelGenerator.GROUND_Y && prevBottom <= LevelGenerator.GROUND_Y + 32f) {
        car.y = LevelGenerator.GROUND_Y - car.height
        car.vy = 0f
        car.onGround = true
      }

      // Pipe top & ramp physics
      for (pipe in pipes) {
        val pipeTop = LevelGenerator.GROUND_Y - pipe.height
        if (car.x + car.width > pipe.x + 4f && car.x < pipe.x + pipe.width - 4f) {
          if (car.vy >= 0f && prevBottom <= pipeTop + 26f && car.y + car.height >= pipeTop) {
            car.y = pipeTop - car.height
            car.vy = if (pipe.isRamp && abs(car.vx) > 240f) -390f else 0f
            car.onGround = !(pipe.isRamp && abs(car.vx) > 240f)
          }
        }
      }

      // Solid step blocks landing
      for (block in blocks) {
        if (block.broken) continue
        if (car.x + car.width > block.x + 4f && car.x < block.x + block.size - 4f) {
          if (car.vy >= 0f && prevBottom <= block.y + 22f && car.y + car.height >= block.y) {
            car.y = block.y - car.height
            car.vy = 0f
            car.onGround = true
          }
        }
      }

      car.wheelSpin += car.vx * dt * 0.08f
      player.x = car.x + car.width * 0.35f
      player.y = car.y - 6f

      // Pit fall recovery with forgiving 1 HP penalty
      if (car.y > LevelGenerator.WORLD_HEIGHT + 90f) {
        car.wrecked = true
        car.isOccupiedByPlayer = false
        player.drivenCarId = null
        damagePlayer(1)
        if (outcome == GameOutcome.PLAYING) {
          val safeX = findNearestSafeGroundX(car.x)
          player.x = safeX
          player.y = LevelGenerator.GROUND_Y - player.height - 80f
          player.vy = 0f
          showBanner("CAR LOST IN PIT! BAILED OUT!")
        }
      }
      return
    }

    // On-foot Mario platformer physics
    if (player.onGround) {
      coyoteTimer = 0.16f
    }
    player.isCrouching = inputDown && player.onGround

    val dir =
      when {
        inputRight && !inputLeft -> 1f
        inputLeft && !inputRight -> -1f
        else -> 0f
      }
    val walkSpeed =
      when {
        player.isCrouching -> 170f
        player.starPowerTimer > 0f -> 460f
        player.turboTimer > 0f -> 440f
        else -> 360f
      }
    if (dir != 0f) {
      val accelRate = if (player.onGround) 3400f else 2650f
      val targetVx = dir * walkSpeed
      val diff = targetVx - player.vx
      val stepVx = accelRate * dt
      player.vx = if (abs(diff) <= stepVx) targetVx else player.vx + kotlin.math.sign(diff) * stepVx
      player.facingRight = dir > 0f
      player.walkAnim += dt * 12f
    } else {
      val friction = if (player.onGround) (if (inputDown) 2600f else 1600f) else 240f
      val decel = friction * dt
      if (abs(player.vx) <= decel) player.vx = 0f else player.vx -= kotlin.math.sign(player.vx) * decel
    }

    if (jumpBufferTimer > 0f && (player.onGround || coyoteTimer > 0f)) {
      jumpBufferTimer = 0f
      coyoteTimer = 0f
      player.vy = -710f
      // Instant forward/back air burst when jumping while pressing a direction so jump + move forward feels crisp
      if (dir != 0f && abs(player.vx) < 210f) {
        player.vx = dir * 210f
      }
      player.onGround = false
      player.isCrouching = false
      soundListener.onJump()
    }

    if (inputDown && !player.onGround && player.vy < 680f) {
      player.vy = (player.vy + 2900f * dt).coerceAtMost(720f)
    } else {
      player.vy += 1380f * dt
    }

    // Apply vertical displacement before horizontal pipe check so standing next to a pipe and jumping right clears cleanly
    val prevBottom = player.y + player.height
    val prevTop = player.y
    player.y += player.vy * dt
    player.x = (player.x + player.vx * dt).coerceIn(12f, LevelGenerator.WORLD_LENGTH - player.width - 20f)

    // Horizontal pipe collision (preserve upward jump velocity so player never snags while jumping over)
    for (pipe in pipes) {
      val pipeTop = LevelGenerator.GROUND_Y - pipe.height
      if (player.y + player.height > pipeTop + 14f && player.x + player.width > pipe.x && player.x < pipe.x + pipe.width) {
        if (player.x + player.width * 0.5f > pipe.x + 8f && player.x + player.width * 0.5f < pipe.x + pipe.width - 8f && player.y + player.height > pipeTop + 22f) {
          player.y = pipeTop - player.height
          player.vy = 0f
          player.onGround = true
        } else {
          if (player.vx > 0f) player.x = pipe.x - player.width
          else if (player.vx < 0f) player.x = pipe.x + pipe.width
          if (player.vy >= 0f) player.vx = 0f
        }
      }
    }

    player.onGround = false

    if (hasGroundBelow(player.x + 6f, player.x + player.width - 6f) && player.y + player.height >= LevelGenerator.GROUND_Y && prevBottom <= LevelGenerator.GROUND_Y + 28f) {
      player.y = LevelGenerator.GROUND_Y - player.height
      player.vy = 0f
      player.onGround = true
    }

    for (pipe in pipes) {
      val pipeTop = LevelGenerator.GROUND_Y - pipe.height
      if (player.x + player.width > pipe.x + 4f && player.x < pipe.x + pipe.width - 4f) {
        if (player.vy >= 0f && prevBottom <= pipeTop + 22f && player.y + player.height >= pipeTop) {
          player.y = pipeTop - player.height
          player.vy = 0f
          player.onGround = true
        }
      }
    }

    // Floating brick & question blocks
    for (block in blocks) {
      if (block.broken) continue
      if (block.bumpOffset < 0f) {
        block.bumpOffset = (block.bumpOffset + dt * 90f).coerceAtMost(0f)
      }
      val overlapX = player.x + player.width > block.x + 4f && player.x < block.x + block.size - 4f
      if (!overlapX) continue
      if (player.vy >= 0f && prevBottom <= block.y + 20f && player.y + player.height >= block.y) {
        player.y = block.y - player.height
        player.vy = 0f
        player.onGround = true
      } else if (player.vy < 0f && prevTop >= block.y + block.size - 16f && player.y <= block.y + block.size) {
        player.y = block.y + block.size
        player.vy = 120f
        triggerBlockHit(block)
      }
    }

    if (player.y > LevelGenerator.WORLD_HEIGHT + 80f) {
      damagePlayer(1)
      if (outcome == GameOutcome.PLAYING) {
        player.x = findNearestSafeGroundX(player.x)
        player.y = LevelGenerator.GROUND_Y - player.height - 90f
        player.vy = 0f
      }
    }
  }

  private fun resolveHorizontalPipeCollision(car: WorldCar) {
    for (pipe in pipes) {
      val pipeTop = LevelGenerator.GROUND_Y - pipe.height
      if (car.y + car.height > pipeTop + 16f && car.x + car.width > pipe.x && car.x < pipe.x + pipe.width) {
        if (pipe.isRamp && abs(car.vx) > 190f) {
          // Ramp launch over low ramp pipes!
          car.vy = -420f
          car.y = pipeTop - car.height - 4f
        } else if (car.x + car.width * 0.5f > pipe.x + 12f && car.x + car.width * 0.5f < pipe.x + pipe.width - 12f && car.y + car.height > pipeTop + 24f) {
          // Anti-wedge lift onto pipe top
          car.y = pipeTop - car.height
          car.vy = 0f
          car.onGround = true
        } else {
          if (car.vx > 0f) car.x = pipe.x - car.width
          else if (car.vx < 0f) car.x = pipe.x + pipe.width
          // Keep horizontal speed if currently jumping upward so car vaults cleanly over the pipe
          if (car.vy >= 0f) car.vx = 0f
        }
      }
    }
  }

  private fun updateUnoccupiedCars(dt: Float) {
    val playerCenterX = currentPlayerCenterX()
    for (car in cars) {
      if (car.isOccupiedByPlayer || car.wrecked) continue
      if (car.hasPoliceDriver) {
        car.sirenTimer += dt
        val dist = playerCenterX - (car.x + car.width * 0.5f)
        val chaseDir = if (dist >= 0f) 1f else -1f
        car.facingRight = chaseDir > 0f
        val targetSpeed = if (abs(dist) > 230f) chaseDir * (260f + wantedStars * 35f) else 0f
        car.vx += (targetSpeed - car.vx) * (dt * 3.5f).coerceAtMost(1f)
        car.x = (car.x + car.vx * dt).coerceIn(20f, LevelGenerator.WORLD_LENGTH - car.width - 20f)
        car.wheelSpin += car.vx * dt * 0.08f

        // Police car shoots from range only at high wanted heat (>= 3)
        if (wantedStars >= 3 && abs(dist) < 660f) {
          car.policeShootTimer -= dt
          if (car.policeShootTimer <= 0f) {
            car.policeShootTimer = (2.6f - wantedStars * 0.22f).coerceAtLeast(1.45f)
            spawnEnemyShot(
              startX = car.x + car.width * 0.5f,
              startY = car.y + 18f,
              targetX = currentPlayerCenterX(),
              targetY = currentPlayerCenterY(),
              speed = 430f,
            )
          }
        }
      } else {
        car.vx *= 0.88f
        if (abs(car.vx) < 4f) car.vx = 0f
        car.x += car.vx * dt
      }

      // Gravity for parked/chasing cars
      if (!hasGroundBelow(car.x + 15f, car.x + car.width - 15f)) {
        car.vy += 1200f * dt
        car.y += car.vy * dt
        if (car.y > LevelGenerator.WORLD_HEIGHT + 80f) car.wrecked = true
      } else if (car.y + car.height < LevelGenerator.GROUND_Y) {
        car.vy += 1200f * dt
        car.y = (car.y + car.vy * dt).coerceAtMost(LevelGenerator.GROUND_Y - car.height)
        if (car.y + car.height >= LevelGenerator.GROUND_Y) car.vy = 0f
      }
    }
  }

  private fun updateEnemies(dt: Float) {
    val drivenCar = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    val pCenterX = currentPlayerCenterX()
    val pCenterY = currentPlayerCenterY()

    for (enemy in enemies) {
      if (!enemy.alive) continue
      enemy.animTime += dt
      if (enemy.hitFlash > 0f) enemy.hitFlash -= dt

      when (enemy.kind) {
        EnemyKind.GOOMBA,
        EnemyKind.KOOPA -> {
          enemy.x += enemy.vx * dt
          // Reverse at pit edges or pipes
          val aheadX = if (enemy.vx > 0f) enemy.x + enemy.width + 4f else enemy.x - 4f
          if (aheadX < 20f || aheadX > LevelGenerator.WORLD_LENGTH - 80f || !hasGroundBelow(aheadX, aheadX + 4f)) {
            enemy.vx = -enemy.vx
          }
          for (pipe in pipes) {
            val pipeTop = LevelGenerator.GROUND_Y - pipe.height
            if (enemy.y + enemy.height > pipeTop && enemy.x + enemy.width > pipe.x && enemy.x < pipe.x + pipe.width) {
              enemy.vx = -enemy.vx
              enemy.x += enemy.vx * dt * 2f
            }
          }
          enemy.facingRight = enemy.vx > 0f

          // Koopas fire ranged plasma shells if player is within 560px
          if (enemy.kind == EnemyKind.KOOPA && abs(enemy.x - pCenterX) < 560f) {
            enemy.shootCooldown -= dt
            if (enemy.shootCooldown <= 0f) {
              enemy.shootCooldown = 3.4f
              spawnEnemyShot(
                startX = enemy.x + enemy.width * 0.5f,
                startY = enemy.y + 18f,
                targetX = pCenterX,
                targetY = pCenterY,
                speed = 360f,
              )
            }
          }
        }
        EnemyKind.PARATROOPA -> {
          enemy.x += enemy.vx * dt
          enemy.y = enemy.baseY + sin(enemy.animTime * 2.8f) * 44f
          if (enemy.x < 300f || enemy.x > LevelGenerator.FLAGPOLE_X - 180f) {
            enemy.vx = -enemy.vx
          }
          enemy.facingRight = enemy.vx > 0f
          if (abs(enemy.x - pCenterX) < 620f) {
            enemy.shootCooldown -= dt
            if (enemy.shootCooldown <= 0f) {
              enemy.shootCooldown = 3.3f
              spawnEnemyShot(
                startX = enemy.x + enemy.width * 0.5f,
                startY = enemy.y + enemy.height * 0.5f,
                targetX = pCenterX,
                targetY = pCenterY,
                speed = 380f,
              )
            }
          }
        }
        EnemyKind.CHOPPER_BOSS -> {
          val dist = pCenterX - enemy.x
          enemy.vx = if (abs(dist) > 280f) kotlin.math.sign(dist) * 145f else sin(enemy.animTime * 1.5f) * 120f
          enemy.x = (enemy.x + enemy.vx * dt).coerceIn(600f, LevelGenerator.FLAGPOLE_X - 120f)
          enemy.y = enemy.baseY + sin(enemy.animTime * 2.2f) * 32f
          enemy.facingRight = dist > 0f
          if (abs(dist) < 760f) {
            enemy.shootCooldown -= dt
            if (enemy.shootCooldown <= 0f) {
              enemy.shootCooldown = 2.35f
              spawnEnemyShot(
                startX = enemy.x + enemy.width * 0.5f,
                startY = enemy.y + enemy.height,
                targetX = pCenterX,
                targetY = pCenterY,
                speed = 430f,
              )
            }
          }
        }
      }

      // Collision with driven car or on-foot player
      if (drivenCar != null) {
        if (rectsOverlap(
            drivenCar.x,
            drivenCar.y,
            drivenCar.width,
            drivenCar.height,
            enemy.x,
            enemy.y,
            enemy.width,
            enemy.height,
          )
        ) {
          if (player.starPowerTimer > 0f || abs(drivenCar.vx) >= 110f) {
            defeatEnemy(enemy, viaCarRam = true)
          } else {
            damageCarOrPlayer(1)
            enemy.vx = -enemy.vx
          }
        }
      } else {
        if (rectsOverlap(
            player.x,
            player.y,
            player.width,
            player.height,
            enemy.x,
            enemy.y,
            enemy.width,
            enemy.height,
          )
        ) {
          if (player.starPowerTimer > 0f) {
            defeatEnemy(enemy, viaCarRam = false)
          } else if (player.vy > 10f && player.y + player.height - enemy.y < 34f && enemy.kind != EnemyKind.CHOPPER_BOSS) {
            // Classic forgiving Mario stomp from above!
            player.vy = -500f
            defeatEnemy(enemy, viaCarRam = false)
          } else {
            damagePlayer(1)
          }
        }
      }
    }
  }

  private fun updateLongRangeTargetLock(viewportWorldWidth: Float) {
    if (manualAimX != null && manualAimY != null) {
      lockedEnemyId = null
      lockedTargetX = manualAimX
      lockedTargetY = manualAimY
      return
    }
    val originX = currentPlayerCenterX()
    val originY = currentPlayerCenterY()
    val maxRange = weaponSpec.rangePx
    val facingSign = if (player.facingRight) 1f else -1f

    var bestDist = Float.MAX_VALUE
    var bestX: Float? = null
    var bestY: Float? = null
    var bestId: Int? = null

    for (enemy in enemies) {
      if (!enemy.alive) continue
      val ex = enemy.x + enemy.width * 0.5f
      val ey = enemy.y + enemy.height * 0.5f
      val dx = ex - originX
      val dy = ey - originY
      val dist = hypot(dx, dy)
      if (dist <= maxRange && dx * facingSign >= -60f && ex >= cameraX - 80f && ex <= cameraX + viewportWorldWidth + 320f) {
        if (dist < bestDist) {
          bestDist = dist
          bestId = enemy.id
          bestX = ex
          bestY = ey
        }
      }
    }

    // Also lock onto hostile police cars if closer
    for (car in cars) {
      if (!car.hasPoliceDriver || car.wrecked) continue
      val cx = car.x + car.width * 0.5f
      val cy = car.y + car.height * 0.5f
      val dx = cx - originX
      val dy = cy - originY
      val dist = hypot(dx, dy)
      if (dist <= maxRange && dx * facingSign >= -40f && dist < bestDist) {
        bestDist = dist
        bestId = null
        bestX = cx
        bestY = cy
      }
    }

    lockedEnemyId = bestId
    lockedTargetX = bestX
    lockedTargetY = bestY
  }

  private fun tryShoot(explicitTargetX: Float?, explicitTargetY: Float?) {
    if (fireCooldownRemaining > 0f) return

    val originX = currentPlayerCenterX() + if (player.facingRight) 24f else -24f
    val originY = currentPlayerCenterY() - (if (player.isCrouching) -6f else 6f)

    val defaultAimOffsetY =
      when {
        inputUp -> -260f
        inputDown && !isGroundedNow() -> 180f
        else -> 0f
      }
    val tx = explicitTargetX ?: lockedTargetX ?: (originX + if (player.facingRight) 600f else -600f)
    val ty = explicitTargetY ?: lockedTargetY ?: (originY + defaultAimOffsetY)
    val baseAngle = atan2(ty - originY, tx - originX)

    val powerUp = player.activeWeaponPowerUp
    val cooldownSec: Float
    val style: ProjectileStyle
    val projSpeed: Float
    val damage: Int
    val burst: Int
    val spreadStep: Float
    val maxRange: Float
    val splashRadius: Float
    val homing: Boolean

    when (powerUp) {
      WeaponPowerUp.MISSILE_LAUNCHER -> {
        cooldownSec = 0.38f
        style = ProjectileStyle.MISSILE
        projSpeed = 900f
        damage = 4
        burst = 1
        spreadStep = 0f
        maxRange = 1750f
        splashRadius = 115f
        homing = true
        soundListener.onMissileLaunch()
      }
      WeaponPowerUp.SPREAD_SHOTGUN -> {
        cooldownSec = 0.30f
        style = ProjectileStyle.SHOTGUN_PELLET
        projSpeed = 1480f
        damage = 2
        burst = 5
        spreadStep = 0.085f
        maxRange = 1150f
        splashRadius = 0f
        homing = false
        soundListener.onShoot()
      }
      WeaponPowerUp.RAPID_MINIGUN -> {
        cooldownSec = 0.085f
        style = ProjectileStyle.MINIGUN_ROUND
        projSpeed = 1820f
        damage = 2
        burst = 1
        spreadStep = 0f
        maxRange = 1650f
        splashRadius = 0f
        homing = false
        soundListener.onShoot()
      }
      WeaponPowerUp.FIRE_OVERDRIVE -> {
        cooldownSec = (weaponSpec.fireCooldownMs / 1000f) * 0.5f
        style = ProjectileStyle.FIREBALL
        projSpeed = weaponSpec.projectileSpeed * 1.18f
        damage = weaponSpec.damage + 1
        burst = weaponSpec.burstCount + 1
        spreadStep = 0.10f
        maxRange = weaponSpec.rangePx * 1.15f
        splashRadius = 0f
        homing = false
        soundListener.onShoot()
      }
      WeaponPowerUp.NONE -> {
        cooldownSec = weaponSpec.fireCooldownMs / 1000f
        style =
          when (weaponSpec) {
            WeaponSpec.FIRE_BLASTER -> ProjectileStyle.FIREBALL
            WeaponSpec.STAR_RIFLE -> ProjectileStyle.STAR_BOLT
            WeaponSpec.SHELL_CANNON -> ProjectileStyle.SHELL_ORB
          }
        projSpeed = weaponSpec.projectileSpeed
        damage = weaponSpec.damage
        burst = weaponSpec.burstCount
        spreadStep = if (burst > 1) 0.11f else 0f
        maxRange = weaponSpec.rangePx
        splashRadius = 0f
        homing = false
        soundListener.onShoot()
      }
    }

    fireCooldownRemaining = cooldownSec

    for (i in 0 until burst) {
      val offsetIdx = i - (burst - 1) / 2f
      val angle = baseAngle + offsetIdx * spreadStep
      projectiles.add(
        Projectile(
          id = nextEntityId++,
          x = originX,
          y = originY,
          vx = cos(angle) * projSpeed,
          vy = sin(angle) * projSpeed,
          fromPlayer = true,
          style = style,
          damage = damage,
          maxRange = maxRange,
          splashRadius = splashRadius,
          homing = homing,
        )
      )
    }

    // Muzzle spark
    particles.add(
      Particle(
        x = originX,
        y = originY,
        vx = cos(baseAngle) * 120f,
        vy = sin(baseAngle) * 120f,
        life = 0.14f,
        maxLife = 0.14f,
        size = if (powerUp == WeaponPowerUp.MISSILE_LAUNCHER) 16f else 11f,
        colorArgb = if (powerUp == WeaponPowerUp.MISSILE_LAUNCHER) 0xFFFF6D00L else 0xFFFFE040L,
      )
    )
  }

  private fun spawnEnemyShot(
    startX: Float,
    startY: Float,
    targetX: Float,
    targetY: Float,
    speed: Float,
  ) {
    val angle = atan2(targetY - startY, targetX - startX)
    projectiles.add(
      Projectile(
        id = nextEntityId++,
        x = startX,
        y = startY,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed,
        fromPlayer = false,
        style = ProjectileStyle.ENEMY_BOLT,
        damage = 1,
        maxRange = 950f,
      )
    )
  }

  private fun updateProjectiles(dt: Float, viewportWorldWidth: Float) {
    val iterator = projectiles.iterator()
    while (iterator.hasNext()) {
      val p = iterator.next()
      if (!p.active) {
        iterator.remove()
        continue
      }
      // Homing missile tracking toward nearest enemy or hostile police car
      if (p.fromPlayer && p.homing) {
        steerMissileTowardTarget(p, dt)
        if (Random.nextFloat() < 0.65f) {
          spawnSmoke(p.x, p.y)
        }
      }
      val stepX = p.vx * dt
      val stepY = p.vy * dt
      p.x += stepX
      p.y += stepY
      p.distanceTraveled += sqrt(stepX * stepX + stepY * stepY)

      if (p.distanceTraveled >= p.maxRange || p.y < -80f || p.y > LevelGenerator.WORLD_HEIGHT + 40f || p.x < cameraX - 240f || p.x > cameraX + viewportWorldWidth + 420f) {
        iterator.remove()
        continue
      }

      if (p.fromPlayer) {
        // Long-range shooting can pop question blocks too!
        var consumed = false
        for (block in blocks) {
          if (!block.broken && block.kind != BlockKind.SOLID_STEP && p.x in block.x..(block.x + block.size) && p.y in block.y..(block.y + block.size)) {
            triggerBlockHit(block)
            if (p.splashRadius > 0f) {
              detonateSplashExplosion(p.x, p.y, p.splashRadius, p.damage)
            }
            consumed = true
            break
          }
        }
        if (consumed) {
          iterator.remove()
          continue
        }

        // Hit enemies from far away
        for (enemy in enemies) {
          if (!enemy.alive) continue
          if (p.x in (enemy.x - 10f)..(enemy.x + enemy.width + 10f) && p.y in (enemy.y - 10f)..(enemy.y + enemy.height + 10f)) {
            if (p.splashRadius > 0f) {
              detonateSplashExplosion(p.x, p.y, p.splashRadius, p.damage)
            } else {
              enemy.hp -= p.damage
              enemy.hitFlash = 0.16f
              spawnSparks(p.x, p.y, 0xFFFFB300L, 6)
              if (enemy.hp <= 0) {
                defeatEnemy(enemy, viaCarRam = false)
              }
            }
            consumed = true
            break
          }
        }
        if (consumed) {
          iterator.remove()
          continue
        }

        // Hit hostile police cruisers
        for (car in cars) {
          if (!car.hasPoliceDriver || car.wrecked) continue
          if (p.x in car.x..(car.x + car.width) && p.y in car.y..(car.y + car.height)) {
            if (p.splashRadius > 0f) {
              detonateSplashExplosion(p.x, p.y, p.splashRadius, p.damage)
            } else {
              car.hp -= p.damage
              spawnSparks(p.x, p.y, 0xFF00E5FFL, 6)
              if (car.hp <= 0) {
                disablePoliceCar(car)
              }
            }
            consumed = true
            break
          }
        }
        if (consumed) {
          iterator.remove()
          continue
        }
      } else {
        // Enemy bolt hitting player or player's car
        val drivenCar = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
        if (drivenCar != null) {
          if (p.x in drivenCar.x..(drivenCar.x + drivenCar.width) && p.y in drivenCar.y..(drivenCar.y + drivenCar.height)) {
            damageCarOrPlayer(p.damage)
            spawnSparks(p.x, p.y, 0xFFFF3B30L, 6)
            iterator.remove()
            continue
          }
        } else if (p.x in player.x..(player.x + player.width) && p.y in player.y..(player.y + player.height)) {
          damagePlayer(p.damage)
          spawnSparks(p.x, p.y, 0xFFFF3B30L, 6)
          iterator.remove()
          continue
        }
      }
    }
  }

  private fun steerMissileTowardTarget(p: Projectile, dt: Float) {
    var bestX: Float? = null
    var bestY: Float? = null
    var bestDist = 620f
    for (enemy in enemies) {
      if (!enemy.alive) continue
      val ex = enemy.x + enemy.width * 0.5f
      val ey = enemy.y + enemy.height * 0.5f
      val d = hypot(ex - p.x, ey - p.y)
      if (d < bestDist) {
        bestDist = d
        bestX = ex
        bestY = ey
      }
    }
    for (car in cars) {
      if (!car.hasPoliceDriver || car.wrecked) continue
      val cx = car.x + car.width * 0.5f
      val cy = car.y + car.height * 0.5f
      val d = hypot(cx - p.x, cy - p.y)
      if (d < bestDist) {
        bestDist = d
        bestX = cx
        bestY = cy
      }
    }
    val tx = bestX ?: return
    val ty = bestY ?: return
    val speed = hypot(p.vx, p.vy).coerceAtLeast(400f)
    val desiredAngle = atan2(ty - p.y, tx - p.x)
    val currentAngle = atan2(p.vy, p.vx)
    var diff = desiredAngle - currentAngle
    while (diff > Math.PI) diff -= (2 * Math.PI).toFloat()
    while (diff < -Math.PI) diff += (2 * Math.PI).toFloat()
    val maxTurn = 4.5f * dt
    val newAngle = currentAngle + diff.coerceIn(-maxTurn, maxTurn)
    p.vx = cos(newAngle) * speed
    p.vy = sin(newAngle) * speed
  }

  private fun detonateSplashExplosion(cx: Float, cy: Float, radius: Float, damage: Int) {
    soundListener.onExplosion()
    spawnSparks(cx, cy, 0xFFFF5722L, 18)
    spawnSparks(cx, cy, 0xFFFBD000L, 10)
    spawnFloatingText(cx - 24f, cy - 22f, "BOOM!", 0xFFFF6D00L)

    for (enemy in enemies) {
      if (!enemy.alive) continue
      val ex = enemy.x + enemy.width * 0.5f
      val ey = enemy.y + enemy.height * 0.5f
      if (hypot(ex - cx, ey - cy) <= radius) {
        enemy.hp -= damage
        enemy.hitFlash = 0.2f
        if (enemy.hp <= 0) {
          defeatEnemy(enemy, viaCarRam = false)
        }
      }
    }
    for (car in cars) {
      if (!car.hasPoliceDriver || car.wrecked) continue
      val carX = car.x + car.width * 0.5f
      val carY = car.y + car.height * 0.5f
      if (hypot(carX - cx, carY - cy) <= radius) {
        car.hp -= damage
        if (car.hp <= 0) {
          disablePoliceCar(car)
        }
      }
    }
  }

  private fun disablePoliceCar(car: WorldCar) {
    car.hasPoliceDriver = false
    car.hp = car.maxHp
    score += 400
    coins += 5
    addWantedHeat(1.2f)
    spawnSparks(car.x + 50f, car.y + 20f, 0xFFFF5252L, 14)
    spawnFloatingText(car.x + 40f, car.y - 16f, "+400 PATROL!", 0xFF00E5FFL)
  }

  private fun updatePickups(dt: Float) {
    val pLeft: Float
    val pTop: Float
    val pW: Float
    val pH: Float
    val drivenCar = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    if (drivenCar != null) {
      pLeft = drivenCar.x
      pTop = drivenCar.y
      pW = drivenCar.width
      pH = drivenCar.height
    } else {
      pLeft = player.x
      pTop = player.y
      pW = player.width
      pH = player.height
    }

    val pCenterX = pLeft + pW * 0.5f
    val pCenterY = pTop + pH * 0.5f

    for (pickup in pickups) {
      if (pickup.collected) continue
      pickup.animTime += dt
      if (pickup.vy != 0f) {
        pickup.y += pickup.vy * dt
        pickup.vy += 650f * dt
        if (pickup.vy > 0f && pickup.y >= LevelGenerator.GROUND_Y - 40f) {
          pickup.vy = 0f
        }
      }
      // Shield Coin Magnet pulls nearby coins smoothly
      if (pickup.kind == PickupKind.COIN && player.shieldCharges > 0) {
        val dx = pCenterX - pickup.x
        val dy = pCenterY - pickup.y
        val dist = hypot(dx, dy)
        if (dist in 4f..260f) {
          pickup.x += (dx / dist) * 460f * dt
          pickup.y += (dy / dist) * 460f * dt
        }
      }
      if (rectsOverlap(pLeft, pTop, pW, pH, pickup.x - 22f, pickup.y - 22f, 44f, 44f)) {
        pickup.collected = true
        when (pickup.kind) {
          PickupKind.COIN -> {
            coins += 1
            score += 100
            soundListener.onCoin()
            spawnFloatingText(pickup.x, pickup.y - 14f, "+100", 0xFFFBD000L)
          }
          PickupKind.SUPER_SHROOM -> {
            player.hp = (player.hp + 3).coerceAtMost(player.maxHp)
            drivenCar?.let { it.hp = it.maxHp }
            score += 300
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "REPAIR +HP!", 0xFF43B047L)
            showBanner("SUPER MUSHROOM REPAIR!")
          }
          PickupKind.CLEAR_WANTED_STAR -> {
            wantedHeat = 0f
            wantedStars = 0
            score += 500
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "HEAT CLEARED!", 0xFF00E5FFL)
            showBanner("PAY'N'SPRAY STAR: WANTED CLEARED!")
          }
          PickupKind.TURBO_NITRO -> {
            player.turboTimer = 7.0f
            score += 250
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "TURBO BOOST!", 0xFFFF5E7EL)
            showBanner("TURBO NITRO ENGAGED!")
          }
          PickupKind.STAR_POWER -> {
            player.starPowerTimer = 9.0f
            score += 500
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "★ STAR POWER!", 0xFFFBD000L)
            showBanner("RAINBOW STAR: INVINCIBLE RAM!")
          }
          PickupKind.ARMOR_SHIELD -> {
            player.shieldCharges = 3
            player.shieldTimer = 18.0f
            score += 350
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "SHIELD x3!", 0xFF00E5FFL)
            showBanner("ARMOR SHIELD + COIN MAGNET!")
          }
          PickupKind.FIRE_FLOWER -> {
            player.activeWeaponPowerUp = WeaponPowerUp.FIRE_OVERDRIVE
            player.weaponPowerUpTimer = 12.0f
            fireCooldownRemaining = 0f
            score += 350
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "FIRE OVERDRIVE!", 0xFFFF6D00L)
            showBanner("FIRE FLOWER OVERDRIVE!")
          }
          PickupKind.MISSILE_LAUNCHER -> {
            player.activeWeaponPowerUp = WeaponPowerUp.MISSILE_LAUNCHER
            player.weaponPowerUpTimer = 13.0f
            fireCooldownRemaining = 0f
            score += 450
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "RPG LAUNCHER!", 0xFFFF5252L)
            showBanner("HOMING MISSILE LAUNCHER EQUIPPED!")
          }
          PickupKind.SPREAD_SHOTGUN -> {
            player.activeWeaponPowerUp = WeaponPowerUp.SPREAD_SHOTGUN
            player.weaponPowerUpTimer = 12.0f
            fireCooldownRemaining = 0f
            score += 400
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "5x SHOTGUN!", 0xFFFFB300L)
            showBanner("5-BARREL SPREAD SHOTGUN!")
          }
          PickupKind.RAPID_MINIGUN -> {
            player.activeWeaponPowerUp = WeaponPowerUp.RAPID_MINIGUN
            player.weaponPowerUpTimer = 12.0f
            fireCooldownRemaining = 0f
            score += 400
            soundListener.onPowerUp()
            spawnFloatingText(pickup.x, pickup.y - 14f, "MINIGUN!", 0xFF00E5FFL)
            showBanner("RAPID TURBO MINIGUN!")
          }
        }
        if (pickup.kind != PickupKind.COIN) {
          hudSyncTimer = 0f
          hudSnapshot = buildHudSnapshot()
        }
      }
    }
  }

  private fun updateParticles(dt: Float) {
    val it = particles.iterator()
    while (it.hasNext()) {
      val pt = it.next()
      pt.life -= dt
      if (pt.life <= 0f) {
        it.remove()
        continue
      }
      pt.x += pt.vx * dt
      pt.y += pt.vy * dt
    }
  }

  private fun updateWantedSystem(dt: Float, viewportWorldWidth: Float) {
    if (wantedHeat > 0f) {
      wantedHeat = (wantedHeat - dt * 0.22f).coerceAtLeast(0f)
      wantedStars = wantedHeat.toInt().coerceIn(0, 5)
    }
    if (wantedStars >= 1) {
      policeSpawnTimer -= dt
      if (policeSpawnTimer <= 0f) {
        policeSpawnTimer = (7.5f - wantedStars * 0.65f).coerceAtLeast(4.2f)
        val activePatrols = cars.count { it.hasPoliceDriver && !it.wrecked }
        if (activePatrols < 1 + wantedStars / 2) {
          val spawnAhead = Random.nextBoolean()
          val spawnX =
            if (spawnAhead) (cameraX + viewportWorldWidth + 140f).coerceAtMost(LevelGenerator.FLAGPOLE_X - 180f)
            else (cameraX - 160f).coerceAtLeast(40f)
          cars.add(
            WorldCar(
              id = nextEntityId++,
              spec = VehicleSpec.KART_RED,
              isPolice = true,
              x = spawnX,
              y = LevelGenerator.GROUND_Y - 54f,
              hp = 2 + wantedStars,
              maxHp = 2 + wantedStars,
              facingRight = spawnX < currentPlayerCenterX(),
              hasPoliceDriver = true,
            )
          )
        }
      }
    }
  }

  private fun triggerBlockHit(block: BlockTile) {
    if (block.used && block.kind != BlockKind.BRICK) return
    block.bumpOffset = -12f
    when (block.kind) {
      BlockKind.BRICK -> {
        block.broken = true
        score += 50
        soundListener.onBlockHit()
        spawnSparks(block.x + 24f, block.y + 24f, 0xFFC84C0CL, 8)
      }
      BlockKind.QUESTION_COIN -> {
        block.used = true
        coins += 3
        score += 300
        soundListener.onCoin()
        spawnFloatingText(block.x + 12f, block.y - 14f, "+3 COINS", 0xFFFBD000L)
      }
      BlockKind.QUESTION_SHROOM -> spawnBlockPowerUp(block, PickupKind.SUPER_SHROOM)
      BlockKind.QUESTION_STAR -> spawnBlockPowerUp(block, PickupKind.STAR_POWER)
      BlockKind.QUESTION_FIRE -> spawnBlockPowerUp(block, PickupKind.FIRE_FLOWER)
      BlockKind.QUESTION_SHIELD -> spawnBlockPowerUp(block, PickupKind.ARMOR_SHIELD)
      BlockKind.QUESTION_MISSILE -> spawnBlockPowerUp(block, PickupKind.MISSILE_LAUNCHER)
      BlockKind.QUESTION_SHOTGUN -> spawnBlockPowerUp(block, PickupKind.SPREAD_SHOTGUN)
      BlockKind.QUESTION_MINIGUN -> spawnBlockPowerUp(block, PickupKind.RAPID_MINIGUN)
      BlockKind.QUESTION_NITRO -> spawnBlockPowerUp(block, PickupKind.TURBO_NITRO)
      BlockKind.SOLID_STEP -> Unit
    }
  }

  private fun spawnBlockPowerUp(block: BlockTile, kind: PickupKind) {
    block.used = true
    soundListener.onBlockHit()
    pickups.add(
      WorldPickup(
        id = nextEntityId++,
        kind = kind,
        x = block.x + 24f,
        y = block.y - 24f,
        vy = -210f,
      )
    )
  }

  private fun defeatEnemy(enemy: EnemyEntity, viaCarRam: Boolean) {
    if (!enemy.alive) return
    enemy.alive = false
    enemiesDefeated += 1
    soundListener.onEnemyDefeat()
    val bonus = if (viaCarRam) 200 else 150
    val kindPoints =
      when (enemy.kind) {
        EnemyKind.GOOMBA -> 100
        EnemyKind.KOOPA -> 200
        EnemyKind.PARATROOPA -> 300
        EnemyKind.CHOPPER_BOSS -> 1000
      }
    val totalPts = kindPoints + bonus
    score += totalPts
    val dropCoins = if (enemy.kind == EnemyKind.CHOPPER_BOSS) 12 else 2
    coins += dropCoins
    addWantedHeat(if (enemy.kind == EnemyKind.CHOPPER_BOSS) 1.2f else 0.35f)
    spawnSparks(enemy.x + enemy.width * 0.5f, enemy.y + enemy.height * 0.5f, 0xFFFBD000L, 10)
    spawnFloatingText(
      enemy.x,
      enemy.y - 12f,
      if (viaCarRam) "+$totalPts ROADKILL!" else "+$totalPts",
      0xFFFBD000L,
    )

    // Bonus weapon / power-up loot drop every 3 KO's or on Boss defeat
    if (enemy.kind == EnemyKind.CHOPPER_BOSS) {
      pickups.add(
        WorldPickup(
          id = nextEntityId++,
          kind = PickupKind.STAR_POWER,
          x = enemy.x + enemy.width * 0.5f,
          y = enemy.y,
          vy = -200f,
        )
      )
    } else if (enemiesDefeated % 3 == 0) {
      val lootPool =
        listOf(
          PickupKind.MISSILE_LAUNCHER,
          PickupKind.SPREAD_SHOTGUN,
          PickupKind.RAPID_MINIGUN,
          PickupKind.SUPER_SHROOM,
          PickupKind.FIRE_FLOWER,
          PickupKind.ARMOR_SHIELD,
          PickupKind.TURBO_NITRO,
        )
      val loot = lootPool[(enemiesDefeated / 3) % lootPool.size]
      pickups.add(
        WorldPickup(
          id = nextEntityId++,
          kind = loot,
          x = enemy.x + enemy.width * 0.5f,
          y = enemy.y - 8f,
          vy = -190f,
        )
      )
    }
  }

  private fun addWantedHeat(amount: Float) {
    wantedHeat = (wantedHeat + amount).coerceAtMost(5.8f)
    wantedStars = wantedHeat.toInt().coerceIn(0, 5)
    if (wantedStars > maxWantedStarsReached) {
      maxWantedStarsReached = wantedStars
      soundListener.onWantedUp()
      showBanner("WANTED LEVEL ★$wantedStars!")
    }
  }

  private fun damageCarOrPlayer(amount: Int) {
    if (player.invulnTimer > 0f || player.starPowerTimer > 0f) return
    if (player.shieldCharges > 0) {
      player.shieldCharges -= 1
      player.invulnTimer = 0.55f
      soundListener.onBlockHit()
      spawnSparks(currentPlayerCenterX(), currentPlayerCenterY(), 0xFF00E5FFL, 10)
      showBanner(
        if (player.shieldCharges > 0) "SHIELD ABSORBED HIT (${player.shieldCharges} LEFT)"
        else "SHIELD DEPLETED!",
      )
      return
    }
    player.invulnTimer = 0.95f
    soundListener.onDamage()
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    if (car != null) {
      car.hp -= amount
      spawnSparks(car.x + car.width * 0.5f, car.y + 18f, 0xFFFF5252L, 8)
      if (car.hp <= 0) {
        car.wrecked = true
        car.isOccupiedByPlayer = false
        player.drivenCarId = null
        player.y = car.y - player.height - 8f
        player.vy = -380f
        soundListener.onExplosion()
        showBanner("CAR WRECKED! BAILING OUT!")
      }
    } else {
      damagePlayer(amount)
    }
  }

  private fun damagePlayer(amount: Int) {
    if (player.invulnTimer > 0f || player.starPowerTimer > 0f) return
    if (player.shieldCharges > 0) {
      player.shieldCharges -= 1
      player.invulnTimer = 0.65f
      soundListener.onBlockHit()
      spawnSparks(currentPlayerCenterX(), currentPlayerCenterY(), 0xFF00E5FFL, 10)
      return
    }
    player.invulnTimer = 1.15f
    soundListener.onDamage()
    player.hp = (player.hp - amount).coerceAtLeast(0)
    if (player.hp <= 0) {
      outcome = GameOutcome.BUSTED
      showBanner("BUSTED IN THE MUSHROOM KINGDOM!")
      soundListener.onBusted()
      hudSnapshot = buildHudSnapshot()
    }
  }

  private fun findNearestEnterableCar(): WorldCar? {
    val px = currentPlayerCenterX()
    val py = currentPlayerCenterY()
    return cars
      .filter { !it.wrecked && !it.isOccupiedByPlayer }
      .minByOrNull { hypot((it.x + it.width * 0.5f) - px, (it.y + it.height * 0.5f) - py) }
      ?.takeIf { hypot((it.x + it.width * 0.5f) - px, (it.y + it.height * 0.5f) - py) <= 130f }
  }

  private fun hasGroundBelow(leftX: Float, rightX: Float): Boolean {
    val midX = (leftX + rightX) * 0.5f
    return groundSegments.any { seg -> midX >= seg.startX && midX <= seg.endX }
  }

  private fun findNearestSafeGroundX(x: Float): Float {
    val seg = groundSegments.minByOrNull { abs(x - (it.startX + it.endX) * 0.5f) } ?: return 120f
    return x.coerceIn(seg.startX + 40f, seg.endX - 80f)
  }

  private fun rectsOverlap(
    ax: Float,
    ay: Float,
    aw: Float,
    ah: Float,
    bx: Float,
    by: Float,
    bw: Float,
    bh: Float,
  ): Boolean {
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
  }

  private fun spawnSparks(x: Float, y: Float, colorArgb: Long, count: Int) {
    repeat(count) {
      val angle = Random.nextFloat() * 6.283f
      val speed = 80f + Random.nextFloat() * 240f
      particles.add(
        Particle(
          x = x,
          y = y,
          vx = cos(angle) * speed,
          vy = sin(angle) * speed - 60f,
          life = 0.35f + Random.nextFloat() * 0.25f,
          maxLife = 0.6f,
          size = 6f + Random.nextFloat() * 5f,
          colorArgb = colorArgb,
        )
      )
    }
  }

  private fun spawnSmoke(x: Float, y: Float) {
    particles.add(
      Particle(
        x = x,
        y = y,
        vx = -30f + Random.nextFloat() * 60f,
        vy = -40f - Random.nextFloat() * 30f,
        life = 0.42f,
        maxLife = 0.42f,
        size = 10f,
        colorArgb = 0xCCDFE6E9L,
      )
    )
  }

  private fun spawnFloatingText(x: Float, y: Float, label: String, colorArgb: Long) {
    particles.add(
      Particle(
        x = x,
        y = y,
        vx = 0f,
        vy = -52f,
        life = 0.85f,
        maxLife = 0.85f,
        size = 14f,
        colorArgb = colorArgb,
        text = label,
      )
    )
  }

  private fun showBanner(message: String) {
    statusBanner = message
    statusBannerTimer = 2.6f
  }

  fun currentPlayerCenterX(): Float {
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    return if (car != null) car.x + car.width * 0.5f else player.x + player.width * 0.5f
  }

  fun currentPlayerCenterY(): Float {
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    return if (car != null) car.y + car.height * 0.45f else player.y + player.height * 0.5f
  }

  private fun buildHudSnapshot(): HudSnapshot {
    val car = cars.firstOrNull { it.id == player.drivenCarId && !it.wrecked }
    val nearCar = if (car == null) findNearestEnterableCar() else null
    return HudSnapshot(
      worldTheme = worldTheme,
      score = score,
      coins = coins,
      enemiesDefeated = enemiesDefeated,
      hp = player.hp,
      maxHp = player.maxHp,
      carHp = car?.hp,
      carMaxHp = car?.maxHp,
      carName = if (car?.isPolice == true) "Toad Patrol" else car?.spec?.displayName,
      canEnterCar = nearCar != null,
      nearPoliceCar = nearCar?.hasPoliceDriver == true,
      inCar = car != null,
      wantedStars = wantedStars,
      maxWantedStarsReached = maxWantedStarsReached,
      weaponSpec = weaponSpec,
      progressFraction = (currentPlayerCenterX() / LevelGenerator.FLAGPOLE_X).coerceIn(0f, 1f),
      hasTargetLock = lockedTargetX != null,
      outcome = outcome,
      statusBanner = statusBanner,
      starSecondsLeft = ceil(player.starPowerTimer).toInt().coerceAtLeast(0),
      nitroSecondsLeft = ceil(player.turboTimer).toInt().coerceAtLeast(0),
      shieldCharges = player.shieldCharges,
      activeWeaponPowerUp = player.activeWeaponPowerUp,
      weaponPowerUpSecondsLeft = ceil(player.weaponPowerUpTimer).toInt().coerceAtLeast(0),
    )
  }
}
