const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vids_123";
const BOB_UID = "bob_vids_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(ownerId, overrides = {}) {
  const now = new Date(Date.now() - 10000);
  return {
    ownerId,
    ownerName: "Alice Creator",
    title: "Q4 Product Launch Reel",
    description: "Timeline cut with intro, demo, and outro.",
    aspectRatio: "16:9",
    templateKey: "launch",
    shareVisibility: "private",
    shareCode: "",
    totalDurationMs: 12000,
    clips: [
      {
        clipId: "clip_1",
        type: "SCENE_CARD",
        title: "Opening Title",
        subtitle: "Introducing Vids Studio",
        mediaUri: "",
        durationMs: 4000,
        trimStartMs: 0,
        trimEndMs: 4000,
        speed: 1.0,
        transition: "Fade",
        filter: "Vivid",
        accentColorHex: 0xFF7C4DFF,
      },
    ],
    overlays: [
      {
        overlayId: "ov_1",
        text: "Coming Soon",
        startMs: 500,
        durationMs: 2500,
        position: "BOTTOM",
        styleKey: "BOLD_PILL",
        colorHex: 0xFFFFFFFF,
      },
    ],
    audioTracks: [
      {
        trackId: "aud_1",
        title: "Upbeat Synth Pulse",
        sourceType: "STUDIO_BED",
        audioUri: "",
        startMs: 0,
        durationMs: 12000,
        volume: 0.75,
        muted: false,
      },
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

test("Unauthenticated user: denied all reads and writes on projects and saved_shares", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("projects").get());
  await assertFails(unauthDb.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("saved_shares").get());
});

test("Owner: can create, read, list, update timeline, update share settings, and delete own project", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("projects").doc("proj_alice_1");

  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(aliceDb.collection("projects").where("ownerId", "==", ALICE_UID).get());

  // Timeline edit partial update
  await assertSucceeds(
    docRef.update({
      title: "Q4 Launch Final Cut",
      totalDurationMs: 15000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Sharing settings partial update
  await assertSucceeds(
    docRef.update({
      shareVisibility: "link_anyone",
      shareCode: "VIDS8492",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Security: rejects ghost fields, @DocumentId 'id' field, immutable ownerId/createdAt changes, and invalid shareCode", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Reject extra 'id' field
  await assertFails(
    aliceDb.collection("projects").doc("proj_bad_id").set(validProjectPayload(ALICE_UID, { id: "proj_bad_id" }))
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("projects").doc("proj_ghost").set(validProjectPayload(ALICE_UID, { isFeatured: true }))
  );

  // Reject link_anyone without valid >=4 char shareCode
  await assertFails(
    aliceDb
      .collection("projects")
      .doc("proj_bad_share")
      .set(validProjectPayload(ALICE_UID, { shareVisibility: "link_anyone", shareCode: "12" }))
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb
      .collection("projects")
      .doc("proj_future")
      .set(validProjectPayload(ALICE_UID, { updatedAt: new Date(Date.now() + 60000) }))
  );

  // Create valid doc, then test immutable updates
  const docRef = aliceDb.collection("projects").doc("proj_immut");
  await assertSucceeds(docRef.set(validProjectPayload(ALICE_UID)));

  await assertFails(
    docRef.update({
      ownerId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    docRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: private projects hidden, link_anyone projects readable but never editable by non-owner", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("projects").doc("alice_priv").set(validProjectPayload(ALICE_UID));
    await db
      .collection("projects")
      .doc("alice_shared")
      .set(validProjectPayload(ALICE_UID, { shareVisibility: "link_anyone", shareCode: "TEAM9001" }));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob cannot get or list Alice's private project
  await assertFails(bobDb.collection("projects").doc("alice_priv").get());
  await assertFails(bobDb.collection("projects").where("ownerId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("projects").get());

  // Bob CAN read/query Alice's link_anyone project by shareCode
  await assertSucceeds(bobDb.collection("projects").doc("alice_shared").get());
  await assertSucceeds(
    bobDb
      .collection("projects")
      .where("shareCode", "==", "TEAM9001")
      .where("shareVisibility", "==", "link_anyone")
      .get()
  );

  // Bob CANNOT edit or delete Alice's shared project
  await assertFails(
    bobDb.collection("projects").doc("alice_shared").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("projects").doc("alice_shared").delete());
});

test("User saved_shares subcollection: isolated per user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("saved_shares").doc("alice_shared").set({
      projectId: "alice_shared",
      shareCode: "TEAM9001",
      title: "Q4 Product Launch Reel",
      ownerName: "Alice Creator",
      aspectRatio: "16:9",
      totalDurationMs: 12000,
      savedAt: now,
    })
  );

  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("saved_shares").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("saved_shares").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("saved_shares").doc("alice_shared").get());
});
