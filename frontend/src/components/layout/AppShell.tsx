import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadScanQueue } from '../../store/barcodeQueueSlice'
import { loadEntryQueue, loadHistory, loadQueue } from '../../store/chatSlice'
import { loadNotes } from '../../store/notesSlice'
import {
  useGetMacrosQuery,
  useGetSummaryQuery,
  useGetTdeeQuery,
  useGetWeightTrendQuery,
} from '../../store/dashboardApi'
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
import MeasurementsView from '../measurements/MeasurementsView'
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

  // Subscribed here, where it's always mounted: the summary is polled so the header stays current
  // even after a tool-driven change made elsewhere (e.g. a chat turn on another device), and the
  // chart data is prefetched so the mobile chart sheets open already filled in. The charts read the
  // same cache through their own hooks, so this costs no extra requests.
  useGetSummaryQuery(undefined, { pollingInterval: 60_000 })
  useGetMacrosQuery(range)
  useGetWeightTrendQuery()
  useGetTdeeQuery()

  useEffect(() => {
    dispatch(loadHistory())
    dispatch(loadQueue())
    dispatch(loadEntryQueue())
    dispatch(loadScanQueue())
    dispatch(loadNotes())
  }, [dispatch])

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
        {screen === 'measurements' && <MeasurementsView />}
        {screen === 'about' && <AboutView />}
      </div>
      <Sidebar />
      <ChartSheets />
      <OfflineQueuePanel />
    </div>
  )
}
