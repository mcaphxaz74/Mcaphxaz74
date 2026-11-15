const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    displayName: "MarioDriver",
    totalCoins: 150,
    highScore: 12000,
    highestWorldUnlocked: 2,
    equippedVehicle: "kart_red",
    equippedWeapon: "fire_blaster",
    unlockedVehicles: ["kart_red"],
    unlockedWeapons: ["fire_blaster"],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validRun(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    worldId: 1,
    worldName: "1-1 Toadstool Strip",
    score: 8500,
    coinsCollected: 42,
    enemiesDefeated: 18,
    maxWantedStars: 3,
    cleared: true,
    vehicleUsed: "kart_red",
    weaponUsed: "fire_blaster",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user profiles or runs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("runs").get());
});

test("Authenticated owner can create, read, and update their PlayerProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      totalCoins: 250,
      highScore: 18500,
      equippedVehicle: "muscle_gold",
      unlockedVehicles: ["kart_red", "muscle_gold"],
      updatedAt: pastTimestamp(),
    })
  );
});

test("Cross-user access is blocked on profiles and runs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_1").set(validRun(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set(validProfile(BOB_UID)));
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ totalCoins: 99999, updatedAt: pastTimestamp() })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("runs").get());
});

test("Shadow update with ghost field and invalid vehicle enum are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      equippedVehicle: "rocket_ufo",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    profileRef.update({
      totalCoins: -10,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Owner can create and list immutable LevelRun records, but cannot update them", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const runRef = aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_abc_1");

  await assertSucceeds(runRef.set(validRun(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("runs").get());
  await assertFails(
    runRef.update({
      score: 999999,
      updatedAt: pastTimestamp(),
    })
  );
});
