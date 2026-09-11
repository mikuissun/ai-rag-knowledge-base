import { computed, reactive } from 'vue'
import type { UserProfile } from '../types/api'

const TOKEN_KEY = 'knowledge-base-token'
const USER_KEY = 'knowledge-base-user'

function readUser(): UserProfile | null {
  const value = localStorage.getItem(USER_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as UserProfile
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  user: readUser(),
})

export function useAuthStore() {
  const isAuthenticated = computed(() => Boolean(state.token))

  function setSession(token: string, user: UserProfile) {
    state.token = token
    state.user = user
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  }

  function clearSession() {
    state.token = ''
    state.user = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { state, isAuthenticated, setSession, clearSession }
}

export function getAccessToken() {
  return state.token || localStorage.getItem(TOKEN_KEY) || ''
}
