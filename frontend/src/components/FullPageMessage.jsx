export default function FullPageMessage({ text }) {
  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <p className="text-slate-500">{text}</p>
    </main>
  )
}
