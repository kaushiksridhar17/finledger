import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { getNotifications, markAllNotificationsRead, markNotificationRead } from '../api/notifications.js'

const POLL_MS = 60_000

function timeAgo(iso) {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  return `${days} ${days === 1 ? 'day' : 'days'} ago`
}

// Bell in the header with an unread count. Checks for new notifications every minute.
export default function NotificationBell() {
  const navigate = useNavigate()
  const [data, setData] = useState({ unreadCount: 0, items: [] })
  const [open, setOpen] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const panelRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    getNotifications()
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  useEffect(() => {
    const timer = setInterval(() => setReloadKey((k) => k + 1), POLL_MS)
    return () => clearInterval(timer)
  }, [])

  // Close the panel when clicking anywhere outside it
  useEffect(() => {
    if (!open) return undefined
    function handleClick(event) {
      if (panelRef.current && !panelRef.current.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  function toggle() {
    if (!open) setReloadKey((k) => k + 1)
    setOpen((o) => !o)
  }

  async function openItem(item) {
    setOpen(false)
    if (!item.read) {
      try {
        await markNotificationRead(item.id)
      } catch {
        // not important enough to show an error
      }
      setReloadKey((k) => k + 1)
    }
    if (item.link) navigate(item.link)
  }

  async function readAll() {
    try {
      await markAllNotificationsRead()
    } finally {
      setReloadKey((k) => k + 1)
    }
  }

  return (
    <div className="relative" ref={panelRef}>
      <button
        type="button"
        onClick={toggle}
        aria-label={data.unreadCount > 0 ? `Notifications, ${data.unreadCount} unread` : 'Notifications'}
        className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {data.unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-4 rounded-full bg-rose-600 px-1 text-center text-[10px] font-semibold leading-4 text-white">
            {data.unreadCount > 9 ? '9+' : data.unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-md bg-white shadow-lg border border-slate-200">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2">
            <span className="text-sm font-semibold text-slate-900">Notifications</span>
            {data.unreadCount > 0 && (
              <button type="button" onClick={readAll} className="text-xs font-medium text-emerald-700 hover:text-emerald-800">
                Mark all read
              </button>
            )}
          </div>

          {data.items.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-slate-500">No notifications.</p>
          ) : (
            <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
              {data.items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => openItem(item)}
                    className={`block w-full px-4 py-3 text-left hover:bg-slate-50 ${item.read ? '' : 'bg-emerald-50/50'}`}
                  >
                    <p className="flex items-start gap-2 text-sm font-medium text-slate-900">
                      {!item.read && <span className="mt-1.5 inline-block h-2 w-2 shrink-0 rounded-full bg-emerald-700" />}
                      <span>{item.title}</span>
                    </p>
                    <p className="mt-0.5 text-sm text-slate-600">{item.message}</p>
                    <p className="mt-1 text-xs text-slate-400">{timeAgo(item.createdAt)}</p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
