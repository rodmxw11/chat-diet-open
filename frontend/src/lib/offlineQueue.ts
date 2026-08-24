// App-level offline write queue for chat messages, backed by IndexedDB. Replaces the old Workbox
// backgroundSync queue, which was opaque (couldn't be listed or deleted from) - the offline queue
// panel needs to show and remove individual queued items, which Workbox's queue can't do.

const DB_NAME = 'chat-diet-queue'
const DB_VERSION = 1
const STORE_NAME = 'queued-requests'

export interface QueuedRequest {
  id: string
  text: string
  clientSentAt: string
  createdAt: string
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function withStore<T>(mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb()
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, mode)
      const store = tx.objectStore(STORE_NAME)
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
  await withStore('readwrite', (store) => store.add(record))
  return record
}

export async function listQueued(): Promise<QueuedRequest[]> {
  const items = await withStore<QueuedRequest[]>('readonly', (store) => store.getAll())
  return items.sort((a, b) => a.createdAt.localeCompare(b.createdAt))
}

export async function removeQueued(id: string): Promise<void> {
  await withStore('readwrite', (store) => store.delete(id))
}

export async function clearQueued(): Promise<void> {
  await withStore('readwrite', (store) => store.clear())
}
