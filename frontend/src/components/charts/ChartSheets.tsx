import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { closeOverlay } from '../../store/uiSlice'
import Modal from '../modal/Modal'
import MacroBarChart from './MacroBarChart'
import WeightTrendChart from './WeightTrendChart'

// Phone/tablet only (desktop renders both charts permanently in the Sidebar instead): two
// separate single-chart bottom sheets, opened independently from the header menu - not one
// combined sheet, per the design handoff.
export default function ChartSheets() {
  const dispatch = useAppDispatch()
  const overlay = useAppSelector((state) => state.ui.overlay)

  return (
    <>
      <Modal
        open={overlay === 'chartsMacros'}
        onClose={() => dispatch(closeOverlay())}
        variant="sheet"
        title="Calories & macros"
      >
        <MacroBarChart />
      </Modal>
      <Modal
        open={overlay === 'chartsWeight'}
        onClose={() => dispatch(closeOverlay())}
        variant="sheet"
        title="Weight trend"
      >
        <WeightTrendChart />
      </Modal>
    </>
  )
}
