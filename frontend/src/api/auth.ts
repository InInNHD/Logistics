import http, { unwrap } from './http'
import type {
  AuthRole,
  AuthUser,
  CreateUserRequest,
  LoginResult,
  PageQuery,
  PageResult,
  UpdateUserRequest,
  UserProfile,
} from '@/types'

export async function login(payload: { username: string; password: string }) {
  return unwrap<LoginResult>(await http.post('/auth/login', payload))
}

export async function getProfile() {
  return unwrap<UserProfile>(await http.get('/auth/me'))
}

export async function getUsers(params: PageQuery & { status?: string; role?: string }) {
  return unwrap<PageResult<AuthUser>>(await http.get('/auth/users', { params }))
}

export async function createUser(payload: CreateUserRequest) {
  return unwrap<AuthUser>(await http.post('/auth/users', payload))
}

export async function updateUser(id: number, payload: UpdateUserRequest) {
  return unwrap<AuthUser>(await http.patch(`/auth/users/${id}`, payload))
}

export async function getRoles() {
  return unwrap<AuthRole[]>(await http.get('/auth/roles'))
}
