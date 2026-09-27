export const ACCOUNT_TYPES = [
  { value: 'BANK', label: 'Bank account' },
  { value: 'CASH', label: 'Cash' },
  { value: 'CREDIT_CARD', label: 'Credit card' },
  { value: 'WALLET', label: 'Wallet (Paytm, PhonePe...)' },
]

export function accountTypeLabel(value) {
  return ACCOUNT_TYPES.find((t) => t.value === value)?.label ?? value
}
