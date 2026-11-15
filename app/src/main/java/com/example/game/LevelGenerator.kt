package com.example.game

import com.example.data.VehicleSpec

object LevelGenerator {
  const val GROUND_Y = 580f
  const val WORLD_HEIGHT = 720f
  const val FLAGPOLE_X = 7160f
  const val WORLD_LENGTH = 7600f

  data class GeneratedLevel(
    val groundSegments: List<GroundSegment>,
    val pipes: List<WarpPipe>,
    val blocks: MutableList<BlockTile>,
    val cars: MutableList<WorldCar>,
    val enemies: MutableList<EnemyEntity>,
    val pickups: MutableList<WorldPickup>,
  )

  fun buildLevel(world: WorldTheme, equippedVehicle: VehicleSpec): GeneratedLevel {
    val groundSegments =
      when (world) {
        WorldTheme.TOADSTOOL_STRIP ->
          listOf(
            GroundSegment(0f, 2270f),
            GroundSegment(2395f, 4570f),
            GroundSegment(4700f, WORLD_LENGTH),
          )
        WorldTheme.KOOPA_VICE_HWY ->
          listOf(
            GroundSegment(0f, 1875f),
            GroundSegment(2020f, 3625f),
            GroundSegment(3780f, 5625f),
            GroundSegment(5775f, WORLD_LENGTH),
          )
        WorldTheme.BOWSER_BLVD ->
          listOf(
            GroundSegment(0f, 1625f),
            GroundSegment(1785f, 3275f),
            GroundSegment(3440f, 4975f),
            GroundSegment(5140f, WORLD_LENGTH),
          )
      }

    val pipes =
      mutableListOf(
        WarpPipe(x = 680f, height = 74f, isRamp = true),
        WarpPipe(x = 1320f, height = 98f, isRamp = false),
        WarpPipe(x = 2720f, height = 84f, isRamp = true),
        WarpPipe(x = 3980f, height = 108f, isRamp = false),
        WarpPipe(x = 5320f, height = 82f, isRamp = true),
        WarpPipe(x = 6350f, height = 96f, isRamp = false),
      )

    val blocks = mutableListOf<BlockTile>()
    var blockId = 1
    fun addBlockRow(startX: Float, y: Float, kinds: List<BlockKind>) {
      kinds.forEachIndexed { idx, kind ->
        blocks.add(BlockTile(id = blockId++, x = startX + idx * 50f, y = y, kind = kind))
      }
    }

    addBlockRow(
      460f,
      415f,
      listOf(BlockKind.BRICK, BlockKind.QUESTION_FIRE, BlockKind.BRICK, BlockKind.QUESTION_MISSILE),
    )
    addBlockRow(
      980f,
      380f,
      listOf(BlockKind.QUESTION_COIN, BlockKind.QUESTION_SHOTGUN, BlockKind.QUESTION_SHROOM),
    )
    addBlockRow(
      1660f,
      410f,
      listOf(BlockKind.BRICK, BlockKind.QUESTION_STAR, BlockKind.QUESTION_MINIGUN, BlockKind.QUESTION_COIN),
    )
    addBlockRow(
      2220f,
      430f,
      listOf(BlockKind.SOLID_STEP, BlockKind.SOLID_STEP, BlockKind.SOLID_STEP),
    )
    addBlockRow(
      3050f,
      390f,
      listOf(
        BlockKind.BRICK,
        BlockKind.QUESTION_SHIELD,
        BlockKind.QUESTION_MISSILE,
        BlockKind.BRICK,
        BlockKind.QUESTION_COIN,
      ),
    )
    addBlockRow(
      4220f,
      370f,
      listOf(BlockKind.QUESTION_COIN, BlockKind.QUESTION_MINIGUN, BlockKind.QUESTION_STAR, BlockKind.BRICK),
    )
    addBlockRow(
      5000f,
      410f,
      listOf(BlockKind.BRICK, BlockKind.QUESTION_SHROOM, BlockKind.QUESTION_SHOTGUN, BlockKind.QUESTION_MISSILE),
    )
    addBlockRow(
      6040f,
      385f,
      listOf(
        BlockKind.QUESTION_SHIELD,
        BlockKind.QUESTION_COIN,
        BlockKind.QUESTION_FIRE,
        BlockKind.BRICK,
      ),
    )

    // Pre-flagpole stair blocks
    for (step in 0 until 4) {
      for (col in step until 4) {
        blocks.add(
          BlockTile(
            id = blockId++,
            x = 6740f + col * 48f,
            y = GROUND_Y - (step + 1) * 48f,
            kind = BlockKind.SOLID_STEP,
          )
        )
      }
    }

    val cars =
      mutableListOf(
        WorldCar(
          id = 1,
          spec = equippedVehicle,
          x = 240f,
          y = GROUND_Y - 54f,
          facingRight = true,
        ),
        WorldCar(
          id = 2,
          spec = VehicleSpec.MUSCLE_GOLD,
          x = 1920f,
          y = GROUND_Y - 54f,
          facingRight = true,
        ),
        WorldCar(
          id = 3,
          spec = VehicleSpec.KART_RED,
          x = 3450f,
          y = GROUND_Y - 54f,
          facingRight = true,
        ),
        WorldCar(
          id = 4,
          spec = VehicleSpec.KOOPA_TANK,
          x = 4920f,
          y = GROUND_Y - 54f,
          facingRight = true,
        ),
        WorldCar(
          id = 5,
          spec = VehicleSpec.MUSCLE_GOLD,
          x = 6120f,
          y = GROUND_Y - 54f,
          facingRight = true,
        ),
      )

    val enemies = mutableListOf<EnemyEntity>()
    var enemyId = 1
    val goombaXs = listOf(840f, 1220f, 1580f, 2580f, 3020f, 3440f, 4180f, 4920f, 5620f, 6460f)
    goombaXs.forEach { gx ->
      val hp = if (world == WorldTheme.BOWSER_BLVD) 2 else 1
      enemies.add(
        EnemyEntity(
          id = enemyId++,
          kind = EnemyKind.GOOMBA,
          x = gx,
          y = GROUND_Y - 38f,
          vx = -70f - world.worldId * 12f,
          vy = 0f,
          width = 38f,
          height = 38f,
          hp = hp,
          maxHp = hp,
        )
      )
    }

    val koopaXs = listOf(1080f, 1820f, 2860f, 3620f, 4420f, 5260f, 5980f, 6620f)
    koopaXs.forEach { kx ->
      val hp = world.worldId.coerceAtMost(3)
      enemies.add(
        EnemyEntity(
          id = enemyId++,
          kind = EnemyKind.KOOPA,
          x = kx,
          y = GROUND_Y - 48f,
          vx = -88f - world.worldId * 14f,
          vy = 0f,
          width = 40f,
          height = 48f,
          hp = hp,
          maxHp = hp,
        )
      )
    }

    val paraXs = listOf(1480f, 2400f, 3240f, 3960f, 4740f, 5520f, 6260f)
    paraXs.forEachIndexed { idx, px ->
      val hp = if (world == WorldTheme.BOWSER_BLVD) 2 else 1
      enemies.add(
        EnemyEntity(
          id = enemyId++,
          kind = EnemyKind.PARATROOPA,
          x = px,
          y = 250f + (idx % 3) * 42f,
          vx = -60f,
          vy = 0f,
          width = 44f,
          height = 44f,
          hp = hp,
          maxHp = hp,
          baseY = 250f + (idx % 3) * 42f,
          animTime = idx * 0.9f,
        )
      )
    }

    if (world != WorldTheme.TOADSTOOL_STRIP) {
      enemies.add(
        EnemyEntity(
          id = enemyId++,
          kind = EnemyKind.CHOPPER_BOSS,
          x = 5880f,
          y = 195f,
          vx = -80f,
          vy = 0f,
          width = 76f,
          height = 48f,
          hp = 5 + world.worldId * 2,
          maxHp = 5 + world.worldId * 2,
          baseY = 195f,
        )
      )
    }

    val pickups = mutableListOf<WorldPickup>()
    var pickupId = 1
    val coinArcs = listOf(560f, 1020f, 1690f, 2330f, 3140f, 3700f, 4300f, 5140f, 5700f, 6400f)
    coinArcs.forEach { cx ->
      for (i in 0 until 4) {
        pickups.add(
          WorldPickup(
            id = pickupId++,
            kind = PickupKind.COIN,
            x = cx + i * 44f,
            y = GROUND_Y - 95f - (if (i == 1 || i == 2) 26f else 0f),
          )
        )
      }
    }

    // Weapon crates, Pay'n'Spray stars & Nitro power-ups along the highway
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.SPREAD_SHOTGUN, x = 520f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.MISSILE_LAUNCHER, x = 920f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.TURBO_NITRO, x = 1580f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.ARMOR_SHIELD, x = 2120f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.RAPID_MINIGUN, x = 2660f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.CLEAR_WANTED_STAR, x = 3300f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.STAR_POWER, x = 3880f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.SUPER_SHROOM, x = 4480f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.MISSILE_LAUNCHER, x = 5080f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.CLEAR_WANTED_STAR, x = 5760f, y = GROUND_Y - 52f))
    pickups.add(WorldPickup(id = pickupId++, kind = PickupKind.RAPID_MINIGUN, x = 6260f, y = GROUND_Y - 52f))

    return GeneratedLevel(
      groundSegments = groundSegments,
      pipes = pipes,
      blocks = blocks,
      cars = cars,
      enemies = enemies,
      pickups = pickups,
    )
  }
}
