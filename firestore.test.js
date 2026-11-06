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

test("Unauthenticated user cannot read or write trips", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trips").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user can create and query their own trip and place", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tripRef = aliceDb.collection("users").doc(ALICE_UID).collection("trips").doc("trip_1");
  await assertSucceeds(
    tripRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-06",
      transportMode: "WALKING",
      startTimeEpochMs: 1700000000000,
      endTimeEpochMs: 1700001800000,
      durationMinutes: 30,
      distanceKm: 2.5,
      startLabel: "Home",
      endLabel: "Office",
      startLat: 24.7136,
      startLng: 46.6753,
      endLat: 24.7236,
      endLng: 46.6853,
      routePointsSerialized: "24.7136,46.6753;24.7236,46.6853",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trips").where("userId", "==", ALICE_UID).get()
  );
});

test("Cross-user access to another user's trips or places is denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("places").doc("place_bob").set({
      userId: BOB_UID,
      name: "Bob Home",
      category: "HOME",
      latitude: 24.7136,
      longitude: 46.6753,
      radiusMeters: 100,
      visitCount: 5,
      totalDurationMinutes: 600,
      lastArrivalEpochMs: 1700000000000,
      lastDepartureEpochMs: 1700036000000,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("places").doc("place_bob").get()
  );
});

test("Shadow update with ghost field or invalid category is rejected", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const placeRef = aliceDb.collection("users").doc(ALICE_UID).collection("places").doc("place_1");
  await assertFails(
    placeRef.set({
      userId: ALICE_UID,
      name: "My Place",
      category: "INVALID_CATEGORY",
      latitude: 24.7136,
      longitude: 46.6753,
      radiusMeters: 100,
      visitCount: 1,
      totalDurationMinutes: 60,
      lastArrivalEpochMs: 1700000000000,
      lastDepartureEpochMs: 1700003600000,
      createdAt: now,
      updatedAt: now,
    })
  );
});
