package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LevelRun
import com.example.data.PlayerProfile
import com.example.data.PlayerRepository
import com.example.data.VehicleCatalog
import com.example.data.VehicleSpec
import com.example.data.WeaponCatalog
import com.example.data.WeaponSpec
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

class CareerViewModel(
  private val repository: PlayerRepository,
  private val currentUserId: String,
  private val initialDisplayName: String,
) : ViewModel() {

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  val profileState: StateFlow<UiState<PlayerProfile>> =
    repository
      .observeProfile(currentUserId)
      .map<PlayerProfile?, UiState<PlayerProfile>> { profile ->
        UiState.Success(
          profile
            ?: PlayerProfile(
              id = currentUserId,
              userId = currentUserId,
              displayName = initialDisplayName,
            )
        )
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing profile", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load career profile"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val runsState: StateFlow<UiState<List<LevelRun>>> =
    repository
      .observeRuns(currentUserId)
      .map<List<LevelRun>, UiState<List<LevelRun>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing runs", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load run history"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      repository.ensureProfileExists(initialDisplayName).onFailure { err ->
        _actionError.value = err.localizedMessage
      }
    }
  }

  fun clearActionError() {
    _actionError.value = null
  }

  fun selectOrBuyVehicle(profile: PlayerProfile, vehicle: VehicleSpec) {
    viewModelScope.launch {
      val alreadyUnlocked = profile.unlockedVehicles.contains(vehicle.id)
      if (!alreadyUnlocked && profile.totalCoins < vehicle.costCoins) {
        _actionError.value = "Need ${vehicle.costCoins} coins to unlock ${vehicle.displayName}."
        return@launch
      }
      val nextCoins =
        if (alreadyUnlocked) profile.totalCoins else profile.totalCoins - vehicle.costCoins
      val nextUnlocked =
        if (alreadyUnlocked) profile.unlockedVehicles
        else (profile.unlockedVehicles + vehicle.id).distinct()
      repository
        .updateLoadout(
          totalCoins = nextCoins,
          equippedVehicle = vehicle.id,
          equippedWeapon = profile.equippedWeapon,
          unlockedVehicles = nextUnlocked,
          unlockedWeapons = profile.unlockedWeapons,
        )
        .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update garage" }
    }
  }

  fun selectOrBuyWeapon(profile: PlayerProfile, weapon: WeaponSpec) {
    viewModelScope.launch {
      val alreadyUnlocked = profile.unlockedWeapons.contains(weapon.id)
      if (!alreadyUnlocked && profile.totalCoins < weapon.costCoins) {
        _actionError.value = "Need ${weapon.costCoins} coins to unlock ${weapon.displayName}."
        return@launch
      }
      val nextCoins =
        if (alreadyUnlocked) profile.totalCoins else profile.totalCoins - weapon.costCoins
      val nextUnlocked =
        if (alreadyUnlocked) profile.unlockedWeapons
        else (profile.unlockedWeapons + weapon.id).distinct()
      repository
        .updateLoadout(
          totalCoins = nextCoins,
          equippedVehicle = profile.equippedVehicle,
          equippedWeapon = weapon.id,
          unlockedVehicles = profile.unlockedVehicles,
          unlockedWeapons = nextUnlocked,
        )
        .onFailure { _actionError.value = it.localizedMessage ?: "Failed to update armory" }
    }
  }

  fun recordRun(
    worldId: Int,
    worldName: String,
    score: Int,
    coinsCollected: Int,
    enemiesDefeated: Int,
    maxWantedStars: Int,
    cleared: Boolean,
    vehicleUsed: VehicleSpec,
    weaponUsed: WeaponSpec,
  ) {
    viewModelScope.launch {
      repository
        .recordRunAndUpdateCareer(
          worldId = worldId,
          worldName = worldName,
          score = score,
          coinsCollected = coinsCollected,
          enemiesDefeated = enemiesDefeated,
          maxWantedStars = maxWantedStars,
          cleared = cleared,
          vehicleUsed = vehicleUsed.id,
          weaponUsed = weaponUsed.id,
          displayName = initialDisplayName,
        )
        .onFailure { _actionError.value = it.localizedMessage ?: "Failed to save run" }
    }
  }

  fun equippedVehicleOf(profile: PlayerProfile): VehicleSpec =
    VehicleCatalog.byId(profile.equippedVehicle)

  fun equippedWeaponOf(profile: PlayerProfile): WeaponSpec =
    WeaponCatalog.byId(profile.equippedWeapon)

  private companion object {
    const val TAG = "CareerVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
