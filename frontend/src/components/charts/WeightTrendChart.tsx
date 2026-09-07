import {
  CartesianGrid,
  ComposedChart,
  ErrorBar,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipContentProps,
} from 'recharts'
import { useAppSelector } from '../../store/hooks'
import type { GoalLine } from '../../store/dashboardSlice'

interface MergedPoint {
  date: string
  actual: number | null
  trend: number | null
  goal: number | null
}

function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

// Asymmetric ErrorBar range is [value - low, value + high]; with value = actual, this spans
// exactly [min(actual, trend), max(actual, trend)] regardless of which one is bigger, so it draws
// as a line from the marker straight to the trend line rather than a symmetric whisker around it.
function pull(row: MergedPoint): [number, number] {
  if (row.actual === null || row.trend === null) return [0, 0]
  const diff = row.trend - row.actual
  return diff >= 0 ? [0, diff] : [-diff, 0]
}

// Only the diamond markers (actual weigh-ins) get a tooltip - hovering elsewhere along the trend
// line shows nothing, since there's no reading there to report.
function WeightTooltip({ active, payload }: TooltipContentProps) {
  const row = payload?.[0]?.payload as MergedPoint | undefined
  if (!active || !row || row.actual === null) return null
  return (
    <div className="dash-tooltip">
      <div className="dash-tooltip-date">{formatDate(row.date)}</div>
      <div className="dash-tooltip-value">{row.actual.toFixed(1)} lbs</div>
    </div>
  )
}

function goalValueOn(goal: GoalLine, date: string): number {
  const days = (Date.parse(`${date}T00:00:00`) - Date.parse(`${goal.startDate}T00:00:00`)) / (24 * 60 * 60 * 1000)
  return goal.startWeightLbs + goal.dailyRateLbs * days
}

function buildSeries(
  actual: { date: string; weightLbs: number }[],
  smoothed: { date: string; value: number }[],
  goal: GoalLine | null,
): MergedPoint[] {
  const dates = new Set<string>()
  actual.forEach((p) => dates.add(p.date))
  smoothed.forEach((p) => dates.add(p.date))
  const sortedDates = Array.from(dates).sort()

  const actualByDate = new Map(actual.map((p) => [p.date, p.weightLbs]))
  const smoothedByDate = new Map(smoothed.map((p) => [p.date, p.value]))

  return sortedDates.map((date) => ({
    date,
    actual: actualByDate.get(date) ?? null,
    trend: smoothedByDate.get(date) ?? null,
    goal: goal ? goalValueOn(goal, date) : null,
  }))
}

// Non-interactive per the design handoff except for a hover tooltip on the weigh-in markers (see
// WeightTooltip): no click handlers. Scatter for actual weigh-ins, solid line for the smoothed
// Hacker's Diet trend, dashed line for the goal trajectory - computed client-side from the
// GoalLine anchor point/slope the backend returns, rather than the backend materializing every
// point of a straight line.
export default function WeightTrendChart() {
  const trend = useAppSelector((state) => state.dashboard.weightTrend)
  const tdee = useAppSelector((state) => state.dashboard.tdee)

  if (!trend) return null

  const data = buildSeries(trend.actual, trend.smoothed, trend.goal)

  return (
    <div className="dash-card weight-chart-card">
      <div className="dash-card-header">
        <span className="dash-card-title">Weight trend</span>
        <span className="dash-card-subtitle">30 days</span>
      </div>
      {tdee && (
        <div className="dash-card-stat">
          {tdee.estimatedCalories !== null ? (
            <>
              Est. TDEE ({tdee.windowDays}-day): {tdee.estimatedCalories.toLocaleString()} ±{' '}
              {tdee.standardErrorCalories?.toLocaleString()} cal/day
              {tdee.caveat && <div className="dash-card-stat-caveat">⚠ {tdee.caveat}</div>}
            </>
          ) : (
            `TDEE estimate: ${tdee.unavailableReason}`
          )}
        </div>
      )}
      <ResponsiveContainer width="100%" height={220}>
        <ComposedChart data={data} margin={{ top: 8, right: 8, left: -20, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--dash-grid)" />
          <XAxis
            dataKey="date"
            tickFormatter={formatDate}
            stroke="var(--dash-text-tertiary)"
            fontSize={9}
            fontFamily="var(--font-mono)"
            minTickGap={24}
          />
          <YAxis stroke="var(--dash-text-tertiary)" fontSize={9} fontFamily="var(--font-mono)" domain={['auto', 'auto']} />
          <Tooltip content={WeightTooltip} cursor={{ stroke: 'var(--dash-grid)' }} />
          {trend.goal && (
            <Line
              dataKey="goal"
              stroke="var(--status-retrying)"
              strokeDasharray="4 4"
              dot={false}
              strokeWidth={1.5}
              isAnimationActive={false}
              connectNulls
            />
          )}
          <Line
            dataKey="trend"
            stroke="var(--accent)"
            dot={false}
            strokeWidth={2.2}
            isAnimationActive={false}
            connectNulls
          />
          <Scatter dataKey="actual" fill="var(--text-primary)" shape="diamond" isAnimationActive={false}>
            {/* Thin vertical line from each weigh-in marker to the trend line, showing how much
                that point pulled the smoothed trend up or down. */}
            <ErrorBar dataKey={pull} direction="y" width={0} strokeWidth={1} stroke="var(--status-offline)" />
          </Scatter>
        </ComposedChart>
      </ResponsiveContainer>
      <div className="weight-chart-legend">
        <span>
          <span className="legend-swatch legend-swatch--dot" /> weigh-in
        </span>
        <span>
          <span className="legend-swatch legend-swatch--line" /> trend
        </span>
        {trend.goal && (
          <span>
            <span className="legend-swatch legend-swatch--goal" /> goal
          </span>
        )}
      </div>
      <div className="sql-table-wrapper weight-table-wrapper">
        <table className="sql-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Weight</th>
              <th>Trend</th>
              <th>Variance</th>
            </tr>
          </thead>
          <tbody>
            {data
              .filter((row) => row.actual !== null)
              .reverse()
              .map((row) => {
                const variance = row.actual !== null && row.trend !== null ? row.actual - row.trend : null
                return (
                  <tr key={row.date}>
                    <td>{formatDate(row.date)}</td>
                    <td>{row.actual?.toFixed(1)}</td>
                    <td>{row.trend !== null ? row.trend.toFixed(1) : '—'}</td>
                    <td className={variance !== null ? (variance >= 0 ? 'variance-gain' : 'variance-loss') : ''}>
                      {variance !== null ? `${variance >= 0 ? '+' : ''}${variance.toFixed(1)}` : '—'}
                    </td>
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
