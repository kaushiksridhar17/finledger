// UPI payment links. Opening upi://pay?... on a phone offers every installed UPI app
// (GPay, PhonePe, Paytm...) with the payee, amount and note already filled in.
// On a computer, the same link shown as a QR code can be scanned with a phone.

import { paiseToInput } from './money.js'

// name@handle, the same rule as the backend: letters, digits, dots, dashes, underscores, then the bank's handle
export const UPI_ID_PATTERN = /^[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9]{1,63}$/

export function isValidUpiId(text) {
  return UPI_ID_PATTERN.test(String(text ?? '').trim())
}

// upi://pay?pa=rohan.k@okaxis&pn=Rohan%20Kulkarni&am=1200.00&cu=INR&tn=Goa%20trip
export function upiPayLink({ upiId, name, amountPaise, note }) {
  const params = [
    `pa=${upiId}`,
    `pn=${encodeURIComponent(name)}`,
    `am=${paiseToInput(amountPaise)}`,
    'cu=INR',
  ]
  if (note) params.push(`tn=${encodeURIComponent(note.slice(0, 50))}`)
  return `upi://pay?${params.join('&')}`
}
