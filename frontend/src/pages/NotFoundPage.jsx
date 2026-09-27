import { Link } from 'react-router'

// Any address that isn't a page
export default function NotFoundPage() {
  return (
    <div className="py-16 text-center">
      <p className="text-sm font-medium text-slate-500">404</p>
      <h1 className="mt-1 text-2xl font-semibold text-slate-900">There's no page here</h1>
      <p className="mt-2 text-slate-600">The link may be old, or the address mistyped.</p>
      <Link
        to="/"
        className="mt-6 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800"
      >
        Go to the dashboard
      </Link>
    </div>
  )
}
