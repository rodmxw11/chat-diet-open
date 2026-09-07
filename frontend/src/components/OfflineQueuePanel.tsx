import { useAppDispatch, useAppSelector } from '../store/hooks'
import { closeOverlay } from '../store/uiSlice'
import { checkConnectivity } from '../store/connectivitySlice'
import { deleteQueuedEntry, deleteQueuedMessage, scannedEntryText } from '../store/chatSlice'
import { deleteQueuedScan } from '../store/barcodeQueueSlice'
import Modal from './modal/Modal'

function relativeMinutesAgo(iso: string): string {
  const minutes = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60_000))
  if (minutes < 1) return 'queued just now'
  if (minutes === 1) return 'queued 1 min ago'
  return `queued ${minutes} min ago`
}

export default function OfflineQueuePanel() {
  const dispatch = useAppDispatch()
  const open = useAppSelector((state) => state.ui.overlay === 'queue')
  const queue = useAppSelector((state) => state.chat.queue)
  const entryQueue = useAppSelector((state) => state.chat.entryQueue)
  const scanQueue = useAppSelector((state) => state.barcodeQueue.queue)
  const status = useAppSelector((state) => state.connectivity.status)

  return (
    <Modal
      open={open}
      onClose={() => dispatch(closeOverlay())}
      variant="modal"
      title="Waiting to send"
      footer={
        <button
          type="button"
          className={`queue-retry-button ${status === 'offline' ? 'queue-retry-button--prominent' : ''}`}
          onClick={() => dispatch(checkConnectivity())}
        >
          Try to reconnect now
        </button>
      }
    >
      {queue.length === 0 && entryQueue.length === 0 && scanQueue.length === 0 ? (
        <p className="queue-empty">Nothing queued.</p>
      ) : (
        <>
          {queue.length > 0 && (
            <ul className="queue-list">
              {queue.map((item) => (
                <li key={item.id} className="queue-row">
                  <div className="queue-row-text">
                    <p>{item.text}</p>
                    <span className="queue-row-caption">{relativeMinutesAgo(item.createdAt)}</span>
                  </div>
                  <button
                    type="button"
                    className="queue-delete-button"
                    onClick={() => dispatch(deleteQueuedMessage(item.id))}
                  >
                    Delete
                  </button>
                </li>
              ))}
            </ul>
          )}
          {entryQueue.length > 0 && (
            <>
              <h3 className="queue-section-title">Scanned foods</h3>
              <ul className="queue-list">
                {entryQueue.map((item) => (
                  <li key={item.id} className="queue-row">
                    <div className="queue-row-text">
                      <p>{scannedEntryText(item.name, item.amount, item.unit)}</p>
                      <span className="queue-row-caption">
                        {relativeMinutesAgo(item.createdAt)} · will log once you're back online
                      </span>
                    </div>
                    <button
                      type="button"
                      className="queue-delete-button"
                      onClick={() => dispatch(deleteQueuedEntry(item.id))}
                    >
                      Delete
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
          {scanQueue.length > 0 && (
            <>
              <h3 className="queue-section-title">Scanned barcodes</h3>
              <ul className="queue-list">
                {scanQueue.map((item) => (
                  <li key={item.id} className="queue-row">
                    <div className="queue-row-text">
                      <p>{item.upc}</p>
                      <span className="queue-row-caption">
                        {relativeMinutesAgo(item.createdAt)} · will look it up once you're back online
                      </span>
                    </div>
                    <button
                      type="button"
                      className="queue-delete-button"
                      onClick={() => dispatch(deleteQueuedScan(item.id))}
                    >
                      Delete
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
    </Modal>
  )
}
