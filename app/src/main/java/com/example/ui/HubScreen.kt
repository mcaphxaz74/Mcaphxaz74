package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOut
import com.example.data.LevelRun
import com.example.data.PlayerProfile
import com.example.data.VehicleCatalog
import com.example.data.VehicleSpec
import com.example.data.WeaponCatalog
import com.example.data.WeaponSpec
import com.example.game.WorldTheme
import com.example.ui.theme.AsphaltCard
import com.example.ui.theme.AsphaltDark
import com.example.ui.theme.AsphaltSurface
import com.example.ui.theme.CoinGold
import com.example.ui.theme.MarioRed
import com.example.ui.theme.PipeGreen
import com.example.ui.theme.ViceCyan

@Composable
fun HubScreen(
  viewModel: CareerViewModel,
  onLaunchWorld: (WorldTheme, VehicleSpec, WeaponSpec) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val runsState by viewModel.runsState.collectAsStateWithLifecycle()
  val actionError by viewModel.actionError.collectAsStateWithLifecycle()

  var selectedTab by remember { mutableIntStateOf(0) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(actionError) {
    if (actionError != null) {
      snackbarHostState.showSnackbar(actionError ?: "")
      viewModel.clearActionError()
    }
  }

  Scaffold(
    containerColor = AsphaltDark,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(AsphaltSurface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AsphaltCard)
                .border(2.dp, CoinGold, RoundedCornerShape(12.dp)),
          ) {
            Image(
              painter = painterResource(R.drawable.sprite_player_hero),
              contentDescription = stringResource(R.string.player_avatar_desc),
              contentScale = ContentScale.Crop,
              alignment = Alignment.TopCenter,
              modifier = Modifier.fillMaxSize(),
            )
          }
          Column {
            Text(
              text = stringResource(R.string.title_shroom_auto),
              style = MaterialTheme.typography.headlineMedium,
              color = CoinGold,
            )
            val profileName = (profileState as? UiState.Success)?.data?.displayName ?: "Driver"
            Text(
              text = "DRIVER: ${profileName.uppercase()}",
              style = MaterialTheme.typography.labelSmall,
              color = ViceCyan,
            )
          }
        }
        IconButton(
          onClick = { signOut(credentialManager, scope) },
          modifier = Modifier.size(48.dp).testTag("sign_out_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = stringResource(R.string.sign_out),
            tint = Color.White,
          )
        }
      }
    },
    bottomBar = {
      NavigationBar(
        containerColor = AsphaltSurface,
        modifier = Modifier.navigationBarsPadding(),
      ) {
        NavigationBarItem(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
          label = { Text(stringResource(R.string.tab_worlds)) },
          modifier = Modifier.testTag("tab_worlds"),
        )
        NavigationBarItem(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          icon = { Icon(Icons.Default.Build, contentDescription = null) },
          label = { Text(stringResource(R.string.tab_garage)) },
          modifier = Modifier.testTag("tab_garage"),
        )
        NavigationBarItem(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
          label = { Text(stringResource(R.string.tab_runs)) },
          modifier = Modifier.testTag("tab_runs"),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      when (val state = profileState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CoinGold)
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(text = state.message, color = MaterialTheme.colorScheme.error)
          }
        }
        is UiState.Success -> {
          val profile = state.data
          Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
            CareerSummaryStrip(profile = profile)

            when (selectedTab) {
              0 ->
                WorldsTab(
                  profile = profile,
                  onPlayWorld = { world ->
                    onLaunchWorld(
                      world,
                      viewModel.equippedVehicleOf(profile),
                      viewModel.equippedWeaponOf(profile),
                    )
                  },
                )
              1 ->
                GarageTab(
                  profile = profile,
                  onSelectVehicle = { viewModel.selectOrBuyVehicle(profile, it) },
                  onSelectWeapon = { viewModel.selectOrBuyWeapon(profile, it) },
                )
              else -> RunsHistoryTab(runsState = runsState)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CareerSummaryStrip(profile: PlayerProfile) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(AsphaltCard)
        .border(1.5.dp, CoinGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = CoinGold)
      Text(
        text = "${profile.totalCoins} COINS",
        style = MaterialTheme.typography.labelLarge,
        color = CoinGold,
      )
    }
    Text(
      text = "BEST ${profile.highScore}",
      style = MaterialTheme.typography.labelLarge,
      color = Color.White,
    )
    Text(
      text = VehicleCatalog.byId(profile.equippedVehicle).displayName,
      style = MaterialTheme.typography.labelSmall,
      color = ViceCyan,
    )
  }
}

