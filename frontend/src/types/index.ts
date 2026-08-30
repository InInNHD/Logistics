export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page: number
  size: number
  keyword?: string
}

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'PENDING'

export type AppRole =
  | 'ADMIN'
  | 'WAREHOUSE_ADMIN'
  | 'WAREHOUSE_MANAGER'
  | 'RECEIVER'
  | 'PICKER'

export interface UserProfile {
  id: number
  username: string
  displayName: string
  roles: string[]
  role?: string
  avatar?: string
}

export interface AuthUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  status: UserStatus | string
  createdAt?: string
  updatedAt?: string
}

export interface AuthRole {
  code: string
  name: string
  scope: string
}

export interface CreateUserRequest {
  username: string
  displayName: string
  password: string
  roles: string[]
  status: UserStatus
}

export interface UpdateUserRequest {
  displayName?: string
  password?: string
  roles?: string[]
  status?: UserStatus
}

export interface LoginResult {
  token?: string
  accessToken?: string
  expiresIn?: number
  user?: UserProfile
  username?: string
  displayName?: string
  roles?: string[]
  role?: string
}

export interface Warehouse {
  id: number
  code: string
  name: string
  address?: string
  manager?: string
  status: string
  createdAt?: string
}

export interface CreateWarehouseRequest {
  code: string
  name: string
  address?: string
  manager?: string
  status: string
}

export interface Location {
  id: number
  warehouseId: number
  warehouseName?: string
  code: string
  name: string
  type: string
  capacity?: number
  status: string
}

export interface CreateLocationRequest {
  warehouseId: number
  code: string
  name: string
  type: string
  capacity?: number
  status: string
}

export interface Product {
  id: number
  sku: string
  name: string
  category?: string
  unit: string
  barcode?: string
  safetyStock?: number
  status: string
}

export interface CreateProductRequest {
  sku: string
  name: string
  category?: string
  unit: string
  barcode?: string
  safetyStock?: number
  status: string
}

export interface Partner {
  id: number
  code: string
  name: string
  type: 'SUPPLIER' | 'CUSTOMER' | string
  contact?: string
  phone?: string
  status: string
}

export interface CreatePartnerRequest {
  code: string
  name: string
  type: 'SUPPLIER' | 'CUSTOMER'
  contact?: string
  phone?: string
  status: string
}

export interface OrderLineDraft {
  productId?: number
  sku: string
  productName?: string
  quantity: number
  batchNo?: string
  expiryDate?: string
}

export interface OrderLineRequest {
  productId: number
  quantity: number
  batchNo?: string
  expiryDate?: string
}

export interface OrderLine {
  id: number
  productId: number
  sku: string
  productName: string
  quantity: number
  allocatedQuantity: number
  receivedQuantity: number
  shippedQuantity: number
  batchNo?: string
}

export interface InboundOrder {
  id: number
  orderNo: string
  supplierId?: number
  supplierName?: string
  warehouseId?: number
  warehouseName?: string
  status: string
  expectedAt?: string
  totalQuantity: number
  receivedQuantity: number
  remark?: string
  createdAt?: string
  items?: OrderLine[]
}

export interface CreateInboundOrderRequest {
  supplierId: number
  warehouseId: number
  expectedAt?: string
  remark?: string
  items: OrderLineRequest[]
}

export interface ReceiveInboundRequest {
  locationCode?: string
  items?: Array<{ itemId: number; quantity: number }>
}

export interface InventoryItem {
  id: number
  warehouseId?: number
  warehouseName: string
  locationId?: number
  locationCode: string
  productId?: number
  sku: string
  productName: string
  batchNo?: string
  quantity: number
  availableQuantity: number
  allocatedQuantity: number
  lockedQuantity: number
  expiryDate?: string
  updatedAt?: string
}

export interface InventoryAdjustmentRequest {
  warehouseId: number
  locationCode: string
  productId: number
  quantity: number
  batchNo?: string
  expiryDate?: string
  reason?: string
}

export interface InventoryTransferRequest {
  inventoryId: number
  sourceLocationCode: string
  targetLocationCode: string
  quantity: number
  reason?: string
}

export interface InventoryStocktakeRequest {
  inventoryId: number
  actualQuantity: number
  reason?: string
}

export interface InventoryMovement {
  id: number
  movementNo: string
  type: string
  warehouseId: number
  warehouseName: string
  locationId?: number
  locationCode?: string
  productId: number
  sku: string
  productName: string
  batchNo?: string
  quantity: number
  referenceType?: string
  referenceId?: number
  reason?: string
  operatorName?: string
  createdAt?: string
}

export interface OutboundOrder {
  id: number
  orderNo: string
  customerId?: number
  customerName?: string
  warehouseId?: number
  warehouseName?: string
  status: string
  requiredAt?: string
  totalQuantity: number
  allocatedQuantity: number
  shippedQuantity: number
  remark?: string
  createdAt?: string
  items?: OrderLine[]
}

export interface CreateOutboundOrderRequest {
  customerId: number
  warehouseId: number
  requiredAt?: string
  remark?: string
  items: OrderLineRequest[]
}

export interface DashboardSummary {
  skuCount: number
  inventoryQuantity: number
  todayInboundQuantity: number
  todayOutboundQuantity: number
  pendingInboundCount: number
  pendingOutboundCount: number
  lowStockCount: number
  expiringCount: number
  inboundTrend?: number[]
  outboundTrend?: number[]
  recentActivities?: ActivityItem[]
}

export interface ActivityItem {
  id: string | number
  title: string
  description?: string
  time: string
  type?: 'inbound' | 'outbound' | 'inventory' | 'system'
}

export interface CarrierAccount {
  id: number
  warehouseId: number
  warehouseName: string
  carrierCode: string
  accountName: string
  apiBaseUrl: string
  credentialHint: string
  status: string
  connectionStatus: string
  tokenExpiresAt?: string
  lastSyncedAt?: string
  syncEnabled: boolean
  syncIntervalMinutes: number
  nextSyncAt?: string
  consecutiveFailures: number
  circuitOpenedUntil?: string
  updatedAt?: string
}

export interface CreateCarrierAccountRequest {
  warehouseId: number
  carrierCode: string
  accountName: string
  apiBaseUrl: string
  credential: string
  status: string
  tokenExpiresAt?: string
  syncEnabled: boolean
  syncIntervalMinutes: number
}

export interface UpdateCarrierAccountRequest {
  accountName: string
  apiBaseUrl: string
  credential?: string
  status: string
  tokenExpiresAt?: string
  syncEnabled: boolean
  syncIntervalMinutes: number
}

export interface CarrierOrder {
  id: number
  accountId: number
  accountName: string
  carrierCode: string
  externalOrderNo: string
  trackingNo?: string
  recipientRegion?: string
  status: string
  amount: number
  placedAt?: string
  syncedAt: string
}

export interface CarrierSyncLog {
  id: number
  accountId: number
  accountName: string
  carrierCode: string
  triggerType: string
  status: string
  fetchedCount: number
  message?: string
  startedAt: string
  finishedAt: string
}

export interface CarrierSyncResult {
  account: CarrierAccount
  fetchedCount: number
}
