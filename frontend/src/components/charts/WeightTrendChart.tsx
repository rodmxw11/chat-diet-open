import { CartesianGrid, ComposedChart, Line, ResponsiveContainer, Scatter, XAxis, YAxis } from 'recharts'
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

// Non-interactive per the design handoff: no tooltip, no click handlers. Scatter for actual
// weigh-ins, solid line for the smoothed Hacker's Diet trend, dashed line for the goal
// trajectory - computed client-side from the GoalLine anchor point/slope the backend returns,
// rather than the backend materializing every point of a straight line.
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
          {tdee.estimatedCalories !== null
            ? `Est. TDEE (${tdee.windowDays}-day): ${tdee.estimatedCalories.toLocaleString()} cal/day`
            : `TDEE estimate: ${tdee.unavailableReason}`}
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
          <Scatter dataKey="actual" fill="var(--text-primary)" shape="diamond" isAnimationActive={false} />
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
