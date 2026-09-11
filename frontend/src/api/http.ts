import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import router from '../router'
import { getAccessToken, useAuthStore } from '../stores/auth'
import type { ApiResponse } from '../types/api'

const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/+$/, '')

export const apiClient = axios.create({
  baseURL: configuredBaseUrl || undefined,
  timeout: 30000,
})

export function apiUrl(path: string) {
  return `${configuredBaseUrl}${path}`
}

export function handleUnauthorized() {
  useAuthStore().clearSession()
  window.dispatchEvent(new Event('knowledge-base:unauthorized'))
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    if (response.data?.code === 401) handleUnauthorized()
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401 || error.response?.data?.code === 401) handleUnauthorized()
    return Promise.reject(error)
  },
)

export class ApiRequestError extends Error {
  readonly code?: number

  constructor(message: string, code?: number) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
  }
}

function getAxiosMessage(error: unknown) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error.response?.data?.message || error.message || '请求失败，请稍后重试'
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

export async function request<T>(config: AxiosRequestConfig) {
  try {
    const response = await apiClient.request<ApiResponse<T>>(config)
    const payload = response.data
    if (payload.code !== 200) throw new ApiRequestError(payload.message || '请求失败', payload.code)
    return payload.data
  } catch (error) {
    if (error instanceof ApiRequestError) throw error
    throw new ApiRequestError(getAxiosMessage(error), axios.isAxiosError(error) ? error.response?.status : undefined)
  }
}
