package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PlayerProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Plumber",
  val totalCoins: Long = 0L,
  val highScore: Long = 0L,
  val highestWorldUnlocked: Long = 1L,
  val equippedVehicle: String = VehicleCatalog.KART_RED.id,
  val equippedWeapon: String = WeaponCatalog.FIRE_BLASTER.id,
  val unlockedVehicles: List<String> = listOf(VehicleCatalog.KART_RED.id),
  val unlockedWeapons: List<String> = listOf(WeaponCatalog.FIRE_BLASTER.id),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LevelRun(
  @DocumentId val id: String = "",
  val userId: String = "",
  val worldId: Long = 1L,
  val worldName: String = "",
  val score: Long = 0L,
  val coinsCollected: Long = 0L,
  val enemiesDefeated: Long = 0L,
  val maxWantedStars: Long = 0L,
  val cleared: Boolean = false,
  val vehicleUsed: String = VehicleCatalog.KART_RED.id,
  val weaponUsed: String = WeaponCatalog.FIRE_BLASTER.id,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class VehicleSpec(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val maxSpeed: Float,
  val acceleration: Float,
  val jumpImpulse: Float,
  val armorHp: Int,
  val costCoins: Int,
) {
  KART_RED(
    id = "kart_red",
    displayName = "Pipe Roadster",
    subtitle = "Balanced crimson coupe with turbo boost",
    maxSpeed = 620f,
    acceleration = 1120f,
    jumpImpulse = -645f,
    armorHp = 4,
    costCoins = 0,
  ),
  MUSCLE_GOLD(
    id = "muscle_gold",
    displayName = "Starlight GT",
    subtitle = "High-velocity golden V8 interceptor",
    maxSpeed = 790f,
    acceleration = 1380f,
    jumpImpulse = -680f,
    armorHp = 5,
    costCoins = 40,
  ),
  KOOPA_TANK(
    id = "koopa_tank",
    displayName = "Shell Cruiser",
    subtitle = "Heavy armored rig built to ram patrols",
    maxSpeed = 560f,
    acceleration = 980f,
    jumpImpulse = -615f,
    armorHp = 8,
    costCoins = 85,
  ),
}

object VehicleCatalog {
  val KART_RED = VehicleSpec.KART_RED
  val MUSCLE_GOLD = VehicleSpec.MUSCLE_GOLD
  val KOOPA_TANK = VehicleSpec.KOOPA_TANK

  val all: List<VehicleSpec> = VehicleSpec.entries

  fun byId(id: String): VehicleSpec = all.firstOrNull { it.id == id } ?: KART_RED
}

enum class WeaponSpec(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val rangePx: Float,
  val projectileSpeed: Float,
  val fireCooldownMs: Long,
  val damage: Int,
  val burstCount: Int,
  val costCoins: Int,
) {
  FIRE_BLASTER(
    id = "fire_blaster",
    displayName = "Fire Blaster",
    subtitle = "Long-range rapid plasma fireballs",
    rangePx = 1150f,
    projectileSpeed = 1280f,
    fireCooldownMs = 210L,
    damage = 1,
    burstCount = 1,
    costCoins = 0,
  ),
  STAR_RIFLE(
    id = "star_rifle",
    displayName = "Star Sniper",
    subtitle = "Ultra-far precision star bolts",
    rangePx = 1650f,
    projectileSpeed = 1780f,
    fireCooldownMs = 260L,
    damage = 2,
    burstCount = 1,
    costCoins = 35,
  ),
  SHELL_CANNON(
    id = "shell_cannon",
    displayName = "Triple Launcher",
    subtitle = "Long-range tri-spread shell barrage",
    rangePx = 1350f,
    projectileSpeed = 1380f,
    fireCooldownMs = 340L,
    damage = 2,
    burstCount = 3,
    costCoins = 75,
  ),
}

object WeaponCatalog {
  val FIRE_BLASTER = WeaponSpec.FIRE_BLASTER
  val STAR_RIFLE = WeaponSpec.STAR_RIFLE
  val SHELL_CANNON = WeaponSpec.SHELL_CANNON

  val all: List<WeaponSpec> = WeaponSpec.entries

  fun byId(id: String): WeaponSpec = all.firstOrNull { it.id == id } ?: FIRE_BLASTER
}
