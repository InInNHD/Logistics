import { describe, expect, it } from 'vitest'
import { hasAnyRole, normalizeRole, readStoredRoles } from './access'

describe('role access', () => {
  it('normalizes Spring Security role prefixes', () => {
    expect(normalizeRole(' role_receiver ')).toBe('RECEIVER')
  })

  it('grants administrators every restricted route', () => {
    expect(hasAnyRole(['WAREHOUSE_ADMIN'], ['PICKER'])).toBe(true)
    expect(hasAnyRole(['ADMIN'], ['WAREHOUSE_MANAGER'])).toBe(true)
  })

  it('requires an exact operational role for non-admin users', () => {
    expect(hasAnyRole(['ROLE_RECEIVER'], ['RECEIVER'])).toBe(true)
    expect(hasAnyRole(['RECEIVER'], ['PICKER'])).toBe(false)
  })

  it('reads persisted roles without trusting malformed JSON', () => {
    expect(readStoredRoles({ getItem: () => '{invalid' })).toEqual([])
    expect(readStoredRoles({ getItem: () => JSON.stringify({ role: 'PICKER' }) })).toEqual(['PICKER'])
    expect(readStoredRoles(
      { getItem: () => null },
      { getItem: () => JSON.stringify({ roles: ['RECEIVER'] }) },
    )).toEqual(['RECEIVER'])
  })
})
