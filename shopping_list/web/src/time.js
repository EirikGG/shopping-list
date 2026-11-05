// The backend sends LocalDateTime values as ISO strings with no time zone,
// such as '2026-10-10T19:05:03.123456'. They are the server's clock time,
// which is UTC in the container, so they are read as UTC unless they name a
// zone. The fraction is cut to milliseconds, as not every browser parses more.
export function parseBackendTime(value) {
  const trimmed = value.replace(/(\.\d{3})\d+/, '$1')
  const hasZone = /(Z|[+-]\d\d:?\d\d)$/.test(trimmed)
  return new Date(hasZone ? trimmed : `${trimmed}Z`)
}

// Just the time for today, the date and time otherwise, and the year as well
// when it is not this year.
export function formatTime(date, locale) {
  const now = new Date()
  const options = { hour: '2-digit', minute: '2-digit' }

  if (date.toDateString() !== now.toDateString()) {
    options.day = 'numeric'
    options.month = 'short'
    if (date.getFullYear() !== now.getFullYear()) {
      options.year = 'numeric'
    }
  }

  return new Intl.DateTimeFormat(locale, options).format(date)
}

// Shorter, for the rows of a list: just the time for today, otherwise just
// the date, with the year when it is not this year.
export function formatShortTime(date, locale) {
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(date)
  }
  const options = { day: 'numeric', month: 'short' }
  if (date.getFullYear() !== now.getFullYear()) {
    options.year = 'numeric'
  }
  return new Intl.DateTimeFormat(locale, options).format(date)
}
