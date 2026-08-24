import { useAppDispatch, useAppSelector } from '../store/hooks'
import { checkConnectivity } from '../store/connectivitySlice'
import { openOverlay } from '../store/uiSlice'

const LABELS = {
  online: 'Online',
  retrying: 'Reconnecting…',
  offline: 'Offline',
} as const

export default function StatusLight() {
  const dispatch = useAppDispatch()
  const status = useAppSelector((state) => state.connectivity.status)

  const handleClick = () => {
    if (status === 'offline') {
      // Offline is the one state that opens the queue panel on tap (per handoff); a manual
      // retry also happens from there via "try to reconnect now".
      dispatch(openOverlay('queue'))
      return
    }
    dispatch(checkConnectivity())
  }

  return (
    <button
      type="button"
      className="status-light"
      onClick={handleClick}
      aria-label={`Connection status: ${LABELS[status]}`}
      title={LABELS[status]}
    >
      <span className={`status-dot status-dot--${status}`} />
      <span className="status-label">{LABELS[status]}</span>
    </button>
  )
}
