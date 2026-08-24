import MacroBarChart from '../charts/MacroBarChart'
import WeightTrendChart from '../charts/WeightTrendChart'

// Desktop-only (>=1080px, enforced by CSS grid placement in AppShell), permanently visible, both
// charts stacked - the same chart components the phone/tablet bottom sheets render, fed by the
// same dashboardSlice state so nothing re-fetches on a breakpoint crossing.
export default function Sidebar() {
  return (
    <aside className="sidebar">
      <MacroBarChart />
      <WeightTrendChart />
    </aside>
  )
}
