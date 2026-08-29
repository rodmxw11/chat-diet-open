import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadHistory, loadQueue } from '../../store/chatSlice'
import { loadShoppingItems } from '../../store/shoppingSlice'
import { loadNotes } from '../../store/notesSlice'
import { loadMacros, loadWeightTrend } from '../../store/dashboardSlice'
import Header from '../Header'
import ChatWindow from '../ChatWindow'
import MessageInput from '../MessageInput'
import ShoppingModeView from '../shopping/ShoppingModeView'
import NotesView from '../notes/NotesView'
import DailyFoodsView from '../foods/DailyFoodsView'
import ChatHistoryView from '../chatHistory/ChatHistoryView'
import Sidebar from './Sidebar'
import ChartSheets from '../charts/ChartSheets'
import OfflineQueuePanel from '../OfflineQueuePanel'

// CSS Grid shell per the design handoff's breakpoints: phone <620px and tablet 620-1079px both
// render a single column (chat or shop fills the width); desktop >=1080px adds a fixed 420px
// sidebar with the two dashboard charts rendered permanently, capped at a 1440px centered shell.
export default function AppShell() {
  const dispatch = useAppDispatch()
  const screen = useAppSelector((state) => state.ui.screen)
  const range = useAppSelector((state) => state.dashboard.range)

  // Fetched once here (not inside Sidebar/ChartSheets) so the same dashboard state feeds both the
  // desktop sidebar and the mobile chart sheets without re-fetching when the sheet opens or the
  // viewport crosses a breakpoint.
  useEffect(() => {
    dispatch(loadHistory())
    dispatch(loadQueue())
    dispatch(loadShoppingItems())
    dispatch(loadWeightTrend())
    dispatch(loadNotes())
  }, [dispatch])

  useEffect(() => {
    dispatch(loadMacros(range))
  }, [dispatch, range])

  return (
    <div className="app-shell">
      <div className="main-column">
        {screen === 'chat' && (
          <>
            <Header />
            <ChatWindow />
            <MessageInput />
          </>
        )}
        {screen === 'shop' && <ShoppingModeView />}
        {screen === 'notes' && <NotesView />}
        {screen === 'foods' && <DailyFoodsView />}
        {screen === 'chatHistory' && <ChatHistoryView />}
      </div>
      <Sidebar />
      <ChartSheets />
      <OfflineQueuePanel />
    </div>
  )
}
