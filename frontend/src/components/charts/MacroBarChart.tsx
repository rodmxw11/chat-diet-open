import {
  Bar,
  BarChart,
  LabelList,
  ResponsiveContainer,
  usePlotArea,
  XAxis,
  YAxis,
  type MouseHandlerDataParam,
} from 'recharts'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setRange, type MacroRange } from '../../store/dashboardSlice'
import { setFoodEntriesDate } from '../../store/foodEntriesSlice'
import { closeOverlay, setScreen } from '../../store/uiSlice'

// Recharts' label-callback props type numeric fields loosely (string | number | boolean | ...),
// so this accepts `unknown` for each and normalizes to numbers internally rather than fighting
// the library's contravariant callback typing with a narrower interface.
interface RawLabelProps {
  x?: unknown
  y?: unknown
  width?: unknown
  height?: unknown
  value?: unknown
}

function num(value: unknown): number | undefined {
  if (typeof value === 'number') return value
  if (typeof value === 'string') return Number(value)
  return undefined
}

function formatDayLabel(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  return date.toLocaleDateString(undefined, { weekday: 'short' })
}

// Stacked bar chart per the design handoff - no tooltip, legend, or hover state, but clicking a
// bar opens that day on the Daily Foods page (see handleChartClick). The calorie badge (above the
// stack) and the in-bar gram+letter labels are
// both rendered as custom label content, attached via a LabelList with an explicit dataKey on
// each Bar rather than that Bar's own `label` prop. Two independent Recharts quirks make that
// necessary: (1) for a stacked series, the `value` a label callback receives is the cumulative
// stack height up to and including that segment, not the segment's own amount - so without this,
// "protein" would render as protein+fat+carbs, "fat" as protein+fat, and so on; (2) Recharts
// silently skips rendering (and renumbers from zero) bars for days with no data, so an `index`
// handed to a label callback doesn't line up with the day it looks like it should. LabelList's
// dataKey sidesteps both: it resolves the value straight from each rendered point's own
// underlying row via `getValueByDataKey`, correct regardless of stacking or skipped days.
function CalorieBadge(props: RawLabelProps) {
  const x = num(props.x)
  const y = num(props.y)
  const width = num(props.width)
  const value = num(props.value)
  if (x === undefined || y === undefined || width === undefined || value === undefined) return null
  return (
    <g transform={`translate(${x + width / 2}, ${y - 10})`}>
      <foreignObject x={-24} y={-10} width={48} height={20} style={{ overflow: 'visible' }}>
        <div className="macro-chart-badge">{Math.round(value)}</div>
      </foreignObject>
    </g>
  )
}

function segmentLabel(letter: string) {
  return (props: RawLabelProps) => {
    const x = num(props.x)
    const y = num(props.y)
    const width = num(props.width)
    const height = num(props.height)
    const value = num(props.value)
    if (x === undefined || y === undefined || width === undefined || height === undefined || value === undefined) {
      return null
    }
    if (height < 16) return null
    return (
      <text
        x={x + width / 2}
        y={y + height / 2}
        textAnchor="middle"
        dominantBaseline="central"
        className="macro-chart-segment-label"
      >
        {Math.round(value)}
        {letter}
      </text>
    )
  }
}

// Days with nothing logged carry calories: 0 (the backend fills every day in range, not just ones
// with entries) - counting those would drag the average toward zero on a sparsely-logged range, so
// only days that actually have food logged contribute to it.
function averageCalories(macros: { calories: number }[]): number | null {
  const loggedDays = macros.filter((day) => day.calories > 0)
  if (loggedDays.length === 0) return null
  return loggedDays.reduce((sum, day) => sum + day.calories, 0) / loggedDays.length
}

