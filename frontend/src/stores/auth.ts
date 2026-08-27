import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { UserProfile } from '@/types'

function readSavedUser(): UserProfile | null {
  const savedUser = localStorage.getItem('firefly_user') || sessionStorage.getItem('firefly_user')
  if (!savedUser) return null
  try {
    return JSON.parse(savedUser) as UserProfile
  } catch {
    localStorage.removeItem('firefly_user')
    sessionStorage.removeItem('firefly_user')
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('firefly_token') || sessionStorage.getItem('firefly_token') || '')
  const user = ref<UserProfile | null>(readSavedUser())
  const isAuthenticated = computed(() => Boolean(token.value))

  async function signIn(username: string, password: string, remember = false) {
    const result = await authApi.login({ username, password })
    const nextToken = result.token || result.accessToken
    if (!nextToken) throw new Error('登录响应缺少访问令牌')

    token.value = nextToken
    const responseUser = result.user
    user.value = responseUser ? {
      ...responseUser,
      roles: responseUser.roles || (responseUser.role ? [responseUser.role] : []),
    } : {
      id: 0,
      username: result.username || username,
      displayName: result.displayName || username,
      roles: result.roles || (result.role ? [result.role] : []),
    }
    localStorage.removeItem('firefly_token')
    localStorage.removeItem('firefly_user')
    sessionStorage.removeItem('firefly_token')
    sessionStorage.removeItem('firefly_user')
    const storage = remember ? localStorage : sessionStorage
    storage.setItem('firefly_token', token.value)
    storage.setItem('firefly_user', JSON.stringify(user.value))
  }

  async function refreshProfile() {
    if (!token.value) return
    const profile = await authApi.getProfile()
    user.value = { ...profile, roles: profile.roles || (profile.role ? [profile.role] : []) }
    const storage = localStorage.getItem('firefly_token') ? localStorage : sessionStorage
    storage.setItem('firefly_user', JSON.stringify(user.value))
  }

  async function signOut() {
    try {
      if (token.value) await authApi.logout()
    } finally {
      token.value = ''
      user.value = null
      localStorage.removeItem('firefly_token')
      localStorage.removeItem('firefly_user')
      sessionStorage.removeItem('firefly_token')
      sessionStorage.removeItem('firefly_user')
    }
  }

  return { token, user, isAuthenticated, signIn, refreshProfile, signOut }
})
