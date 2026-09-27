import { Component } from 'react'

// Catches a crash while drawing a page and shows a message instead of a blank screen.
// AppLayout gives it a new key on every page change, so moving to another page clears the error.
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('Page crashed', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children

    // A new version was deployed while the tab was open, so an old page file is gone: reloading fixes it
    const staleBuild = /dynamically imported module|Importing a module script failed/i.test(String(this.state.error))

    return (
      <div role="alert" className="py-16 text-center">
        <h1 className="text-2xl font-semibold text-slate-900">This page didn't load</h1>
        <p className="mt-2 text-slate-600">
          {staleBuild ? 'FinLedger was updated since you opened it.' : 'Something went wrong while showing it.'} Reloading
          usually fixes it.
        </p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="mt-6 rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800"
        >
          Reload
        </button>
      </div>
    )
  }
}
