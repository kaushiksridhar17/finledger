import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatDate } from '../../lib/dates.js'
import { formatCompactPaise, formatPaise } from '../../lib/money.js'
import { shortMonth } from '../../lib/months.js'

// The same colour-blind-safe pair as the dashboard chart (checked with a contrast/CVD validator)
const VALUE_COLOR = '#2a78d6'
const INVESTED_COLOR = '#eb6834'

// Portfolio value against money put in, at each month end and today. The gap between the lines is the gain.
export default function PortfolioChart({ history }) {
  const rows = history.map((point, index) => ({
    ...point,
    label: index === history.length - 1 ? 'Now' : shortMonth(point.date),
  }))

  return (
    <div>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="#e2e8f0" />
            <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: '#cbd5e1' }} tick={{ fill: '#64748b', fontSize: 12 }} />
            <YAxis
              tickFormatter={formatCompactPaise}
              tickLine={false}
              axisLine={false}
              width={64}
              tick={{ fill: '#64748b', fontSize: 12 }}
            />
            <Tooltip content={<PointTooltip />} cursor={{ stroke: '#94a3b8', strokeDasharray: '3 3' }} />
            <Legend iconType="plainline" formatter={(value) => <span className="text-sm text-slate-600">{value}</span>} />
            <Line
              type="monotone"
              dataKey="valuePaise"
              name="Value"
              stroke={VALUE_COLOR}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, stroke: '#ffffff', strokeWidth: 2 }}
            />
            <Line
              type="stepAfter"
              dataKey="investedPaise"
              name="Invested"
              stroke={INVESTED_COLOR}
              strokeWidth={2}
              strokeDasharray="5 4"
              dot={false}
              activeDot={{ r: 4, stroke: '#ffffff', strokeWidth: 2 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <details className="mt-3 text-sm">
        <summary className="cursor-pointer text-slate-500 hover:text-slate-700">View as table</summary>
        <table className="mt-2 w-full text-left">
          <thead className="text-slate-500">
            <tr>
              <th className="py-1 font-medium">Date</th>
              <th className="py-1 text-right font-medium">Invested</th>
              <th className="py-1 text-right font-medium">Value</th>
              <th className="py-1 text-right font-medium">Gain</th>
            </tr>
          </thead>
          <tbody className="tabular-nums text-slate-700">
            {rows.map((row) => (
              <tr key={row.date} className="border-t border-slate-100">
                <td className="py-1">{formatDate(row.date)}</td>
                <td className="py-1 text-right">{formatPaise(row.investedPaise)}</td>
                <td className="py-1 text-right">{formatPaise(row.valuePaise)}</td>
                <td className="py-1 text-right">{formatPaise(row.valuePaise - row.investedPaise)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}

function PointTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <div className="rounded-lg bg-white px-3 py-2 text-sm shadow-lg ring-1 ring-slate-200">
      <p className="font-medium text-slate-900">{formatDate(row.date)}</p>
      <TooltipRow color={VALUE_COLOR} label="Value" value={formatPaise(row.valuePaise)} />
      <TooltipRow color={INVESTED_COLOR} label="Invested" value={formatPaise(row.investedPaise)} dashed />
      <p className="mt-1 border-t border-slate-100 pt-1 text-slate-600">
        Gain <span className="float-right ml-4 font-medium text-slate-900">{formatPaise(row.valuePaise - row.investedPaise)}</span>
      </p>
    </div>
  )
}

function TooltipRow({ color, label, value, dashed = false }) {
  return (
    <p className="flex items-center gap-2 text-slate-600">
      <span className="inline-block w-3 border-t-2" style={{ borderColor: color, borderStyle: dashed ? 'dashed' : 'solid' }} />
      {label}
      <span className="ml-auto pl-4 font-medium text-slate-900">{value}</span>
    </p>
  )
}
