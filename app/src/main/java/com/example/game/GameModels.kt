package com.example.game

import com.example.data.VehicleSpec
import com.example.data.WeaponSpec

enum class WorldTheme(
  val worldId: Int,
  val title: String,
  val subtitle: String,
) {
  TOADSTOOL_STRIP(
    worldId = 1,
    title = "1-1 Toadstool Strip",
    subtitle = "Sunny pipes, brick blocks & Mushroom PD patrols",
  ),
  KOOPA_VICE_HWY(
    worldId = 2,
    title = "1-2 Koopa Vice Hwy",
    subtitle = "Neon sunset skyline, elevated overpasses & paratroopas",
  ),
  BOWSER_BLVD(
    worldId = 3,
    title = "1-3 Bowser Blvd",
    subtitle = "Molten fortress highway, heavy cruisers & elite squads",
  );

  companion object {
    fun byId(id: Int): WorldTheme = entries.firstOrNull { it.worldId == id } ?: TOADSTOOL_STRIP
  }
}

enum class BlockKind {
  BRICK,
  QUESTION_COIN,
  QUESTION_SHROOM,
  QUESTION_STAR,
  QUESTION_FIRE,
  QUESTION_SHIELD,
  QUESTION_MISSILE,
  QUESTION_SHOTGUN,
  QUESTION_MINIGUN,
  QUESTION_NITRO,
  SOLID_STEP,
}

data class BlockTile(
  val id: Int,
  val x: Float,
  val y: Float,
  val size: Float = 48f,
  val kind: BlockKind,
  var used: Boolean = false,
  var bumpOffset: Float = 0f,
  var broken: Boolean = false,
)

data class GroundSegment(
  val startX: Float,
  val endX: Float,
)

data class WarpPipe(
  val x: Float,
  val width: Float = 72f,
  val height: Float,
  val isRamp: Boolean = false,
)

enum class EnemyKind {
  GOOMBA,
  KOOPA,
  PARATROOPA,
  CHOPPER_BOSS,
}

data class EnemyEntity(
  val id: Int,
  val kind: EnemyKind,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var width: Float,
  var height: Float,
  var hp: Int,
  val maxHp: Int,
  var facingRight: Boolean = false,
  var baseY: Float = y,
  var animTime: Float = 0f,
  var shootCooldown: Float = 1.5f,
  var hitFlash: Float = 0f,
  var alive: Boolean = true,
)

data class WorldCar(
  val id: Int,
  val spec: VehicleSpec,
  val isPolice: Boolean = false,
  var x: Float,
  var y: Float,
  var vx: Float = 0f,
  var vy: Float = 0f,
  val width: Float = 112f,
  val height: Float = 54f,
  var hp: Int = spec.armorHp,
  val maxHp: Int = spec.armorHp,
  var facingRight: Boolean = true,
  var wheelSpin: Float = 0f,
  var onGround: Boolean = true,
  var isOccupiedByPlayer: Boolean = false,
  var hasPoliceDriver: Boolean = false,
  var sirenTimer: Float = 0f,
  var policeShootTimer: Float = 1.4f,
  var wrecked: Boolean = false,
)

enum class ProjectileStyle {
  FIREBALL,
  STAR_BOLT,
  SHELL_ORB,
  MISSILE,
  SHOTGUN_PELLET,
  MINIGUN_ROUND,
  ENEMY_BOLT,
}

data class Projectile(
  val id: Int,
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  val fromPlayer: Boolean,
  val style: ProjectileStyle,
  val damage: Int,
  val maxRange: Float,
  val splashRadius: Float = 0f,
  val homing: Boolean = false,
  var distanceTraveled: Float = 0f,
  var active: Boolean = true,
)

enum class PickupKind {
  COIN,
  SUPER_SHROOM,
  CLEAR_WANTED_STAR,
  TURBO_NITRO,
  STAR_POWER,
  ARMOR_SHIELD,
  FIRE_FLOWER,
  MISSILE_LAUNCHER,
  SPREAD_SHOTGUN,
  RAPID_MINIGUN,
}

enum class WeaponPowerUp(
  val label: String,
) {
  NONE(""),
  FIRE_OVERDRIVE("FIRE"),
  MISSILE_LAUNCHER("RPG MISSILE"),
  SPREAD_SHOTGUN("SHOTGUN"),
  RAPID_MINIGUN("MINIGUN"),
}

data class WorldPickup(
  val id: Int,
  val kind: PickupKind,
  var x: Float,
  var y: Float,
  var vy: Float = 0f,
  var animTime: Float = 0f,
  var collected: Boolean = false,
)

data class Particle(
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var life: Float,
  val maxLife: Float,
  val size: Float,
  val colorArgb: Long,
  val text: String? = null,
)

data class PlayerState(
  var x: Float = 120f,
  var y: Float = 524f,
  var vx: Float = 0f,
  var vy: Float = 0f,
  val width: Float = 38f,
  val height: Float = 52f,
  var facingRight: Boolean = true,
  var onGround: Boolean = true,
  var hp: Int = 6,
  val maxHp: Int = 6,
  var invulnTimer: Float = 0f,
  var turboTimer: Float = 0f,
  var starPowerTimer: Float = 0f,
  var shieldCharges: Int = 0,
  var shieldTimer: Float = 0f,
  var activeWeaponPowerUp: WeaponPowerUp = WeaponPowerUp.NONE,
  var weaponPowerUpTimer: Float = 0f,
  var drivenCarId: Int? = null,
  var walkAnim: Float = 0f,
  var isCrouching: Boolean = false,
)

enum class GameOutcome {
  PLAYING,
  PAUSED,
  VICTORY,
  BUSTED,
}

data class HudSnapshot(
  val worldTheme: WorldTheme = WorldTheme.TOADSTOOL_STRIP,
  val score: Int = 0,
  val coins: Int = 0,
  val enemiesDefeated: Int = 0,
  val hp: Int = 6,
  val maxHp: Int = 6,
  val carHp: Int? = null,
  val carMaxHp: Int? = null,
  val carName: String? = null,
  val canEnterCar: Boolean = false,
  val nearPoliceCar: Boolean = false,
  val inCar: Boolean = false,
  val wantedStars: Int = 0,
  val maxWantedStarsReached: Int = 0,
  val weaponSpec: WeaponSpec = WeaponSpec.FIRE_BLASTER,
  val progressFraction: Float = 0f,
  val hasTargetLock: Boolean = false,
  val outcome: GameOutcome = GameOutcome.PLAYING,
  val statusBanner: String? = null,
  val starSecondsLeft: Int = 0,
  val nitroSecondsLeft: Int = 0,
  val shieldCharges: Int = 0,
  val activeWeaponPowerUp: WeaponPowerUp = WeaponPowerUp.NONE,
  val weaponPowerUpSecondsLeft: Int = 0,
)
