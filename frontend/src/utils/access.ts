import type { RouteLocationNormalizedGeneric } from 'vue-router'

const ADMIN_ROLES = new Set(['ADMIN', 'WAREHOUSE_ADMIN'])

export function normalizeRole(role: string): string {
  return role.trim().toUpperCase().replace(/^ROLE_/, '')
}

export function hasAnyRole(userRoles: readonly string[], requiredRoles?: readonly string[]): boolean {
  if (!requiredRoles?.length) return true
  const roles = new Set(userRoles.map(normalizeRole))
  if ([...ADMIN_ROLES].some((role) => roles.has(role))) return true
  return requiredRoles.map(normalizeRole).some((role) => roles.has(role))
}

export function readStoredRoles(storage: Pick<Storage, 'getItem'> = localStorage): string[] {
  const raw = storage.getItem('firefly_user')
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as { roles?: unknown; role?: unknown }
    if (Array.isArray(parsed.roles)) return parsed.roles.filter((role): role is string => typeof role === 'string')
    return typeof parsed.role === 'string' ? [parsed.role] : []
  } catch {
    return []
  }
}

export function canAccessRoute(route: Pick<RouteLocationNormalizedGeneric, 'meta'>, roles: readonly string[]): boolean {
  return hasAnyRole(roles, route.meta.roles as string[] | undefined)
}
