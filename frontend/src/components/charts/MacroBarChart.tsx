import { Bar, BarChart, LabelList, ResponsiveContainer, usePlotArea, XAxis, YAxis } from 'recharts'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { setRange, type MacroRange } from '../../store/dashboardSlice'

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

// Static, non-interactive stacked bar chart per the design handoff - no tooltip, legend, hover,
// or click targets. The calorie badge (above the stack) and the in-bar gram+letter labels are
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

// The visible bars are stacked macro grams, not calories - calories only ever appear as the badge
// text above each bar, positioned off the bar's own height, so there's no calorie-scaled axis to
// hang a Recharts <ReferenceLine> off. Rather than fight Recharts' multi-axis wiring for an axis no
// Bar uses, this maps `average` onto the plot area directly (0 at the bottom, maxCalories at the
// top) and draws a plain SVG line - geometrically independent of the bars' own gram scale. The
// "avg N cal" text lives in the card header instead of riding on the line itself: with narrow bars
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
        <BarChart data={macros} margin={{ top: 24, right: 4, left: -28, bottom: 0 }} barCategoryGap={range === 30 ? 2 : 8}>
          <XAxis
            dataKey="date"
            tickFormatter={formatDayLabel}
            stroke="var(--dash-text-tertiary)"
            fontSize="calc(9px * var(--font-scale))"
            fontFamily="var(--font-mono)"
            interval={range === 30 ? 3 : 0}
          />
          <YAxis hide />
          <Bar dataKey="proteinG" stackId="macros" fill="var(--macro-protein)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="proteinG" content={segmentLabel('P')} />}
          </Bar>
          <Bar dataKey="fatG" stackId="macros" fill="var(--macro-fat)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="fatG" content={segmentLabel('F')} />}
          </Bar>
          <Bar dataKey="carbsG" stackId="macros" fill="var(--macro-carbs)" isAnimationActive={false}>
            {showInBarLabels && <LabelList dataKey="carbsG" content={segmentLabel('C')} />}
            <LabelList dataKey="calories" content={CalorieBadge} />
          </Bar>
          {average !== null && <AverageCalorieLine average={average} maxCalories={maxCalories} />}
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
