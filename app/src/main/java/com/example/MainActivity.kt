package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.data.PlayerRepository
import com.example.data.VehicleSpec
import com.example.data.WeaponSpec
import com.example.game.WorldTheme
import com.example.ui.AuthScreen
import com.example.ui.CareerViewModel
import com.example.ui.GameScreen
import com.example.ui.HubScreen
import com.example.ui.theme.ShroomAutoTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ShroomAutoTheme {
        AppNavigation()
      }
    }
  }
}

private data class ActiveGameSession(
  val worldTheme: WorldTheme,
  val vehicle: VehicleSpec,
  val weapon: WeaponSpec,
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    val uid = user.uid
    val displayName = user.displayName ?: "Plumber"
    key(uid) {
      AuthenticatedShell(currentUserId = uid, displayName = displayName)
    }
  }
}

@Composable
private fun AuthenticatedShell(
  currentUserId: String,
  displayName: String,
) {
  val careerViewModel: CareerViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            CareerViewModel(
              repository = PlayerRepository(db = db, auth = Firebase.auth),
              currentUserId = currentUserId,
              initialDisplayName = displayName,
            )
          }
        },
    )

  var activeGame by remember { mutableStateOf<ActiveGameSession?>(null) }

  val session = activeGame
  if (session == null) {
    HubScreen(
      viewModel = careerViewModel,
      onLaunchWorld = { world, vehicle, weapon ->
        activeGame = ActiveGameSession(worldTheme = world, vehicle = vehicle, weapon = weapon)
      },
    )
  } else {
    GameScreen(
      worldTheme = session.worldTheme,
      equippedVehicle = session.vehicle,
      equippedWeapon = session.weapon,
      onRunFinished = { score, coins, enemies, maxWanted, cleared ->
        careerViewModel.recordRun(
          worldId = session.worldTheme.worldId,
          worldName = session.worldTheme.title,
          score = score,
          coinsCollected = coins,
          enemiesDefeated = enemies,
          maxWantedStars = maxWanted,
          cleared = cleared,
          vehicleUsed = session.vehicle,
          weaponUsed = session.weapon,
        )
      },
      onExitToHub = { activeGame = null },
    )
  }
}
