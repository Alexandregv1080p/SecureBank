import { describe, expect, it } from 'vitest'
import { amountError, buyCost, currencyName, formatForeign, rateLabel, sellProceeds, sideLabel } from './fx'

describe('compra e venda (mesma regra do servidor)', () => {
  it('a compra arredonda o custo para cima ao centavo', () => {
    expect(buyCost('100,00', '5.278000')).toBe('527.80')
    expect(buyCost('0,01', '5.278000')).toBe('0.06') // 0,05278 sobe
    expect(buyCost('1', '5.278000')).toBe('5.28')
    expect(buyCost('50,00', '5.278000')).toBe('263.90')
    expect(buyCost('1.234,56', '5.278000')).toBe('6516.01') // 6516,00768 sobe
  })

  it('a venda arredonda o que se recebe para baixo ao centavo', () => {
    expect(sellProceeds('100', '5.122000')).toBe('512.20')
    expect(sellProceeds('0,99', '5.122000')).toBe('5.07') // 5,07078 desce
    expect(sellProceeds('40,00', '5.122000')).toBe('204.88')
    expect(sellProceeds('0,01', '5.122000')).toBe('0.05')
  })

  it('comprar e vender de volta nunca dá lucro (o spread fica com o banco)', () => {
    for (const v of ['0,01', '1', '37,13', '100', '999,99']) {
      expect(Number(sellProceeds(v, '5.122000'))).toBeLessThan(Number(buyCost(v, '5.278000')))
    }
  })

  it('não inventa valor quando a entrada ou a cotação são inválidas', () => {
    expect(buyCost('abc', '5.278000')).toBeNull()
    expect(buyCost('', '5.278000')).toBeNull()
    expect(buyCost('10', 'x')).toBeNull()
    expect(sellProceeds('0', '5.122000')).toBeNull()
    expect(sellProceeds('10', '')).toBeNull()
  })

  it('cotações grandes e pequenas, sem erro de ponto flutuante', () => {
    expect(buyCost('0,10', '5.000001')).toBe('0.51') // 0,5000001 sobe
    expect(sellProceeds('3', '0.100000')).toBe('0.30')
    expect(buyCost('1000000', '5.278000')).toBe('5278000.00')
  })
})

describe('valores e textos', () => {
  it('a venda não passa da carteira', () => {
    expect(amountError('10,50')).toBeNull()
    expect(amountError('40', '40.00')).toBeNull()
    expect(amountError('40,01', '40.00')).toMatch(/Você tem só/)
    expect(amountError('0')).toMatch(/maior que zero/)
    expect(amountError('1,234')).toMatch(/2 casas/)
  })

  it('mostra moeda, símbolo e cotação em português', () => {
    expect(currencyName('USD')).toBe('Dólar americano')
    expect(currencyName('EUR')).toBe('Euro')
    expect(currencyName('JPY')).toBe('JPY')
    expect(formatForeign('USD', '1234.50')).toMatch(/^US\$ 1\.234,50$/)
    expect(formatForeign('EUR', '10.00')).toMatch(/^€ 10,00$/)
    expect(rateLabel('5.278000')).toBe('5,278')
    expect(rateLabel('5.200000')).toBe('5,20')
    expect(rateLabel('5.734750')).toBe('5,7348')
    expect(rateLabel('5.565250')).toBe('5,5653') // metade sobe (toFixed daria 5,5652)
    expect(rateLabel('5.000049')).toBe('5,00')
    expect(rateLabel('5.000050')).toBe('5,0001')
    expect(rateLabel('abc')).toBe('abc')
    expect(sideLabel('BUY')).toBe('Compra')
    expect(sideLabel('SELL')).toBe('Venda')
  })
})