@Composable
private fun WorldsTab(
  profile: PlayerProfile,
  onPlayWorld: (WorldTheme) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, CoinGold, RoundedCornerShape(16.dp))
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_banner),
          contentDescription = stringResource(R.string.hero_banner_desc),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(listOf(Color.Transparent, AsphaltDark.copy(alpha = 0.88f)))
              )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
          Text(
            text = stringResource(R.string.hub_select_stage),
            style = MaterialTheme.typography.labelLarge,
            color = CoinGold,
          )
          Text(
            text =
              "${VehicleCatalog.byId(profile.equippedVehicle).displayName} • ${WeaponCatalog.byId(profile.equippedWeapon).displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
          )
        }
      }
    }

    items(WorldTheme.entries, key = { it.worldId }) { world ->
      val unlocked = world.worldId <= profile.highestWorldUnlocked.toInt()
      Card(
        colors =
          CardDefaults.cardColors(
            containerColor = if (unlocked) AsphaltSurface else AsphaltCard.copy(alpha = 0.6f)
          ),
        modifier =
          Modifier.fillMaxWidth()
            .border(
              2.dp,
              if (unlocked) PipeGreen else Color.Gray.copy(alpha = 0.35f),
              RoundedCornerShape(16.dp),
            ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = world.title,
              style = MaterialTheme.typography.titleLarge,
              color = if (unlocked) CoinGold else Color.Gray,
            )
            Text(
              text =
                if (unlocked) world.subtitle
                else "Clear World 1-${world.worldId - 1} to unlock!",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Button(
            onClick = { onPlayWorld(world) },
            enabled = unlocked,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MarioRed,
                contentColor = Color.White,
              ),
            modifier = Modifier.height(48.dp).testTag("play_world_${world.worldId}"),
          ) {
            Icon(
              imageVector = if (unlocked) Icons.Default.PlayArrow else Icons.Default.Lock,
              contentDescription = null,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (unlocked) "DRIVE" else "LOCKED",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GarageTab(
  profile: PlayerProfile,
  onSelectVehicle: (VehicleSpec) -> Unit,
  onSelectWeapon: (WeaponSpec) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MarioRed)
        Text(
          text = stringResource(R.string.garage_vehicles_header),
          style = MaterialTheme.typography.labelLarge,
          color = Color.White,
        )
      }
    }
    items(VehicleCatalog.all, key = { it.id }) { vehicle ->
      val unlocked = profile.unlockedVehicles.contains(vehicle.id)
      val equipped = profile.equippedVehicle == vehicle.id
      LoadoutCard(
        title = vehicle.displayName,
        subtitle = vehicle.subtitle,
        stats = "SPD ${vehicle.maxSpeed.toInt()} • ARMOR ${vehicle.armorHp}",
        costCoins = vehicle.costCoins,
        unlocked = unlocked,
        equipped = equipped,
        onClick = { onSelectVehicle(vehicle) },
        testTag = "vehicle_card_${vehicle.id}",
      )
    }

    item {
      Spacer(modifier = Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = ViceCyan)
        Text(
          text = stringResource(R.string.garage_weapons_header),
          style = MaterialTheme.typography.labelLarge,
          color = Color.White,
        )
      }
    }
    items(WeaponCatalog.all, key = { it.id }) { weapon ->
      val unlocked = profile.unlockedWeapons.contains(weapon.id)
      val equipped = profile.equippedWeapon == weapon.id
      LoadoutCard(
        title = weapon.displayName,
        subtitle = weapon.subtitle,
        stats = "RANGE ${weapon.rangePx.toInt()}m • DMG ${weapon.damage}x${weapon.burstCount}",
        costCoins = weapon.costCoins,
        unlocked = unlocked,
        equipped = equipped,
        onClick = { onSelectWeapon(weapon) },
        testTag = "weapon_card_${weapon.id}",
      )
    }
  }
}

@Composable
private fun LoadoutCard(
  title: String,
  subtitle: String,
  stats: String,
  costCoins: Int,
  unlocked: Boolean,
  equipped: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
    modifier =
      Modifier.fillMaxWidth()
        .border(
          2.dp,
          if (equipped) CoinGold else Color.White.copy(alpha = 0.14f),
          RoundedCornerShape(14.dp),
        )
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stats, style = MaterialTheme.typography.labelSmall, color = ViceCyan)
      }
      Spacer(modifier = Modifier.width(10.dp))
      when {
        equipped -> {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CoinGold)
            Text("EQUIPPED", style = MaterialTheme.typography.labelSmall, color = CoinGold)
          }
        }
        unlocked -> {
          Text("EQUIP", style = MaterialTheme.typography.labelLarge, color = PipeGreen)
        }
        else -> {
          Text("UNLOCK $costCoins", style = MaterialTheme.typography.labelLarge, color = CoinGold)
        }
      }
    }
  }
}

@Composable
private fun RunsHistoryTab(runsState: UiState<List<LevelRun>>) {
  when (runsState) {
    is UiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CoinGold)
      }
    }
    is UiState.Error -> {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = runsState.message, color = MaterialTheme.colorScheme.error)
      }
    }
    is UiState.Success -> {
      val runs = runsState.data
      if (runs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
              imageVector = Icons.Default.EmojiEvents,
              contentDescription = null,
              tint = CoinGold,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = stringResource(R.string.runs_empty_title),
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
            )
            Text(
              text = stringResource(R.string.runs_empty_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(runs, key = { it.id }) { run ->
            Card(
              colors = CardDefaults.cardColors(containerColor = AsphaltSurface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = run.worldName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (run.cleared) PipeGreen else MarioRed,
                  )
                  Text(
                    text =
                      "${if (run.cleared) "CLEARED" else "BUSTED"} • ${run.enemiesDefeated} KO • ★${run.maxWantedStars}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "${run.score} PTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                  )
                  Text(
                    text = "+${run.coinsCollected} COINS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoinGold,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
