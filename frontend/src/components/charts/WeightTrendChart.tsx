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
import { useGetTdeeQuery, useGetWeightTrendQuery } from '../../store/dashboardApi'

interface MergedPoint {
  date: string
  actual: number | null
  trend: number | null
  fit: number | null
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

function daysSince(origin: string, date: string): number {
  return (Date.parse(`${date}T00:00:00`) - Date.parse(`${origin}T00:00:00`)) / (24 * 60 * 60 * 1000)
}

/** Average month length (365.25 / 12), for the fit's lb/mo rate. */
const DAYS_PER_MONTH = 30.4375

interface LinearFit {
  origin: string
  intercept: number
  slopePerDay: number
}

// Ordinary least-squares line through the weigh-ins shown (the raw diamonds, not the smoothed
// trend) - the straight-line pace of the last 30 days. Null with fewer than two distinct days.
function fitLine(actual: { date: string; weightLbs: number }[]): LinearFit | null {
  if (actual.length < 2) return null
  const origin = actual.map((p) => p.date).sort()[0]
  const xs = actual.map((p) => daysSince(origin, p.date))
  const ys = actual.map((p) => p.weightLbs)
  const meanX = xs.reduce((a, b) => a + b, 0) / xs.length
  const meanY = ys.reduce((a, b) => a + b, 0) / ys.length
  const sxx = xs.reduce((sum, x) => sum + (x - meanX) ** 2, 0)
  if (sxx === 0) return null
  const sxy = xs.reduce((sum, x, i) => sum + (x - meanX) * (ys[i] - meanY), 0)
  const slopePerDay = sxy / sxx
  return { origin, intercept: meanY - slopePerDay * meanX, slopePerDay }
}

function buildSeries(
  actual: { date: string; weightLbs: number }[],
  smoothed: { date: string; value: number }[],
  fit: LinearFit | null,
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
    fit: fit ? fit.intercept + fit.slopePerDay * daysSince(fit.origin, date) : null,
  }))
}

// Non-interactive per the design handoff except for a hover tooltip on the weigh-in markers (see
// WeightTooltip): no click handlers. Scatter for actual weigh-ins, solid line for the smoothed
// Hacker's Diet trend, dashed line for the linear fit of the visible weigh-ins (fitLine).
export default function WeightTrendChart() {
  const trend = useGetWeightTrendQuery().data
  const tdee = useGetTdeeQuery().data

  if (!trend) return null

  const fit = fitLine(trend.actual)
  const data = buildSeries(trend.actual, trend.smoothed, fit)

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
            fontSize="calc(9px * var(--font-scale))"
            fontFamily="var(--font-mono)"
            minTickGap={24}
          />
          <YAxis
            stroke="var(--dash-text-tertiary)"
            fontSize="calc(9px * var(--font-scale))"
            fontFamily="var(--font-mono)"
            domain={['auto', 'auto']}
          />
          <Tooltip content={WeightTooltip} cursor={{ stroke: 'var(--dash-grid)' }} />
          {fit && (
            <Line
              dataKey="fit"
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
          <span className="legend-swatch legend-swatch--diamond" /> weigh-in
        </span>
        <span>
          <span className="legend-swatch legend-swatch--line" /> trend
        </span>
        {fit && (
          <span>
            <span className="legend-swatch legend-swatch--fit" />
            <span>{(fit.slopePerDay * 7).toFixed(1)} lb/wk</span>
            <span className="legend-rate-gap">{(fit.slopePerDay * DAYS_PER_MONTH).toFixed(1)} lb/mo</span>
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
