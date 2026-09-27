import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCompactPaise, formatPaise } from '../../lib/money.js'
import { formatMonth, shortMonth } from '../../lib/months.js'

// Two series, so two colours from a colour-blind-safe palette (checked with a contrast/CVD validator)
const INCOME_COLOR = '#2a78d6'
const SPENDING_COLOR = '#eb6834'

// Income vs spending for 12 months. Click a month's bars to look at that month.
export default function MonthlyChart({ months, selectedMonth, onSelectMonth }) {
  const rows = months.map((m) => ({
    month: m.month,
    label: shortMonth(m.month),
    incomePaise: m.incomePaise,
    spendingPaise: m.spendingPaise,
  }))

  function handleBarClick(_entry, index) {
    onSelectMonth(rows[index].month)
  }

  return (
    <div>
      <div className="h-72">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} barGap={2} barCategoryGap="28%" margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke="#e2e8f0" />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={{ stroke: '#cbd5e1' }}
              tick={({ x, y, payload, index }) => (
                <text
                  x={x}
                  y={y + 14}
                  textAnchor="middle"
                  fontSize={12}
                  fill={rows[index]?.month === selectedMonth ? '#0f172a' : '#64748b'}
                  fontWeight={rows[index]?.month === selectedMonth ? 600 : 400}
                >
                  {payload.value}
                </text>
              )}
            />
            <YAxis
              tickFormatter={formatCompactPaise}
              tickLine={false}
              axisLine={false}
              width={60}
              tick={{ fill: '#64748b', fontSize: 12 }}
            />
            <Tooltip content={<MonthTooltip />} cursor={{ fill: '#f1f5f9' }} />
            <Legend
              iconType="square"
              iconSize={10}
              formatter={(value) => <span className="text-sm text-slate-600">{value}</span>}
            />
            <Bar
              dataKey="incomePaise"
              name="Income"
              fill={INCOME_COLOR}
              radius={[4, 4, 0, 0]}
              maxBarSize={16}
              cursor="pointer"
              onClick={handleBarClick}
            />
            <Bar
              dataKey="spendingPaise"
              name="Spending"
              fill={SPENDING_COLOR}
              radius={[4, 4, 0, 0]}
              maxBarSize={16}
              cursor="pointer"
              onClick={handleBarClick}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* The same numbers as a table, for screen readers and anyone who prefers exact figures */}
      <details className="mt-3 text-sm">
        <summary className="cursor-pointer text-slate-500 hover:text-slate-700">View as table</summary>
        <table className="mt-2 w-full text-left">
          <thead className="text-slate-500">
            <tr>
              <th className="py-1 font-medium">Month</th>
              <th className="py-1 text-right font-medium">Income</th>
              <th className="py-1 text-right font-medium">Spending</th>
              <th className="py-1 text-right font-medium">Saved</th>
            </tr>
          </thead>
          <tbody className="tabular-nums text-slate-700">
            {rows.map((row) => (
              <tr key={row.month} className="border-t border-slate-100">
                <td className="py-1">{formatMonth(row.month)}</td>
                <td className="py-1 text-right">{formatPaise(row.incomePaise)}</td>
                <td className="py-1 text-right">{formatPaise(row.spendingPaise)}</td>
                <td className="py-1 text-right">{formatPaise(row.incomePaise - row.spendingPaise)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  )
}

function MonthTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  const saved = row.incomePaise - row.spendingPaise

  return (
    <div className="rounded-lg bg-white px-3 py-2 text-sm shadow-lg border border-slate-200">
      <p className="font-medium text-slate-900">{formatMonth(row.month)}</p>
      <TooltipRow color={INCOME_COLOR} label="Income" value={formatPaise(row.incomePaise)} />
      <TooltipRow color={SPENDING_COLOR} label="Spending" value={formatPaise(row.spendingPaise)} />
      <p className="mt-1 border-t border-slate-100 pt-1 text-slate-600">
        Saved <span className="float-right ml-4 font-medium text-slate-900">{formatPaise(saved)}</span>
      </p>
    </div>
  )
}

function TooltipRow({ color, label, value }) {
  return (
    <p className="flex items-center gap-2 text-slate-600">
      <span className="inline-block h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: color }} />
      {label}
      <span className="ml-auto pl-4 font-medium text-slate-900">{value}</span>
    </p>
  )
}
