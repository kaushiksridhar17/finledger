export default function ErrorBanner({ message }) {
  if (!message) return null
  return (
    <div role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 border border-rose-200">
      {message}
    </div>
  )
}
