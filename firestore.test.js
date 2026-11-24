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

test("Unauthenticated user: cannot read or write conversations, messages, or memories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Hola",
      lastMessagePreview: "Hola",
      lastEmotion: "ALEGRIA",
      messageCount: 1,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid conversation, message, and memory create & update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 10000);
  const now = new Date(Date.now() - 1000);

  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Conversación inicial",
      lastMessagePreview: "¡Holaaa! ¿Qué vamos a hacer hoy?",
      lastEmotion: "ALEGRIA",
      messageCount: 1,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    convRef.update({
      lastMessagePreview: "Todo genial",
      lastEmotion: "ENTUSIASMO",
      messageCount: 2,
      updatedAt: now,
    })
  );

  // Message without optional actionTaken
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      sender: "USER",
      text: "Hola DeiAI",
      emotion: "ALEGRIA",
      createdAt: past,
    })
  );

  // Message with optional actionTaken
  await assertSucceeds(
    convRef.collection("messages").doc("msg_2").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      sender: "DEIAI",
      text: "¡Holaaa! ¿Qué vamos a hacer hoy?",
      emotion: "ENTUSIASMO",
      actionTaken: "Consultar hora",
      createdAt: now,
    })
  );

  // List queries succeed on user subcollections even when empty or populated
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").orderBy("updatedAt", "desc").get()
  );
  await assertSucceeds(
    convRef.collection("messages").orderBy("createdAt", "asc").get()
  );

  // Memory create and partial update
  const memRef = aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");
  await assertSucceeds(
    memRef.set({
      userId: ALICE_UID,
      category: "NOMBRE",
      key: "Nombre del usuario",
      value: "Carlos",
      authorized: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    memRef.update({
      value: "Carlos Mendoza",
      updatedAt: now,
    })
  );
});

test("Security bounds: reject cross-user access, extra @DocumentId field, future timestamps, and invalid emotions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_alice").set({
      userId: ALICE_UID,
      category: "PREFERENCIA",
      key: "Bebida",
      value: "Café negro",
      authorized: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_alice").update({
      value: "Hackeado",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject extra @DocumentId 'id' field in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_bad_id").set({
      id: "mem_bad_id",
      userId: ALICE_UID,
      category: "DATO",
      key: "Ciudad",
      value: "Madrid",
      authorized: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_future").set({
      userId: ALICE_UID,
      category: "DATO",
      key: "Ciudad",
      value: "Madrid",
      authorized: true,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: past,
    })
  );

  // Reject invalid emotion enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_bad_emotion").set({
      userId: ALICE_UID,
      title: "Test",
      lastMessagePreview: "Test",
      lastEmotion: "INVALIDA",
      messageCount: 1,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject createdAt mutation on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_alice").update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
