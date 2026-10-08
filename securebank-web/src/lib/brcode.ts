/**
 * "Pix Copia e Cola" (BR Code, EMV QRCPS): texto em blocos id(2) + tamanho(2) + valor, fechado por um CRC16. É o mesmo texto
 * que vira o QR code. Gerar e ler acontece só no navegador; o servidor não participa. Mesma regra do app Android.
 */

export class InvalidBrCodeError extends Error {}

/** Estático traz a [key]; dinâmico (cobrança) traz a [location] e nenhuma chave. */
export interface BrCodeData {
  key: string | null
  location: string | null
  amount: string | null
  name: string
  city: string
  txid: string
}

const GUI = 'br.gov.bcb.pix'

/** CRC-16/CCITT-FALSE (polinômio 0x1021, início 0xFFFF), em 4 hexadecimais maiúsculos. */
export function crc16(input: string): string {
  let crc = 0xffff
  for (let i = 0; i < input.length; i++) {
    crc ^= (input.charCodeAt(i) & 0xff) << 8
    for (let b = 0; b < 8; b++) {
      crc = crc & 0x8000 ? ((crc << 1) ^ 0x1021) & 0xffff : (crc << 1) & 0xffff
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0')
}

function tlv(id: string, value: string): string {
  if (value.length > 99) throw new Error(`campo ${id} grande demais`)
  return id + String(value.length).padStart(2, '0') + value
}

/** Nome e cidade do BR Code: maiúsculas, sem acento e só A-Z/0-9/espaço. */
function clean(text: string, max: number): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9 ]/g, '')
    .trim()
    .slice(0, max)
}

function close(body: string): string {
  const withHeader = body + '6304'
  return withHeader + crc16(withHeader)
}

/** Texto do QR para receber na [key]; [amount] (ex.: "10.50") é opcional (sem valor, quem paga digita). */
export function encode(key: string, name: string, options: { city?: string; amount?: string | null; txid?: string } = {}): string {
  if (!key.trim() || key.length > 77) throw new Error('chave Pix inválida')
  const merchant = tlv('00', GUI) + tlv('01', key)
  return close(
    tlv('00', '01') + tlv('01', '11') + tlv('26', merchant) + tlv('52', '0000') + tlv('53', '986') +
      (options.amount ? tlv('54', options.amount) : '') + tlv('58', 'BR') + tlv('59', clean(name, 25) || 'RECEBEDOR') +
      tlv('60', clean(options.city ?? 'SAO PAULO', 15) || 'BRASIL') + tlv('62', tlv('05', options.txid || '***')),
  )
}

/** QR DINÂMICO de uma cobrança: em vez da chave leva o endereço onde a cobrança mora (uso único, valor fixo, validade). */
export function encodeDynamic(location: string, name: string, city = 'SAO PAULO'): string {
  if (!location.trim() || location.length > 77) throw new Error('endereço da cobrança inválido')
  const merchant = tlv('00', GUI) + tlv('25', location)
  return close(
    tlv('00', '01') + tlv('01', '12') + tlv('26', merchant) + tlv('52', '0000') + tlv('53', '986') + tlv('58', 'BR') +
      tlv('59', clean(name, 25) || 'RECEBEDOR') + tlv('60', clean(city, 15) || 'BRASIL') + tlv('62', tlv('05', '***')),
  )
}

const chargePath = /(?:^|\/)charges\/([A-Za-z0-9]{26,35})\/?$/

/**
 * Identificador da cobrança dentro do endereço do QR dinâmico. O front NUNCA acessa a URL do código (quem fez o QR poderia
 * apontá-la para qualquer servidor): só extrai o txid e consulta a PRÓPRIA API.
 */
export const chargeTxid = (location: string): string | null => chargePath.exec(location.trim())?.[1] ?? null

/** Parece um copia-e-cola (e não uma chave)? Todo BR Code começa pelo formato "000201". */
export const looksLikeCode = (input: string) => input.trim().startsWith('000201')

function parse(text: string): Map<string, string> {
  const fields = new Map<string, string>()
  let i = 0
  while (i < text.length) {
    if (i + 4 > text.length) throw new InvalidBrCodeError('Código Pix incompleto.')
    const id = text.slice(i, i + 2)
    const length = Number(text.slice(i + 2, i + 4))
    if (!/^\d\d$/.test(text.slice(i + 2, i + 4))) throw new InvalidBrCodeError('Código Pix inválido.')
    if (i + 4 + length > text.length) throw new InvalidBrCodeError('Código Pix incompleto.')
    fields.set(id, text.slice(i + 4, i + 4 + length))
    i += 4 + length
  }
  return fields
}

/** Lê um código colado. Confere o CRC (um caractere errado é pego aqui, antes de qualquer consulta). */
export function decode(code: string): BrCodeData {
  const text = code.trim()
  if (text.length < 12 || !text.startsWith('000201')) throw new InvalidBrCodeError('Não parece um código Pix.')
  if (text.slice(-8, -4) !== '6304') throw new InvalidBrCodeError('Código Pix incompleto.')
  if (text.slice(-4).toUpperCase() !== crc16(text.slice(0, -4))) throw new InvalidBrCodeError('Código Pix corrompido. Copie de novo.')

  const fields = parse(text.slice(0, -8))
  const merchantText = fields.get('26')
  if (!merchantText) throw new InvalidBrCodeError('Não é um código Pix.')
  const merchant = parse(merchantText)
  if ((merchant.get('00') ?? '').toLowerCase() !== GUI) throw new InvalidBrCodeError('Não é um código Pix.')
  const key = merchant.get('01') ?? null
  const location = merchant.get('25') ?? null
  if (key === null && location === null) throw new InvalidBrCodeError('O código não traz uma chave Pix.')
  return {
    key,
    location: key === null ? location : null,
    amount: fields.get('54') ?? null,
    name: fields.get('59') ?? '',
    city: fields.get('60') ?? '',
    txid: parse(fields.get('62') ?? '').get('05') ?? '***',
  }
}
