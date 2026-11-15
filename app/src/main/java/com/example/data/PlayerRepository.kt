package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PlayerRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context
  ) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeProfile(userId: String): Flow<PlayerProfile?> = flow {
    val uid = requireUserId()
    require(uid == userId) { "Authenticated user mismatch" }
    val docRef = db.collection("users").document(uid)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              PlayerProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
          }
          throw error
        }
    )
  }

  fun observeRuns(userId: String): Flow<List<LevelRun>> = flow {
    val uid = requireUserId()
    require(uid == userId) { "Authenticated user mismatch" }
    val colRef = db.collection("users").document(uid).collection("runs")
    emitAll(
      colRef
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(15)
        .snapshots()
        .map { querySnapshot ->
          querySnapshot.toObjects(
            LevelRun::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
          }
          throw error
        }
    )
  }

  suspend fun getProfile(userId: String = requireUserId()): Result<PlayerProfile?> {
    val docRef = db.collection("users").document(userId)
    return try {
      val snapshot = docRef.get().await()
      val profile =
        if (snapshot.exists()) {
          snapshot.toObject(
            PlayerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      Result.success(profile)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun ensureProfileExists(displayName: String): Result<PlayerProfile> {
    val uid = requireUserId()
    val safeName = displayName.trim().ifBlank { "Plumber" }.take(50)
    val docRef = db.collection("users").document(uid)
    return try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        val loaded =
          existing.toObject(
            PlayerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: PlayerProfile(id = uid, userId = uid, displayName = safeName)
        return Result.success(loaded)
      }
      val payload =
        mapOf(
          "userId" to uid,
          "displayName" to safeName,
          "totalCoins" to 0L,
          "highScore" to 0L,
          "highestWorldUnlocked" to 1L,
          "equippedVehicle" to VehicleCatalog.KART_RED.id,
          "equippedWeapon" to WeaponCatalog.FIRE_BLASTER.id,
          "unlockedVehicles" to listOf(VehicleCatalog.KART_RED.id),
          "unlockedWeapons" to listOf(WeaponCatalog.FIRE_BLASTER.id),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      val created =
        docRef
          .get()
          .await()
          .toObject(
            PlayerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          ) ?: PlayerProfile(id = uid, userId = uid, displayName = safeName)
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordRunAndUpdateCareer(
    worldId: Int,
    worldName: String,
    score: Int,
    coinsCollected: Int,
    enemiesDefeated: Int,
    maxWantedStars: Int,
    cleared: Boolean,
    vehicleUsed: String,
    weaponUsed: String,
    displayName: String,
  ): Result<String> {
    val uid = requireUserId()
    val runId = "run_${UUID.randomUUID().toString().replace("-", "")}"
    val runRef = db.collection("users").document(uid).collection("runs").document(runId)
    val profileRef = db.collection("users").document(uid)
    return try {
      ensureProfileExists(displayName).getOrThrow()

      val runPayload =
        mapOf(
          "userId" to uid,
          "worldId" to worldId.coerceIn(1, 3).toLong(),
          "worldName" to worldName.trim().ifBlank { "World 1-1" }.take(60),
          "score" to score.coerceAtLeast(0).toLong(),
          "coinsCollected" to coinsCollected.coerceAtLeast(0).toLong(),
          "enemiesDefeated" to enemiesDefeated.coerceAtLeast(0).toLong(),
          "maxWantedStars" to maxWantedStars.coerceIn(0, 5).toLong(),
          "cleared" to cleared,
          "vehicleUsed" to VehicleCatalog.byId(vehicleUsed).id,
          "weaponUsed" to WeaponCatalog.byId(weaponUsed).id,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      runRef.set(runPayload).await()

      val current =
        profileRef
          .get()
          .await()
          .toObject(
            PlayerProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
      if (current != null) {
        val nextUnlocked =
          if (cleared) maxOf(current.highestWorldUnlocked, (worldId + 1).coerceAtMost(3).toLong())
          else current.highestWorldUnlocked
        val nextCoins = (current.totalCoins + coinsCollected.coerceAtLeast(0)).coerceAtMost(9999999L)
        val nextHighScore = maxOf(current.highScore, score.coerceAtLeast(0).toLong())
        profileRef
          .update(
            mapOf(
              "totalCoins" to nextCoins,
              "highScore" to nextHighScore,
              "highestWorldUnlocked" to nextUnlocked,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(runId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, runRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateLoadout(
    totalCoins: Long,
    equippedVehicle: String,
    equippedWeapon: String,
    unlockedVehicles: List<String>,
    unlockedWeapons: List<String>,
  ): Result<Unit> {
    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    return try {
      profileRef
        .update(
          mapOf(
            "totalCoins" to totalCoins.coerceIn(0L, 9999999L),
            "equippedVehicle" to VehicleCatalog.byId(equippedVehicle).id,
            "equippedWeapon" to WeaponCatalog.byId(equippedWeapon).id,
            "unlockedVehicles" to unlockedVehicles.distinct().take(5),
            "unlockedWeapons" to unlockedWeapons.distinct().take(5),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      Result.failure(e)
    }
  }

  suspend fun getRun(runId: String, userId: String = requireUserId()): Result<LevelRun?> {
    val docRef = db.collection("users").document(userId).collection("runs").document(runId)
    return try {
      val snapshot = docRef.get().await()
      val run =
        if (snapshot.exists()) {
          snapshot.toObject(
            LevelRun::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      Result.success(run)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }
}
