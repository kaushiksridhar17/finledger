import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.js'
import AuthLayout from '../components/AuthLayout.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'
import TextField from '../components/TextField.jsx'

export default function LoginPage() {
  const { status, login, startDemo } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [startingDemo, setStartingDemo] = useState(false)

  // Where to go after logging in: the page they were sent away from, or the dashboard
  const from = location.state?.from || '/'

  if (status === 'authenticated') {
    return <Navigate to={from} replace />
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})

    try {
      await login(form.email, form.password)
      navigate(from, { replace: true })
    } catch (err) {
      const errors = err.fieldErrors ?? {}
      setFieldErrors(errors)
      setError(Object.keys(errors).length ? null : err.message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDemo() {
    setStartingDemo(true)
    setError(null)
    setFieldErrors({})
    try {
      await startDemo()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
      setStartingDemo(false)
    }
  }

  const busy = submitting || startingDemo

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Log in to see your money in one place."
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-emerald-600 hover:text-emerald-700">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />

        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          value={form.email}
          onChange={handleChange}
          error={fieldErrors.email}
        />

        <TextField
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={form.password}
          onChange={handleChange}
          error={fieldErrors.password}
        />

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-lg bg-emerald-600 px-4 py-2.5 font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
        >
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
      </form>

      <div className="my-5 flex items-center gap-3 text-xs uppercase tracking-wide text-slate-400">
        <span className="h-px flex-1 bg-slate-200" />
        or
        <span className="h-px flex-1 bg-slate-200" />
      </div>

      <button
        type="button"
        onClick={handleDemo}
        disabled={busy}
        className="w-full rounded-lg px-4 py-2.5 font-medium text-slate-800 ring-1 ring-slate-300 hover:bg-slate-50 disabled:opacity-60"
      >
        {startingDemo ? 'Setting up your demo...' : 'Try the demo'}
      </button>
      <p className="mt-2 text-center text-xs text-slate-500">
        A private sample account with a year of transactions. No sign-up needed.
      </p>
    </AuthLayout>
  )
}
