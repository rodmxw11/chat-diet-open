import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadScanQueue } from '../../store/barcodeQueueSlice'
import { loadEntryQueue, loadHistory, loadQueue } from '../../store/chatSlice'
import { loadNotes } from '../../store/notesSlice'
import { loadMacros, loadTdeeEstimate, loadWeightTrend } from '../../store/dashboardSlice'
import Header from '../Header'
import ChatWindow from '../ChatWindow'
import MessageInput from '../MessageInput'
import QuantityPromptBar from '../QuantityPromptBar'
import NotesView from '../notes/NotesView'
import DailyFoodsView from '../foods/DailyFoodsView'
import ChatHistoryView from '../chatHistory/ChatHistoryView'
import MicronutrientsView from '../micronutrients/MicronutrientsView'
import FoodItemsView from '../foodItems/FoodItemsView'
import SchemaView from '../schema/SchemaView'
import BloodPressureView from '../vitals/BloodPressureView'
import AboutView from '../about/AboutView'
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
  const theme = useAppSelector((state) => state.ui.theme)

  // The CSS reads this attribute to pick which token set applies - see the toggleTheme reducer for
  // why it stops tracking the OS preference once the user has chosen explicitly.
  useEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  // Fetched once here (not inside Sidebar/ChartSheets) so the same dashboard state feeds both the
  // desktop sidebar and the mobile chart sheets without re-fetching when the sheet opens or the
  // viewport crosses a breakpoint.
  useEffect(() => {
    dispatch(loadHistory())
    dispatch(loadQueue())
    dispatch(loadEntryQueue())
    dispatch(loadScanQueue())
    dispatch(loadWeightTrend())
    dispatch(loadTdeeEstimate())
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
            <QuantityPromptBar />
            <MessageInput />
          </>
        )}
        {screen === 'notes' && <NotesView />}
        {screen === 'foods' && <DailyFoodsView />}
        {screen === 'chatHistory' && <ChatHistoryView />}
        {screen === 'micronutrients' && <MicronutrientsView />}
        {screen === 'foodItems' && <FoodItemsView />}
        {screen === 'schema' && <SchemaView />}
        {screen === 'bloodPressure' && <BloodPressureView />}
        {screen === 'about' && <AboutView />}
      </div>
      <Sidebar />
      <ChartSheets />
      <OfflineQueuePanel />
    </div>
  )
}
