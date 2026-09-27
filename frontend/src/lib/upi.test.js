import { describe, expect, it } from 'vitest'
import { isValidUpiId, upiPayLink } from './upi.js'

describe('upiPayLink', () => {
  it('fills in the payee, amount in rupees and an encoded note', () => {
    expect(upiPayLink({ upiId: 'rohan.k@okaxis', name: 'Rohan Kulkarni', amountPaise: 120000, note: 'Goa trip' })).toBe(
      'upi://pay?pa=rohan.k@okaxis&pn=Rohan%20Kulkarni&am=1200.00&cu=INR&tn=Goa%20trip',
    )
    expect(upiPayLink({ upiId: 'a@ybl', name: 'A & B', amountPaise: 5 })).toBe('upi://pay?pa=a@ybl&pn=A%20%26%20B&am=0.05&cu=INR')
  })
})

describe('isValidUpiId', () => {
  it('accepts name@handle and nothing else', () => {
    expect(isValidUpiId('rohan.k@okaxis')).toBe(true)
    expect(isValidUpiId(' 9876543210@ybl ')).toBe(true)
    expect(isValidUpiId('rohan')).toBe(false)
    expect(isValidUpiId('rohan@')).toBe(false)
    expect(isValidUpiId('two words@okaxis')).toBe(false)
  })
})
