const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_123";
const BOB_UID = "bob_shop_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
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

test("Unauthenticated user cannot read or write any shop data", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).collection("bills").get()
  );
  await assertFails(
    unauth
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        userId: ALICE_UID,
        name: "Walk-in",
        phone: "",
        email: "",
        address: "",
        notes: "",
        totalSpent: 0,
        totalDue: 0,
        billCount: 0,
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
});

test("ShopProfile: owner can create and update settings or bill counter; rejects ghost fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      shopName: "Emerald Mart",
      shopPhone: "+1 555-0199",
      shopAddress: "42 Market St",
      currencySymbol: "$",
      taxPercent: 8.5,
      receiptFooter: "Thank you for visiting!",
      nextBillNumber: 1001,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Update settings
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      shopName: "Emerald Goods",
      taxPercent: 10.0,
      updatedAt: pastDate(1000),
    })
  );

  // Advance bill sequence
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      nextBillNumber: 1002,
      updatedAt: pastDate(500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(200),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      shopName: "Time Traveler",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user read/write blocked
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      shopName: "Hijacked",
      updatedAt: pastDate(200),
    })
  );
});

test("Customers & Products: owner CRUD and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        userId: ALICE_UID,
        name: "Maya Lin",
        phone: "555-1234",
        email: "maya@example.com",
        address: "10 Oak Ave",
        notes: "Regular customer",
        totalSpent: 0.0,
        totalDue: 0.0,
        billCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // List empty or populated subcollection succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );

  // Update customer details
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({
        phone: "555-9999",
        updatedAt: pastDate(1000),
      })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({
        createdAt: pastDate(500),
        updatedAt: pastDate(500),
      })
  );

  // Reject `@DocumentId` id field inside document payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        id: "prod_1",
        userId: ALICE_UID,
        name: "Espresso Beans",
        sku: "ESP-01",
        category: "Coffee",
        unitPrice: 18.5,
        unitLabel: "bag",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Valid product create
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: ALICE_UID,
        name: "Espresso Beans",
        sku: "ESP-01",
        category: "Coffee",
        unitPrice: 18.5,
        unitLabel: "bag",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .get()
  );
});

test("Bills & LineItems: atomic batch create, payment update, void lock, and orphan item rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // Create customer first
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        userId: ALICE_UID,
        name: "Liam Patel",
        phone: "555-3030",
        email: "",
        address: "",
        notes: "",
        totalSpent: 0.0,
        totalDue: 0.0,
        billCount: 0,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Atomic batch: bill + line item + customer ledger update
  const batch = aliceDb.batch();
  const billRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("bills")
    .doc("bill_1001");
  const itemRef = billRef.collection("items").doc("item_1");
  const custRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("customers")
    .doc("cust_1");

  batch.set(billRef, {
    userId: ALICE_UID,
    billNumber: "INV-1001",
    customerId: "cust_1",
    customerName: "Liam Patel",
    customerPhone: "555-3030",
    subtotal: 50.0,
    discountAmount: 0.0,
    taxPercent: 10.0,
    taxAmount: 5.0,
    totalAmount: 55.0,
    paidAmount: 20.0,
    dueAmount: 35.0,
    paymentMethod: "CASH",
    status: "PARTIAL",
    itemCount: 1,
    itemsSummary: "2x Artisan Roast",
    notes: "Balance next week",
    createdAt: created,
    updatedAt: created,
  });

  batch.set(itemRef, {
    userId: ALICE_UID,
    billId: "bill_1001",
    productId: "",
    name: "Artisan Roast",
    quantity: 2,
    unitLabel: "bag",
    unitPrice: 25.0,
    lineTotal: 50.0,
    createdAt: created,
    updatedAt: created,
  });

  batch.update(custRef, {
    totalSpent: 55.0,
    totalDue: 35.0,
    billCount: 1,
    updatedAt: created,
  });

  await assertSucceeds(batch.commit());

  // List line items succeeds for owner
  await assertSucceeds(billRef.collection("items").get());

  // Record payment
  await assertSucceeds(
    billRef.update({
      paidAmount: 55.0,
      dueAmount: 0.0,
      status: "PAID",
      paymentMethod: "UPI",
      updatedAt: pastDate(1000),
    })
  );

  // Void bill
  await assertSucceeds(
    billRef.update({
      status: "VOID",
      dueAmount: 0.0,
      updatedAt: pastDate(500),
    })
  );

  // Terminal state lock: subsequent updates to VOID bill must fail
  await assertFails(
    billRef.update({
      status: "PAID",
      paidAmount: 55.0,
      dueAmount: 0.0,
      paymentMethod: "CASH",
      updatedAt: pastDate(100),
    })
  );

  // Orphan line item without existing parent bill must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bills")
      .doc("nonexistent_bill")
      .collection("items")
      .doc("item_x")
      .set({
        userId: ALICE_UID,
        billId: "nonexistent_bill",
        productId: "",
        name: "Orphan Item",
        quantity: 1,
        unitLabel: "pcs",
        unitPrice: 10.0,
        lineTotal: 10.0,
        createdAt: created,
        updatedAt: created,
      })
  );
});
