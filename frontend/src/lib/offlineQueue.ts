// App-level offline write queue for chat messages and barcode scans, backed by IndexedDB.
// Replaces the old Workbox backgroundSync queue, which was opaque (couldn't be listed or deleted
// from) - the offline queue panel needs to show and remove individual queued items, which
// Workbox's queue can't do.

const DB_NAME = 'chat-diet-queue'
const DB_VERSION = 3
const MESSAGE_STORE = 'queued-requests'
const SCAN_STORE = 'queued-scans'
const ENTRY_STORE = 'queued-entries'

export interface QueuedRequest {
  id: string
  text: string
  clientSentAt: string
  createdAt: string
}

export interface QueuedScan {
  id: string
  upc: string
  createdAt: string
}

/** A quantity-prompt submit made offline - logs by food_item id once the connection returns. */
export interface QueuedEntry {
  id: string
  foodItemId: number
  name: string
  amount: number
  unit: 'g' | 'cal' | 'servings'
  clientSentAt: string
  createdAt: string
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(MESSAGE_STORE)) {
        db.createObjectStore(MESSAGE_STORE, { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains(SCAN_STORE)) {
        db.createObjectStore(SCAN_STORE, { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains(ENTRY_STORE)) {
        db.createObjectStore(ENTRY_STORE, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function withStore<T>(
  storeName: string,
  mode: IDBTransactionMode,
  fn: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const db = await openDb()
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(storeName, mode)
      const store = tx.objectStore(storeName)
      const request = fn(store)
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  } finally {
    db.close()
  }
}

export async function enqueue(text: string, clientSentAt: string): Promise<QueuedRequest> {
  const record: QueuedRequest = {
    id: crypto.randomUUID(),
    text,
    clientSentAt,
    createdAt: new Date().toISOString(),
  }
  await withStore(MESSAGE_STORE, 'readwrite', (store) => store.add(record))
  return record
}

export async function listQueued(): Promise<QueuedRequest[]> {
  const items = await withStore<QueuedRequest[]>(MESSAGE_STORE, 'readonly', (store) => store.getAll())
  return items.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

export async function removeQueued(id: string): Promise<void> {
  await withStore(MESSAGE_STORE, 'readwrite', (store) => store.delete(id))
}

export async function clearQueued(): Promise<void> {
  await withStore(MESSAGE_STORE, 'readwrite', (store) => store.clear())
}

export async function enqueueScan(upc: string): Promise<QueuedScan> {
  const record: QueuedScan = {
    id: crypto.randomUUID(),
    upc,
    createdAt: new Date().toISOString(),
  }
  await withStore(SCAN_STORE, 'readwrite', (store) => store.add(record))
  return record
}

export async function listQueuedScans(): Promise<QueuedScan[]> {
  const items = await withStore<QueuedScan[]>(SCAN_STORE, 'readonly', (store) => store.getAll())
  return items.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

export async function removeQueuedScan(id: string): Promise<void> {
  await withStore(SCAN_STORE, 'readwrite', (store) => store.delete(id))
}

export async function enqueueEntry(entry: Omit<QueuedEntry, 'id' | 'createdAt'>): Promise<QueuedEntry> {
  const record: QueuedEntry = {
    ...entry,
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString(),
  }
  await withStore(ENTRY_STORE, 'readwrite', (store) => store.add(record))
  return record
}

export async function listQueuedEntries(): Promise<QueuedEntry[]> {
  const items = await withStore<QueuedEntry[]>(ENTRY_STORE, 'readonly', (store) => store.getAll())
  return items.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

export async function removeQueuedEntry(id: string): Promise<void> {
  await withStore(ENTRY_STORE, 'readwrite', (store) => store.delete(id))
}
