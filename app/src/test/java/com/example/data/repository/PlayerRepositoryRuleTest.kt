package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.PlayerRepository
import com.example.data.VehicleCatalog
import com.example.data.WeaponCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureProfileAndRecordRun_authenticatedOwner_succeedsAndUpdatesCareer() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = PlayerRepository(firestore, auth)

    val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureProfileExists("MarioDriver").getOrThrow() }
    assertEquals(uid, profile.userId)
    assertEquals("MarioDriver", profile.displayName)

    val runId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo
          .recordRunAndUpdateCareer(
            worldId = 1,
            worldName = "1-1 Toadstool Strip",
            score = 6400,
            coinsCollected = 25,
            enemiesDefeated = 12,
            maxWantedStars = 3,
            cleared = true,
            vehicleUsed = VehicleCatalog.KART_RED.id,
            weaponUsed = WeaponCatalog.FIRE_BLASTER.id,
            displayName = "MarioDriver",
          )
          .getOrThrow()
      }
    assertTrue(runId.isNotBlank())

    val emittedRuns =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeRuns(uid).first { runs -> runs.any { it.id == runId } }
      }
    assertTrue(emittedRuns.any { it.id == runId && it.score == 6400L })

    val updatedProfile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getProfile(uid).getOrThrow() }
    assertNotNull(updatedProfile)
    assertTrue((updatedProfile?.totalCoins ?: 0L) >= 25L)
    assertTrue((updatedProfile?.highestWorldUnlocked ?: 1L) >= 2L)
  }

  @Test
  fun getProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = PlayerRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureProfileExists("Alice").getOrThrow() }

    signInTestUser(BOB_EMAIL)
    val bobRepo = PlayerRepository(firestore, auth)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getProfile(aliceUid) }
    assertTrue(result.isFailure)
    val fsError = result.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(fsError)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
  }

  @Test
  fun getProfile_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = PlayerRepository(firestore, auth)
    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getProfile("some_uid") }
    assertTrue(result.isFailure)
    val fsError = result.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(fsError)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
  }

  @Test
  fun updateLoadout_validSelection_persistsEquippedVehicleAndWeapon() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = PlayerRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureProfileExists("MarioDriver").getOrThrow() }

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.updateLoadout(
          totalCoins = 100L,
          equippedVehicle = VehicleCatalog.MUSCLE_GOLD.id,
          equippedWeapon = WeaponCatalog.STAR_RIFLE.id,
          unlockedVehicles = listOf(VehicleCatalog.KART_RED.id, VehicleCatalog.MUSCLE_GOLD.id),
          unlockedWeapons = listOf(WeaponCatalog.FIRE_BLASTER.id, WeaponCatalog.STAR_RIFLE.id),
        )
      }
    assertTrue(updateResult.isSuccess)

    val loaded = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getProfile(uid).getOrThrow() }
    assertEquals(VehicleCatalog.MUSCLE_GOLD.id, loaded?.equippedVehicle)
    assertEquals(WeaponCatalog.STAR_RIFLE.id, loaded?.equippedWeapon)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_shroom@test.com"
    const val BOB_EMAIL = "bob_shroom@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
