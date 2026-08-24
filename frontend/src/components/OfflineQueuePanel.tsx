import { useAppDispatch, useAppSelector } from '../store/hooks'
import { closeOverlay } from '../store/uiSlice'
import { checkConnectivity } from '../store/connectivitySlice'
import { deleteQueuedMessage } from '../store/chatSlice'
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
      {queue.length === 0 ? (
        <p className="queue-empty">Nothing queued.</p>
      ) : (
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
    </Modal>
  )
}
