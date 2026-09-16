import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipContentProps } from 'recharts'
import type { BloodPressureReading } from '../../store/dashboardSlice'

interface ChartPoint {
  time: number
  systolic: number
  diastolic: number
  bpm: number | null
}

function toChartPoints(readings: BloodPressureReading[]): ChartPoint[] {
  return readings.map((r) => ({
    time: Date.parse(r.timestamp),
    systolic: r.systolic,
    diastolic: r.diastolic,
    bpm: r.bpm,
  }))
}

function formatAxisDate(time: number): string {
  const date = new Date(time)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatTooltipDateTime(time: number): string {
  const date = new Date(time)
  return date.toLocaleString(undefined, { month: 'numeric', day: 'numeric', hour: 'numeric', minute: '2-digit' })
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

// Two Y axes (BP on the left, heart rate on the right) sharing one time-based X axis - readings
// land at their real time of day rather than snapping to evenly-spaced categories, so multiple
// same-day readings (a morning and an evening check, say) show their actual spacing. Only
// horizontal gridlines are drawn, capped at 4 ticks, so the chart doesn't turn into a grid of
// rules the way a default Recharts CartesianGrid would with two axes' worth of ticks.
export default function BloodPressureChart({ readings }: { readings: BloodPressureReading[] }) {
  const data = toChartPoints(readings)

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
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
          yAxisId="bp"
          domain={[60, 'auto']}
          tickCount={4}
          stroke="var(--dash-text-tertiary)"
          fontSize="calc(9px * var(--font-scale))"
          fontFamily="var(--font-mono)"
        />
        <YAxis
          yAxisId="bpm"
          orientation="right"
          domain={[40, 'auto']}
          tickCount={4}
          stroke="var(--dash-text-tertiary)"
          fontSize="calc(9px * var(--font-scale))"
          fontFamily="var(--font-mono)"
        />
        <Tooltip content={BloodPressureTooltip} cursor={{ stroke: 'var(--dash-grid)' }} />
        <Line
          yAxisId="bp"
          dataKey="systolic"
          stroke="var(--accent)"
          dot={{ r: 2.5 }}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
        <Line
          yAxisId="bp"
          dataKey="diastolic"
          stroke="var(--status-offline)"
          dot={{ r: 2.5 }}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
        <Line
          yAxisId="bpm"
          dataKey="bpm"
          stroke="var(--macro-carbs)"
          dot={{ r: 2.5 }}
          strokeWidth={1.75}
          isAnimationActive={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