// The stacked segments are sized by each macro's calorie contribution (protein/carbs at 4 cal/g,
// fat at 9 cal/g) rather than raw grams, so bar height always tracks the calorie badge above it -
// a gram-based stack could make a higher-calorie, fattier day render shorter than a lower-calorie,
// carbier one. The in-bar labels still show grams (via each LabelList's own dataKey), only the
// segment height itself is calorie-weighted. There's still no calorie-scaled axis to hang a
// Recharts <ReferenceLine> off, since the axis is hidden. Rather than fight Recharts' multi-axis
// wiring for an axis no Bar uses, this maps `average` onto the plot area directly (0 at the bottom,
// maxCalories at the top) and draws a plain SVG line - geometrically independent of the bars' own
// scale. The "avg N cal" text lives in the card header instead of riding on the line itself: with narrow bars
// (30-day view especially) a tall day's bar/badge can sit right where the line crosses, so an
// in-chart label risked getting covered or looking like a mismatched box floating over a bar.
function AverageCalorieLine({ average, maxCalories }: { average: number; maxCalories: number }) {
  const plotArea = usePlotArea()
  if (!plotArea) return null
  const y = plotArea.y + plotArea.height * (1 - average / maxCalories)
  return (
    <line
      x1={plotArea.x}
      x2={plotArea.x + plotArea.width}
      y1={y}
      y2={y}
      stroke="var(--text-muted)"
      strokeDasharray="4 4"
      strokeWidth={1.5}
    />
  )
}

export default function MacroBarChart() {
  const dispatch = useAppDispatch()
  const range = useAppSelector((state) => state.dashboard.range)
  const macros = useAppSelector((state) => state.dashboard.macros)
  const showInBarLabels = range === 7
  const average = averageCalories(macros)
  const maxCalories = Math.max(1, ...macros.map((day) => day.calories))
  // Stack segments by calorie contribution (Atwater factors: 4 cal/g for protein and carbs, 9 cal/g
  // for fat) rather than raw grams - see the comment above averageCalories for why bar height needs
  // to track the calorie badge rather than gram totals.
  const chartData = macros.map((day) => ({
    ...day,
    proteinCal: day.proteinG * 4,
    fatCal: day.fatG * 9,
    carbsCal: day.carbsG * 4,
  }))

  // Jumps to that day's food log. Wired on the chart container rather than the individual Bar
  // segments: Recharts only attaches a segment's own onClick to a hover-swapped "active" layer,
  // not the always-rendered one, so a per-Bar handler silently never fires on a plain click - the
  // chart-level handler instead gets activeLabel from Recharts' own coordinate-to-category
  // tracking, which works regardless of which stacked segment (or gap between them) was clicked.
  // closeOverlay is a no-op when the chart isn't inside the mobile bottom sheet (desktop sidebar).
  const handleChartClick = (state: MouseHandlerDataParam) => {
    const date = state.activeLabel
    if (typeof date !== 'string') return
    dispatch(setFoodEntriesDate(date))
    dispatch(setScreen('foods'))
    dispatch(closeOverlay())
  }

  return (
    <div className="dash-card macro-chart-card">
      <div className="dash-card-header">
        <span className="dash-card-title">Calories & macros</span>
        <div className="range-toggle">
          {([7, 30] as MacroRange[]).map((option) => (
            <button
              key={option}
              type="button"
              className={`range-toggle-segment ${range === option ? 'active' : ''}`}
              onClick={() => dispatch(setRange(option))}
            >
              {option}d
            </button>
          ))}
        </div>
      </div>
      {average !== null && <div className="macro-chart-avg-caption">avg {Math.round(average)} cal</div>}
      <ResponsiveContainer width="100%" height={250}>
        <BarChart
          data={chartData}
          margin={{ top: 24, right: 4, left: -28, bottom: 0 }}
          barCategoryGap={range === 30 ? 2 : 8}
          onClick={handleChartClick}
          className="macro-chart-clickable"
        >
          <XAxis
            dataKey="date"
            tickFormatter={formatDayLabel}
            stroke="var(--dash-text-tertiary)"
            fontSize="calc(9px * var(--font-scale))"
            fontFamily="var(--font-mono)"
            interval={range === 30 ? 3 : 0}
          />
          <YAxis hide />
          <Bar dataKey="proteinCal" stackId="macros" fill="var(--macro-protein)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="proteinG" content={segmentLabel('P')} />}
          </Bar>
          <Bar dataKey="fatCal" stackId="macros" fill="var(--macro-fat)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="fatG" content={segmentLabel('F')} />}
          </Bar>
          <Bar dataKey="carbsCal" stackId="macros" fill="var(--macro-carbs)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="carbsG" content={segmentLabel('C')} />}
            <LabelList dataKey="calories" content={CalorieBadge} />
          </Bar>
          {average !== null && <AverageCalorieLine average={average} maxCalories={maxCalories} />}
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
