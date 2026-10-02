/** "11 99999-8888" ou "+55 11 99999-8888" para E.164 (+5511999998888); null se não parecer um número brasileiro. */
export function toE164BR(input: string): string | null {
  const digits = input.replace(/\D/g, '')
  const national = digits.startsWith('55') && digits.length >= 12 ? digits.slice(2) : digits
  return /^\d{10,11}$/.test(national) ? `+55${national}` : null
}
