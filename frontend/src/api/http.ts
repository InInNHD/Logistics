import axios, { type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('firefly_token') || sessionStorage.getItem('firefly_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['X-Request-Id'] = crypto.randomUUID()
  const method = config.method?.toUpperCase()
  if (method && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = crypto.randomUUID()
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0 && body.code !== 200) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return response
  },
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('firefly_token')
      localStorage.removeItem('firefly_user')
      sessionStorage.removeItem('firefly_token')
      sessionStorage.removeItem('firefly_user')
      if (router.currentRoute.value.path !== '/login') {
        await router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    } else if (!error.config?.silent) {
      ElMessage.error(error.response?.data?.message || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  },
)

export function unwrap<T>(response: AxiosResponse<ApiResponse<T> | T>): T {
  const body = response.data
  if (body && typeof body === 'object' && 'data' in body && 'code' in body) {
    return (body as ApiResponse<T>).data
  }
  return body as T
}

export default http
