import http, { unwrap } from './http'
import type {
  CreateInboundOrderRequest,
  CreateLocationRequest,
  CreateOutboundOrderRequest,
  CreatePartnerRequest,
  CreateProductRequest,
  CreateWarehouseRequest,
  DashboardSummary,
  InboundOrder,
  InventoryItem,
  InventoryMovement,
  Location,
  OutboundOrder,
  PageResult,
  Partner,
  Product,
  Warehouse,
  InventoryAdjustmentRequest,
  InventoryTransferRequest,
  InventoryStocktakeRequest,
  ReceiveInboundRequest,
} from '@/types'

type Query = Record<string, string | number | boolean | undefined>

function idempotencyConfig(idempotencyKey?: string) {
  return idempotencyKey ? { headers: { 'Idempotency-Key': idempotencyKey } } : undefined
}

function normalizePage<T>(value: PageResult<T> | T[]): PageResult<T> {
  if (Array.isArray(value)) return { records: value, total: value.length, page: 1, size: Math.max(value.length, 20) }
  return value
}

async function getPage<T>(url: string, params?: Query) {
  return normalizePage(unwrap<PageResult<T> | T[]>(await http.get(url, { params })))
}

export const warehouseApi = {
  dashboard: async () => unwrap<DashboardSummary>(await http.get('/dashboard/summary')),

  warehouses: (params?: Query) => getPage<Warehouse>('/warehouses', params),
  createWarehouse: async (payload: CreateWarehouseRequest) =>
    unwrap<Warehouse>(await http.post('/warehouses', payload)),

  locations: (params?: Query) => getPage<Location>('/locations', params),
  createLocation: async (payload: CreateLocationRequest) =>
    unwrap<Location>(await http.post('/locations', payload)),

  products: (params?: Query) => getPage<Product>('/products', params),
  createProduct: async (payload: CreateProductRequest) =>
    unwrap<Product>(await http.post('/products', payload)),

  partners: (params?: Query) => getPage<Partner>('/partners', params),
  createPartner: async (payload: CreatePartnerRequest) =>
    unwrap<Partner>(await http.post('/partners', payload)),

  inboundOrders: (params?: Query) => getPage<InboundOrder>('/inbound-orders', params),
  createInboundOrder: async (payload: CreateInboundOrderRequest, idempotencyKey?: string) =>
    unwrap<InboundOrder>(await http.post('/inbound-orders', payload, idempotencyConfig(idempotencyKey))),
  receiveInbound: async (id: number, payload: ReceiveInboundRequest = {}, idempotencyKey?: string) =>
    unwrap<InboundOrder>(await http.post(`/inbound-orders/${id}/receive`, payload, idempotencyConfig(idempotencyKey))),

  inventory: (params?: Query) => getPage<InventoryItem>('/inventory', params),
  inventoryMovements: (params?: Query) => getPage<InventoryMovement>('/inventory/movements', params),
  adjustInventory: async (payload: InventoryAdjustmentRequest, idempotencyKey?: string) =>
    unwrap<InventoryItem>(await http.post('/inventory/adjustments', payload, idempotencyConfig(idempotencyKey))),
  transferInventory: async (payload: InventoryTransferRequest, idempotencyKey?: string) =>
    unwrap<InventoryItem>(await http.post('/inventory/transfers', payload, idempotencyConfig(idempotencyKey))),
  stocktakeInventory: async (payload: InventoryStocktakeRequest, idempotencyKey?: string) =>
    unwrap<InventoryItem>(await http.post('/inventory/stocktakes', payload, idempotencyConfig(idempotencyKey))),

  outboundOrders: (params?: Query) => getPage<OutboundOrder>('/outbound-orders', params),
  createOutboundOrder: async (payload: CreateOutboundOrderRequest, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post('/outbound-orders', payload, idempotencyConfig(idempotencyKey))),
  allocateOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/allocate`, {}, idempotencyConfig(idempotencyKey))),
  pickOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/pick`, {}, idempotencyConfig(idempotencyKey))),
  packOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/pack`, {}, idempotencyConfig(idempotencyKey))),
  shipOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/ship`, {}, idempotencyConfig(idempotencyKey))),
  cancelOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/cancel`, {}, idempotencyConfig(idempotencyKey))),
  returnOutbound: async (id: number, idempotencyKey?: string) =>
    unwrap<OutboundOrder>(await http.post(`/outbound-orders/${id}/return`, {}, idempotencyConfig(idempotencyKey))),
}
