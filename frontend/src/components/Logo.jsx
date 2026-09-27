// The rupee mark and name, used in the header and on the login pages
export default function Logo({ size = 'md' }) {
  const mark = size === 'lg' ? 'h-9 w-9 text-lg' : 'h-7 w-7 text-base'
  const name = size === 'lg' ? 'text-xl' : 'text-lg'
  return (
    <span className="inline-flex items-center gap-2">
      <span className={`inline-flex items-center justify-center rounded-md bg-emerald-700 font-semibold text-white ${mark}`} aria-hidden="true">
        ₹
      </span>
      <span className={`font-semibold tracking-tight text-slate-900 ${name}`}>FinLedger</span>
    </span>
  )
}
