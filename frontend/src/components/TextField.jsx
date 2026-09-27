// Labelled input with an optional error message underneath
export default function TextField({ label, name, type = 'text', value, onChange, error, autoComplete }) {
  const id = `field-${name}`
  const border = error
    ? 'border-rose-400 focus:ring-rose-200'
    : 'border-slate-300 focus:border-emerald-500 focus:ring-emerald-200'

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        aria-invalid={Boolean(error)}
        className={`mt-1 block w-full rounded-lg border px-3 py-2 text-slate-900 shadow-sm outline-none focus:ring-2 ${border}`}
      />
      {error && <p className="mt-1 text-sm text-rose-600">{error}</p>}
    </div>
  )
}
