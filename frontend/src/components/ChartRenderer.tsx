import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { ChartSeries } from '../store/chatSlice'

const LINE_COLORS = ['var(--accent)', '#3b82f6', '#22c55e', '#f59e0b']

interface MergedPoint {
  at: string
  [key: string]: string | number | null
}

function mergeSeries(series: ChartSeries[]): MergedPoint[] {
  const length = series[0]?.points.length ?? 0
  const merged: MergedPoint[] = []
  for (let i = 0; i < length; i++) {
    const point: MergedPoint = { at: series[0].points[i].at }
    for (const s of series) {
      const p = s.points[i]
      if (!p) continue
      point[s.label] = p.value
      if (p.target !== null) {
        point[`${s.label} target`] = p.target
      }
    }
    merged.push(point)
  }
  return merged
}

function formatTick(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric' })
}

export default function ChartRenderer({ series }: { series: ChartSeries[] }) {
  const data = mergeSeries(series)

  return (
    <div className="chart-container">
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
          <XAxis dataKey="at" tickFormatter={formatTick} stroke="var(--text-muted)" fontSize={12} />
          <YAxis stroke="var(--text-muted)" fontSize={12} />
          <Tooltip
            labelFormatter={(value) => new Date(value as string).toLocaleString()}
            contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', color: 'var(--text-primary)' }}
          />
          {series.map((s, i) => (
            <Line
              key={s.label}
              type="monotone"
              dataKey={s.label}
              stroke={LINE_COLORS[i % LINE_COLORS.length]}
              dot={false}
              strokeWidth={2}
            />
          ))}
          {series.map((s, i) =>
            s.points.some((p) => p.target !== null) ? (
              <Line
                key={`${s.label} target`}
                type="monotone"
                dataKey={`${s.label} target`}
                stroke={LINE_COLORS[i % LINE_COLORS.length]}
                strokeDasharray="4 4"
                dot={false}
                strokeWidth={1.5}
              />
            ) : null,
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
