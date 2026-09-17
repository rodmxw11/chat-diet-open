import type { Key } from 'react'
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Symbols,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts'
import type { BloodPressureReading } from '../../store/dashboardSlice'

interface ChartPoint {
  time: number
  systolic: number
  diastolic: number
  bpm: number | null
}

const SYSTOLIC_COLOR = 'var(--vitals-systolic)'
const DIASTOLIC_COLOR = 'var(--vitals-diastolic)'
const BPM_COLOR = 'var(--macro-carbs)'

function toChartPoints(readings: BloodPressureReading[]): ChartPoint[] {
  return readings.map((r) => ({
    time: Date.parse(r.timestamp),
    systolic: r.systolic,
    diastolic: r.diastolic,
    bpm: r.bpm,
  }))
}

export function average(values: number[]): number | null {
  if (values.length === 0) return null
  return values.reduce((sum, v) => sum + v, 0) / values.length
}

function formatAxisDate(time: number): string {
  const date = new Date(time)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatTooltipDateTime(time: number): string {
  const date = new Date(time)
  return date.toLocaleString(undefined, { month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit' })
}

function isMorning(time: number): boolean {
  return new Date(time).getHours() < 12
}

// Recharts' Line dot prop is normally one fixed shape for a whole series; rendering it as a
// per-point function instead lets the marker itself say AM (circle) vs PM (diamond), on top of
// the series' own color, without a fourth series or separate chart.
function renderTimeOfDayDot(color: string) {
  return ({ cx, cy, payload, key }: { cx?: number; cy?: number; payload?: ChartPoint; key?: Key | null }) => {
    if (cx == null || cy == null || !payload) return null
    return (
      <Symbols
        key={key}
        cx={cx}
        cy={cy}
        type={isMorning(payload.time) ? 'circle' : 'diamond'}
        size={30}
        fill={color}
        stroke={color}
      />
    )
  }
}

function BloodPressureTooltip({ active, payload }: TooltipContentProps) {
  const row = payload?.[0]?.payload as ChartPoint | undefined
  if (!active || !row) return null
  return (
    <div className="dash-tooltip">
      <div className="dash-tooltip-date">{formatTooltipDateTime(row.time)}</div>
      <div className="dash-tooltip-value">
        {row.systolic}/{row.diastolic}
        {row.bpm !== null && <> · {row.bpm} bpm</>}
      </div>
    </div>
  )
}

// One shared Y axis for all three series (SBP/DBP/BPM aren't the same unit, but a single scale is
// what was asked for) on a time-based X axis - readings land at their real time of day rather than
// snapping to evenly-spaced categories, so multiple same-day readings show their actual spacing.
// Only horizontal gridlines are drawn, capped at 4 ticks, and each series gets a light dashed
// reference line at its own average, labeled with that average, in the series' own color. Each
// point's marker shape also encodes time of day - circle before noon, diamond from noon on -
// independent of which series it belongs to (see the AM/PM legend below the chart).
export default function BloodPressureChart({ readings }: { readings: BloodPressureReading[] }) {
  const data = toChartPoints(readings)
  const systolicAvg = average(readings.map((r) => r.systolic))
  const diastolicAvg = average(readings.map((r) => r.diastolic))
  const bpmAvg = average(readings.map((r) => r.bpm).filter((v): v is number => v !== null))

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 8, right: 32, left: -12, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--dash-grid)" vertical={false} />
        <XAxis
          dataKey="time"
          type="number"
          domain={['dataMin', 'dataMax']}
          tickFormatter={formatAxisDate}
          stroke="var(--dash-text-tertiary)"
          fontSize="calc(9px * var(--font-scale))"
          fontFamily="var(--font-mono)"
          minTickGap={24}
        />
        <YAxis
          domain={['auto', 'auto']}
          tickCount={4}
          stroke="var(--dash-text-tertiary)"
          fontSize="calc(9px * var(--font-scale))"
          fontFamily="var(--font-mono)"
        />
        <Tooltip content={BloodPressureTooltip} cursor={{ stroke: 'var(--dash-grid)' }} />
        {systolicAvg !== null && (
          <ReferenceLine
            y={systolicAvg}
            stroke={SYSTOLIC_COLOR}
            strokeOpacity={0.5}
            strokeDasharray="4 4"
            label={{
              value: `${Math.round(systolicAvg)}`,
              position: 'right',
              fill: SYSTOLIC_COLOR,
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
            }}
          />
        )}
        {diastolicAvg !== null && (
          <ReferenceLine
            y={diastolicAvg}
            stroke={DIASTOLIC_COLOR}
            strokeOpacity={0.5}
            strokeDasharray="4 4"
            label={{
              value: `${Math.round(diastolicAvg)}`,
              position: 'right',
              fill: DIASTOLIC_COLOR,
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
            }}
          />
        )}
        {bpmAvg !== null && (
          <ReferenceLine
            y={bpmAvg}
            stroke={BPM_COLOR}
            strokeOpacity={0.5}
            strokeDasharray="4 4"
            label={{
              value: `${Math.round(bpmAvg)}`,
              position: 'right',
              fill: BPM_COLOR,
              fontSize: 10,
              fontFamily: 'var(--font-mono)',
            }}
          />
        )}
        <Line
          dataKey="systolic"
          stroke={SYSTOLIC_COLOR}
          dot={renderTimeOfDayDot(SYSTOLIC_COLOR)}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
        <Line
          dataKey="diastolic"
          stroke={DIASTOLIC_COLOR}
          dot={renderTimeOfDayDot(DIASTOLIC_COLOR)}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
        <Line
          dataKey="bpm"
          stroke={BPM_COLOR}
          dot={renderTimeOfDayDot(BPM_COLOR)}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
