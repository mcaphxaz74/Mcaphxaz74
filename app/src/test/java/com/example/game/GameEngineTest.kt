package com.example.game

import com.example.audio.GameSoundListener
import com.example.data.VehicleSpec
import com.example.data.WeaponSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

  @Test
  fun enterAndExitCar_togglesDrivingStateSmoothly() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
      )

    // Walk right toward the starting parked Pipe Roadster at x=240
    engine.inputRight = true
    repeat(30) { engine.step(0.016f, 1280f) }
    engine.inputRight = false

    assertTrue(engine.hudSnapshot.canEnterCar)
    engine.toggleEnterExitCar()
    assertNotNull(engine.player.drivenCarId)
    assertTrue(engine.hudSnapshot.inCar)

    // Drive forward at speed
    val startX = engine.currentPlayerCenterX()
    engine.inputRight = true
    repeat(40) { engine.step(0.016f, 1280f) }
    assertTrue(engine.currentPlayerCenterX() > startX + 100f)

    // Exit car cleanly
    engine.toggleEnterExitCar()
    assertNull(engine.player.drivenCarId)
    assertFalse(engine.hudSnapshot.inCar)
  }

  @Test
  fun longRangeSnipe_defeatsDistantEnemyAndRaisesWantedStars() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.STAR_RIFLE,
      )

    val distantGoomba = engine.enemies.first { it.kind == EnemyKind.GOOMBA }
    val targetX = distantGoomba.x + distantGoomba.width * 0.5f
    val targetY = distantGoomba.y + distantGoomba.height * 0.5f

    engine.fireAtScreenWorldPoint(targetX, targetY)
    repeat(80) { engine.step(0.016f, 1280f) }

    assertFalse(distantGoomba.alive)
    assertTrue(engine.score > 0)
    assertTrue(engine.enemiesDefeated >= 1)
    assertEquals(1, engine.enemiesDefeated)
  }

  @Test
  fun fourWayController_upJumpsAndDownCrouchesOrFastFalls() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
      )

    // Holding UP triggers immediate jump upward
    engine.inputUp = true
    engine.step(0.016f, 1280f)
    assertFalse(engine.player.onGround)
    assertTrue(engine.player.vy < 0f)
    engine.inputUp = false

    // Advance near apex, then hold DOWN to fast-fall back to ground quickly
    repeat(12) { engine.step(0.016f, 1280f) }
    engine.inputDown = true
    repeat(40) { engine.step(0.016f, 1280f) }
    assertTrue(engine.player.onGround)
    assertTrue(engine.player.isCrouching)

    engine.inputDown = false
    engine.step(0.016f, 1280f)
    assertFalse(engine.player.isCrouching)
  }

  @Test
  fun allVehicles_clearTallestPipeWithFourWayUpRight() {
    for (vehicle in VehicleSpec.entries) {
      val engine =
        GameEngine(
          worldTheme = WorldTheme.TOADSTOOL_STRIP,
          equippedVehicle = vehicle,
          weaponSpec = WeaponSpec.FIRE_BLASTER,
        )

      // Enter the starting car at x=240
      engine.inputRight = true
      repeat(30) { engine.step(0.016f, 1280f) }
      engine.toggleEnterExitCar()
      val car = engine.cars.first { it.id == engine.player.drivenCarId }

      // Place car right in front of the tallest pipe (x=3980, height=108)
      val tallestPipe = engine.pipes.maxBy { it.height }
      car.x = tallestPipe.x - car.width - 10f
      car.y = LevelGenerator.GROUND_Y - car.height
      car.onGround = true
      car.vx = vehicle.maxSpeed * 0.8f

      // Hold UP + RIGHT diagonally like thumb sliding on 4-way D-pad
      engine.inputRight = true
      engine.inputUp = true
      repeat(90) { engine.step(0.016f, 1280f) }
      engine.inputRight = false
      engine.inputUp = false

      assertTrue(
        "${vehicle.displayName} got stuck behind tallest pipe (car.x=${car.x}, pipeEnd=${tallestPipe.x + tallestPipe.width})",
        car.x > tallestPipe.x + tallestPipe.width,
      )
    }
  }

  @Test
  fun missileLauncherPowerUp_firesHomingSplashMissileAndTriggersSoundCallbacks() {
    var missileCalls = 0
    var explosionCalls = 0
    var powerUpCalls = 0
    val listener =
      object : GameSoundListener {
        override fun onJump() = Unit
        override fun onShoot() = Unit
        override fun onCoin() = Unit
        override fun onPowerUp() { powerUpCalls++ }
        override fun onEnemyDefeat() = Unit
        override fun onBlockHit() = Unit
        override fun onCarEnter() = Unit
        override fun onDamage() = Unit
        override fun onWantedUp() = Unit
        override fun onVictory() = Unit
        override fun onBusted() = Unit
        override fun onMissileLaunch() { missileCalls++ }
        override fun onExplosion() { explosionCalls++ }
      }

    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
        soundListener = listener,
      )

    engine.pickups.add(
      WorldPickup(
        id = 99901,
        kind = PickupKind.MISSILE_LAUNCHER,
        x = engine.player.x + engine.player.width * 0.5f,
        y = engine.player.y + engine.player.height * 0.5f,
      )
    )
    engine.step(0.016f, 1280f)

    assertEquals(WeaponPowerUp.MISSILE_LAUNCHER, engine.hudSnapshot.activeWeaponPowerUp)
    assertTrue(powerUpCalls >= 1)

    // Place two clustered Goombas within splash radius in front of player
    engine.enemies.clear()
    val g1 =
      EnemyEntity(
        id = 99902,
        kind = EnemyKind.GOOMBA,
        x = engine.player.x + 260f,
        y = LevelGenerator.GROUND_Y - 40f,
        vx = 0f,
        vy = 0f,
        width = 38f,
        height = 40f,
        hp = 2,
        maxHp = 2,
      )
    val g2 =
      EnemyEntity(
        id = 99903,
        kind = EnemyKind.GOOMBA,
        x = engine.player.x + 305f,
        y = LevelGenerator.GROUND_Y - 40f,
        vx = 0f,
        vy = 0f,
        width = 38f,
        height = 40f,
        hp = 2,
        maxHp = 2,
      )
    engine.enemies.addAll(listOf(g1, g2))

    engine.fireAtScreenWorldPoint(g1.x + 18f, g1.y + 18f)
    assertTrue(missileCalls >= 1)
    assertTrue(engine.projectiles.any { it.style == ProjectileStyle.MISSILE && it.homing })

    repeat(60) { engine.step(0.016f, 1280f) }

    assertFalse(g1.alive)
    assertFalse(g2.alive)
    assertTrue(explosionCalls >= 1)
  }

  @Test
  fun shotgunAndMinigunPowerUps_produceDistinctWeaponPatterns() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
      )
    engine.enemies.clear()
    engine.blocks.clear()

    // Collect Spread Shotgun -> 5 pellets in one trigger
    engine.pickups.add(
      WorldPickup(
        id = 99911,
        kind = PickupKind.SPREAD_SHOTGUN,
        x = engine.player.x + 12f,
        y = engine.player.y + 20f,
      )
    )
    engine.step(0.016f, 1280f)
    assertEquals(WeaponPowerUp.SPREAD_SHOTGUN, engine.hudSnapshot.activeWeaponPowerUp)

    engine.projectiles.clear()
    engine.fireAtScreenWorldPoint(engine.player.x + 400f, engine.player.y)
    assertEquals(5, engine.projectiles.count { it.style == ProjectileStyle.SHOTGUN_PELLET })

    // Collect Rapid Minigun -> high fire rate rounds
    engine.pickups.add(
      WorldPickup(
        id = 99912,
        kind = PickupKind.RAPID_MINIGUN,
        x = engine.player.x + 12f,
        y = engine.player.y + 20f,
      )
    )
    engine.step(0.016f, 1280f)
    assertEquals(WeaponPowerUp.RAPID_MINIGUN, engine.hudSnapshot.activeWeaponPowerUp)

    engine.projectiles.clear()
    engine.inputFireHeld = true
    repeat(30) { engine.step(0.016f, 1280f) }
    engine.inputFireHeld = false
    assertTrue(engine.projectiles.count { it.style == ProjectileStyle.MINIGUN_ROUND } >= 4)
  }

  @Test
  fun starPowerAndArmorShield_protectPlayerAndMagnetizeCoins() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
      )

    // Collect Armor Shield + verify coin magnet pulls nearby coin and absorbs hit
    engine.pickups.add(
      WorldPickup(
        id = 99921,
        kind = PickupKind.ARMOR_SHIELD,
        x = engine.player.x + 14f,
        y = engine.player.y + 20f,
      )
    )
    val nearbyCoin =
      WorldPickup(
        id = 99922,
        kind = PickupKind.COIN,
        x = engine.player.x + 150f,
        y = engine.player.y + 20f,
      )
    engine.pickups.add(nearbyCoin)
    val startCoins = engine.coins
    repeat(30) { engine.step(0.016f, 1280f) }
    assertTrue(engine.hudSnapshot.shieldCharges > 0)
    assertTrue(nearbyCoin.collected)
    assertTrue(engine.coins > startCoins)

    // Collect Star Power -> instant touch KO without losing player HP
    engine.pickups.add(
      WorldPickup(
        id = 99923,
        kind = PickupKind.STAR_POWER,
        x = engine.player.x + 14f,
        y = engine.player.y + 20f,
      )
    )
    engine.step(0.016f, 1280f)
    assertTrue(engine.hudSnapshot.starSecondsLeft > 0)

    val hpBefore = engine.player.hp
    val targetEnemy =
      EnemyEntity(
        id = 99924,
        kind = EnemyKind.KOOPA,
        x = engine.player.x + 10f,
        y = engine.player.y,
        vx = 0f,
        vy = 0f,
        width = 40f,
        height = 50f,
        hp = 4,
        maxHp = 4,
      )
    engine.enemies.add(targetEnemy)
    engine.step(0.016f, 1280f)

    assertFalse(targetEnemy.alive)
    assertEquals(hpBefore, engine.player.hp)
  }

  @Test
  fun jumpAndMoveForwardSimultaneously_launchesUpAndForwardImmediately() {
    val engine =
      GameEngine(
        worldTheme = WorldTheme.TOADSTOOL_STRIP,
        equippedVehicle = VehicleSpec.KART_RED,
        weaponSpec = WeaponSpec.FIRE_BLASTER,
      )

    val startX = engine.player.x
    val startY = engine.player.y

    // Hold Right + Jump Button simultaneously from standstill
    engine.inputRight = true
    engine.inputJumpHeld = true
    engine.step(0.016f, 1280f)

    assertTrue("Player should launch upward immediately", engine.player.vy < -500f && engine.player.y < startY)
    assertTrue("Player should move forward immediately while jumping", engine.player.vx >= 200f && engine.player.x > startX)

    // Keep moving forward while airborne
    repeat(20) { engine.step(0.016f, 1280f) }
    assertTrue("Player should travel significantly forward mid-jump", engine.player.x > startX + 80f)
  }
}
