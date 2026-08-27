package com.firefly.warehouse;

import com.firefly.warehouse.ApiModels.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class WarehouseService {
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final ProductRepository products;
    private final PartnerRepository partners;
    private final InboundOrderRepository inboundOrders;
    private final InventoryRepository inventory;
    private final MovementRepository movements;
    private final OutboundOrderRepository outboundOrders;
    private final AllocationRepository allocations;

    WarehouseService(WarehouseRepository warehouses, LocationRepository locations, ProductRepository products,
                     PartnerRepository partners, InboundOrderRepository inboundOrders, InventoryRepository inventory,
                     MovementRepository movements, OutboundOrderRepository outboundOrders, AllocationRepository allocations) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.products = products;
        this.partners = partners;
        this.inboundOrders = inboundOrders;
        this.inventory = inventory;
        this.movements = movements;
        this.outboundOrders = outboundOrders;
        this.allocations = allocations;
    }

    PageResult<WarehouseView> warehouses(String keyword, int page, int size) {
        Page<WarehouseEntity> result = warehouses.search(query(keyword), pageable(page, size));
        return pageResult(result, result.getContent().stream().map(this::warehouseView).toList());
    }

    @Transactional
    WarehouseView createWarehouse(WarehouseRequest request) {
        if (warehouses.findByCodeIgnoreCase(request.code().trim()).isPresent()) throw conflict("仓库编码已存在");
        WarehouseEntity entity = new WarehouseEntity();
        entity.code = request.code().trim();
        entity.name = request.name().trim();
        entity.address = trim(request.address());
        entity.manager = trim(request.manager());
        entity.status = normalizedStatus(request.status());
        return warehouseView(saveUnique(() -> warehouses.saveAndFlush(entity)));
    }

    PageResult<LocationView> locations(String keyword, Long warehouseId, int page, int size) {
        Page<LocationEntity> result = locations.search(query(keyword), warehouseId, pageable(page, size));
        Map<Long, WarehouseEntity> warehouseMap = warehouseMap(result.getContent().stream().map(e -> e.warehouseId).toList());
        List<LocationView> views = result.getContent().stream().map(e -> locationView(e, warehouseMap)).toList();
        return pageResult(result, views);
    }

    @Transactional
    LocationView createLocation(LocationRequest request) {
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId());
        if (locations.findByWarehouseIdAndCodeIgnoreCase(request.warehouseId(), request.code().trim()).isPresent()) {
            throw conflict("该仓库的库位编码已存在");
        }
        LocationEntity entity = new LocationEntity();
        entity.warehouseId = request.warehouseId();
        entity.code = request.code().trim();
        entity.name = request.name().trim();
        entity.type = defaultText(request.type(), "STORAGE").toUpperCase(Locale.ROOT);
        entity.capacity = nvl(request.capacity());
        entity.status = normalizedStatus(request.status());
        LocationEntity saved = saveUnique(() -> locations.saveAndFlush(entity));
        return new LocationView(saved.id, saved.warehouseId, warehouse.name, saved.code, saved.name,
                saved.type, saved.capacity, saved.status);
    }

    PageResult<ProductView> products(String keyword, int page, int size) {
        Page<ProductEntity> result = products.search(query(keyword), pageable(page, size));
        return pageResult(result, result.getContent().stream().map(this::productView).toList());
    }

    @Transactional
    ProductView createProduct(ProductRequest request) {
        if (products.findBySkuIgnoreCase(request.sku().trim()).isPresent()) throw conflict("SKU 已存在");
        ProductEntity entity = new ProductEntity();
        entity.sku = request.sku().trim();
        entity.name = request.name().trim();
        entity.category = trim(request.category());
        entity.unit = defaultText(request.unit(), "件");
        entity.barcode = emptyToNull(request.barcode());
        entity.safetyStock = nvl(request.safetyStock());
        entity.status = normalizedStatus(request.status());
        return productView(saveUnique(() -> products.saveAndFlush(entity)));
    }

    PageResult<PartnerView> partners(String keyword, String type, int page, int size) {
        Page<PartnerEntity> result = partners.search(query(keyword), query(type), pageable(page, size));
        return pageResult(result, result.getContent().stream().map(this::partnerView).toList());
    }

    @Transactional
    PartnerView createPartner(PartnerRequest request) {
        if (partners.findByCodeIgnoreCase(request.code().trim()).isPresent()) throw conflict("往来单位编码已存在");
        PartnerEntity entity = new PartnerEntity();
        entity.code = request.code().trim();
        entity.name = request.name().trim();
        entity.type = request.type().toUpperCase(Locale.ROOT);
        entity.contact = trim(request.contact());
        entity.phone = trim(request.phone());
        entity.status = normalizedStatus(request.status());
        return partnerView(saveUnique(() -> partners.saveAndFlush(entity)));
    }

    PageResult<InboundView> inboundOrders(String keyword, String status, int page, int size) {
        Page<InboundOrderEntity> result = inboundOrders.search(query(keyword), query(status), pageable(page, size));
        List<InboundOrderEntity> loaded = loadInboundItems(result.getContent());
        return pageResult(result, inboundViews(loaded));
    }

    @Transactional
    InboundView createInbound(InboundRequest request) {
        requireActivePartner(request.supplierId(), "SUPPLIER", "所选往来单位不是供应商");
        requireActiveWarehouse(request.warehouseId());
        Map<Long, ProductEntity> productMap = activeProducts(request.items().stream().map(LineRequest::productId).toList());

        InboundOrderEntity order = new InboundOrderEntity();
        order.orderNo = number("IN");
        order.supplierId = request.supplierId();
        order.warehouseId = request.warehouseId();
        order.expectedAt = request.expectedAt();
        order.remark = trim(request.remark());
        for (LineRequest line : request.items()) {
            requireFromMap(productMap, line.productId(), "商品不存在");
            InboundItemEntity item = new InboundItemEntity();
            item.order = order;
            item.productId = line.productId();
            item.quantity = line.quantity();
            item.batchNo = batch(line.batchNo());
            item.expiryDate = line.expiryDate();
            order.items.add(item);
            order.totalQuantity += line.quantity();
        }
        return inboundView(inboundOrders.saveAndFlush(order));
    }

    @Transactional
    InboundView receive(Long id, ReceiveRequest request, String operator) {
        InboundOrderEntity order = inboundOrders.lockById(id).orElseThrow(() -> notFound("入库单不存在"));
        if (!Set.of("PENDING", "PARTIALLY_RECEIVED").contains(order.status)) {
            throw conflict("只有待收货或部分收货单据可以继续收货");
        }
        requireActiveWarehouse(order.warehouseId);
        requireActivePartner(order.supplierId, "SUPPLIER", "入库单供应商不可用");

        LocationEntity selected = resolveReceivingLocation(order.warehouseId, request == null ? null : request.locationCode());
        LocationEntity location = lockLocations(List.of(selected.id)).get(selected.id);
        requireActive(location.status, "收货库位");
        Map<Long, ProductEntity> productMap = activeProducts(order.items.stream().map(e -> e.productId).toList());

        Map<Long, Long> requested = request == null || request.items() == null || request.items().isEmpty()
                ? Map.of()
                : request.items().stream().collect(java.util.stream.Collectors.toMap(
                        ReceiveLineRequest::itemId, ReceiveLineRequest::quantity,
                        (left, right) -> { throw bad("收货明细不能重复"); }));
        List<InboundItemEntity> sortedItems = order.items.stream()
                .filter(item -> item.receivedQuantity < item.quantity)
                .filter(item -> requested.isEmpty() || requested.containsKey(item.id))
                .sorted(Comparator.comparing((InboundItemEntity e) -> e.productId).thenComparing(e -> e.batchNo))
                .toList();
        if (!requested.isEmpty() && sortedItems.size() != requested.size()) throw bad("收货明细不属于该入库单");
        long receivedNow = 0;
        for (InboundItemEntity item : sortedItems) {
            requireFromMap(productMap, item.productId, "商品不存在");
            long remaining = item.quantity - item.receivedQuantity;
            long quantity = requested.isEmpty() ? remaining : requested.get(item.id);
            if (quantity <= 0 || quantity > remaining) throw conflict("收货数量必须大于 0 且不能超过待收数量");
            InventoryBalanceEntity balance = lockedBalance(order.warehouseId, location.id, item.productId,
                    item.batchNo, item.expiryDate);
            mergeExpiry(balance, item.expiryDate);
            balance.quantity += quantity;
            inventory.save(balance);
            item.receivedQuantity += quantity;
            receivedNow += quantity;
            movement("INBOUND_RECEIPT", order.warehouseId, location.id, item.productId, item.batchNo,
                    quantity, "INBOUND_ORDER", order.id, "入库单确认收货", operator);
        }
        inventory.flush();
        order.receivedQuantity += receivedNow;
        boolean completed = order.receivedQuantity.equals(order.totalQuantity);
        order.status = completed ? "RECEIVED" : "PARTIALLY_RECEIVED";
        if (completed) order.receivedAt = LocalDateTime.now();
        return inboundView(order);
    }

    PageResult<InventoryView> inventory(String keyword, Long warehouseId, int page, int size) {
        Page<InventoryBalanceEntity> result = inventory.search(query(keyword), warehouseId, pageable(page, size));
        return pageResult(result, inventoryViews(result.getContent()));
    }

    PageResult<InventoryMovementView> movements(String keyword, Long warehouseId, String type, int page, int size) {
        Page<InventoryMovementEntity> result = movements.search(query(keyword), warehouseId, query(type), pageable(page, size));
        return pageResult(result, movementViews(result.getContent()));
    }

    @Transactional
    InventoryView adjust(AdjustmentRequest request, String operator) {
        if (request.quantity() == 0) throw bad("调整数量不能为 0");
        requireActiveWarehouse(request.warehouseId());
        ProductEntity product = requireActiveProduct(request.productId());
        LocationEntity selected = locations.findByWarehouseIdAndCodeIgnoreCase(request.warehouseId(), request.locationCode())
                .orElseThrow(() -> bad("库位不属于所选仓库"));
        LocationEntity location = lockLocations(List.of(selected.id)).get(selected.id);
        requireActive(location.status, "库位");

        String batch = batch(request.batchNo());
        InventoryBalanceEntity balance = lockedBalance(request.warehouseId(), location.id, request.productId(),
                batch, request.expiryDate());
        long adjusted = balance.quantity + request.quantity();
        if (adjusted < balance.allocatedQuantity + balance.lockedQuantity) {
            throw conflict("调整后数量不能小于已分配或锁定数量");
        }
        mergeExpiry(balance, request.expiryDate());
        balance.quantity = adjusted;
        inventory.saveAndFlush(balance);
        movement("ADJUSTMENT", request.warehouseId(), location.id, product.id, batch, request.quantity(),
                "MANUAL", null, defaultText(request.reason(), "手工库存调整"), operator);
        return inventoryView(balance);
    }

    @Transactional
    InventoryView transfer(TransferRequest request, String operator) {
        InventoryBalanceEntity snapshot = inventory.findById(request.inventoryId())
                .orElseThrow(() -> notFound("库存明细不存在"));
        LocationEntity targetSnapshot = locations.findByWarehouseIdAndCodeIgnoreCase(
                        snapshot.warehouseId, request.targetLocationCode())
                .orElseThrow(() -> bad("目标库位不属于同一仓库"));
        if (targetSnapshot.id.equals(snapshot.locationId)) throw bad("目标库位不能与源库位相同");

        Map<Long, LocationEntity> lockedLocations = lockLocations(List.of(snapshot.locationId, targetSnapshot.id));
        LocationEntity sourceLocation = requireFromMap(lockedLocations, snapshot.locationId, "源库位不存在");
        LocationEntity target = requireFromMap(lockedLocations, targetSnapshot.id, "目标库位不存在");
        requireActive(sourceLocation.status, "源库位");
        requireActive(target.status, "目标库位");

        InventoryBalanceEntity source = inventory.lockById(request.inventoryId())
                .orElseThrow(() -> notFound("库存明细不存在"));
        if (!source.locationId.equals(sourceLocation.id)) throw conflict("库存位置已发生变化，请重试");
        requireActiveWarehouse(source.warehouseId);
        requireActiveProduct(source.productId);
        if (!blank(request.sourceLocationCode()) && !sourceLocation.code.equalsIgnoreCase(request.sourceLocationCode())) {
            throw conflict("源库位与库存明细不一致");
        }
        if (source.available() < request.quantity()) throw conflict("源库位可用库存不足");

        InventoryBalanceEntity destination = lockedBalance(source.warehouseId, target.id, source.productId,
                source.batchNo, source.expiryDate);
        mergeExpiry(destination, source.expiryDate);
        source.quantity -= request.quantity();
        destination.quantity += request.quantity();
        inventory.save(source);
        inventory.saveAndFlush(destination);

        String reason = defaultText(request.reason(), "库存移库");
        movement("TRANSFER_OUT", source.warehouseId, source.locationId, source.productId, source.batchNo,
                -request.quantity(), "TRANSFER", source.id, reason, operator);
        movement("TRANSFER_IN", source.warehouseId, target.id, source.productId, source.batchNo,
                request.quantity(), "TRANSFER", source.id, reason, operator);
        return inventoryView(destination);
    }

    @Transactional
    InventoryView stocktake(StocktakeRequest request, String operator) {
        // ponytail: 单库存维度即时盘点；需要多人复核时再升级为盘点单与范围冻结。
        InventoryBalanceEntity balance = inventory.lockById(request.inventoryId())
                .orElseThrow(() -> notFound("库存明细不存在"));
        if (balance.allocatedQuantity > 0 || balance.lockedQuantity > 0) {
            throw conflict("存在已分配或冻结数量，不能盘点该库存");
        }
        long difference = request.actualQuantity() - balance.quantity;
        if (difference == 0) throw bad("实盘数量与账面数量一致，无需调整");
        balance.quantity = request.actualQuantity();
        inventory.saveAndFlush(balance);
        movement("STOCKTAKE", balance.warehouseId, balance.locationId, balance.productId, balance.batchNo,
                difference, "INVENTORY", balance.id, defaultText(request.reason(), "库存盘点差异"), operator);
        return inventoryView(balance);
    }

    PageResult<OutboundView> outboundOrders(String keyword, String status, int page, int size) {
        Page<OutboundOrderEntity> result = outboundOrders.search(query(keyword), query(status), pageable(page, size));
        List<OutboundOrderEntity> loaded = loadOutboundItems(result.getContent());
        return pageResult(result, outboundViews(loaded));
    }

    @Transactional
    OutboundView createOutbound(OutboundRequest request) {
        requireActivePartner(request.customerId(), "CUSTOMER", "所选往来单位不是客户");
        requireActiveWarehouse(request.warehouseId());
        Map<Long, ProductEntity> productMap = activeProducts(request.items().stream().map(LineRequest::productId).toList());

        OutboundOrderEntity order = new OutboundOrderEntity();
        order.orderNo = number("OUT");
        order.customerId = request.customerId();
        order.warehouseId = request.warehouseId();
        order.requiredAt = request.requiredAt();
        order.remark = trim(request.remark());
        for (LineRequest line : request.items()) {
            requireFromMap(productMap, line.productId(), "商品不存在");
            OutboundItemEntity item = new OutboundItemEntity();
            item.order = order;
            item.productId = line.productId();
            item.quantity = line.quantity();
            item.batchNo = batch(line.batchNo());
            order.items.add(item);
            order.totalQuantity += line.quantity();
        }
        return outboundView(outboundOrders.saveAndFlush(order));
    }

    @Transactional
    OutboundView allocate(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!"PENDING".equals(order.status)) throw conflict("只有待分配单据可以分配库存");
        requireActiveWarehouse(order.warehouseId);
        requireActivePartner(order.customerId, "CUSTOMER", "出库单客户不可用");
        Map<Long, ProductEntity> productMap = activeProducts(order.items.stream().map(e -> e.productId).toList());

        List<OutboundItemEntity> sortedItems = order.items.stream()
                .sorted(Comparator.comparing((OutboundItemEntity e) -> e.productId)
                        .thenComparing(e -> batch(e.batchNo))
                        .thenComparing(e -> e.id))
                .toList();
        for (OutboundItemEntity item : sortedItems) {
            ProductEntity product = requireFromMap(productMap, item.productId, "商品不存在");
            long required = item.quantity;
            List<InventoryBalanceEntity> candidates = inventory.lockAvailable(
                    order.warehouseId, item.productId, batch(item.batchNo), LocalDate.now());
            long available = candidates.stream().mapToLong(InventoryBalanceEntity::available).sum();
            if (available < required) {
                throw conflict("SKU " + product.sku + " 可用且未过期库存不足，需要 " + required + "，当前 " + available);
            }
            for (InventoryBalanceEntity balance : candidates) {
                if (required == 0) break;
                long take = Math.min(required, balance.available());
                balance.allocatedQuantity += take;
                inventory.save(balance);
                OutboundAllocationEntity allocation = new OutboundAllocationEntity();
                allocation.orderId = order.id;
                allocation.orderItemId = item.id;
                allocation.inventoryId = balance.id;
                allocation.quantity = take;
                allocations.save(allocation);
                required -= take;
            }
            item.allocatedQuantity = item.quantity;
            order.allocatedQuantity += item.quantity;
        }
        inventory.flush();
        order.status = "ALLOCATED";
        movement("OUTBOUND_ALLOCATED", order.warehouseId, null, sortedItems.get(0).productId, "", 0,
                "OUTBOUND_ORDER", order.id, "出库单库存分配", operator);
        return outboundView(order);
    }

    @Transactional
    OutboundView pick(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!"ALLOCATED".equals(order.status)) throw conflict("只有已分配单据可以确认拣货");
        order.status = "PICKED";
        movement("OUTBOUND_PICKED", order.warehouseId, null, order.items.get(0).productId, "", 0,
                "OUTBOUND_ORDER", order.id, "出库单确认拣货", operator);
        return outboundView(order);
    }

    @Transactional
    OutboundView pack(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!"PICKED".equals(order.status)) throw conflict("只有已拣货单据可以确认复核包装");
        order.status = "PACKED";
        movement("OUTBOUND_PACKED", order.warehouseId, null, order.items.get(0).productId, "", 0,
                "OUTBOUND_ORDER", order.id, "出库单复核包装完成", operator);
        return outboundView(order);
    }

    @Transactional
    OutboundView cancel(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!Set.of("PENDING", "ALLOCATED", "PICKED", "PACKED").contains(order.status)) {
            throw conflict("当前状态不能取消出库单");
        }
        if (!"PENDING".equals(order.status)) {
            Map<Long, OutboundItemEntity> items = index(order.items);
            for (OutboundAllocationEntity allocation : allocations.findByOrderIdOrderByInventoryIdAscIdAsc(order.id)) {
                if (allocation.shipped) throw conflict("已发运的出库单不能取消");
                InventoryBalanceEntity balance = inventory.lockById(allocation.inventoryId)
                        .orElseThrow(() -> conflict("已分配库存不存在"));
                if (balance.allocatedQuantity < allocation.quantity) throw conflict("库存预占数量异常");
                balance.allocatedQuantity -= allocation.quantity;
                requireFromMap(items, allocation.orderItemId, "出库明细不存在").allocatedQuantity -= allocation.quantity;
            }
            order.allocatedQuantity = 0L;
            inventory.flush();
        }
        order.status = "CANCELLED";
        movement("OUTBOUND_CANCELLED", order.warehouseId, null, order.items.get(0).productId, "", 0,
                "OUTBOUND_ORDER", order.id, "取消出库单并释放预占", operator);
        return outboundView(order);
    }

    @Transactional
    OutboundView ship(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!Set.of("ALLOCATED", "PICKED", "PACKED").contains(order.status)) {
            throw conflict("只有已分配、已拣货或已包装单据可以发运");
        }
        requireActiveWarehouse(order.warehouseId);
        requireActivePartner(order.customerId, "CUSTOMER", "出库单客户不可用");
        activeProducts(order.items.stream().map(e -> e.productId).toList());

        Map<Long, OutboundItemEntity> items = index(order.items);
        for (OutboundAllocationEntity allocation : allocations.findByOrderIdOrderByInventoryIdAscIdAsc(order.id)) {
            if (allocation.shipped) continue;
            InventoryBalanceEntity balance = inventory.lockById(allocation.inventoryId)
                    .orElseThrow(() -> conflict("已分配库存不存在"));
            LocationEntity location = locations.findById(balance.locationId)
                    .orElseThrow(() -> conflict("已分配库存库位不存在"));
            requireActive(location.status, "发运库位");
            if (balance.expiryDate != null && balance.expiryDate.isBefore(LocalDate.now())) {
                throw conflict("已分配库存已过期，请重新分配");
            }
            if (balance.quantity < allocation.quantity || balance.allocatedQuantity < allocation.quantity) {
                throw conflict("已分配库存发生异常，请检查库存");
            }
            balance.quantity -= allocation.quantity;
            balance.allocatedQuantity -= allocation.quantity;
            allocation.shipped = true;
            OutboundItemEntity item = requireFromMap(items, allocation.orderItemId, "出库明细不存在");
            item.shippedQuantity += allocation.quantity;
            movement("OUTBOUND_SHIPMENT", balance.warehouseId, balance.locationId, balance.productId,
                    balance.batchNo, -allocation.quantity, "OUTBOUND_ORDER", order.id, "出库单确认发运", operator);
        }
        inventory.flush();
        order.shippedQuantity = order.totalQuantity;
        order.status = "SHIPPED";
        order.shippedAt = LocalDateTime.now();
        return outboundView(order);
    }

    @Transactional
    OutboundView returnShipment(Long id, String operator) {
        OutboundOrderEntity order = outboundOrders.lockById(id).orElseThrow(() -> notFound("出库单不存在"));
        if (!"SHIPPED".equals(order.status)) throw conflict("只有已发运单据可以整单退回");
        Map<Long, OutboundItemEntity> items = index(order.items);
        for (OutboundAllocationEntity allocation : allocations.findByOrderIdOrderByInventoryIdAscIdAsc(order.id)) {
            if (!allocation.shipped) continue;
            InventoryBalanceEntity balance = inventory.lockById(allocation.inventoryId)
                    .orElseThrow(() -> conflict("原发运库存不存在"));
            balance.quantity += allocation.quantity;
            allocation.shipped = false;
            requireFromMap(items, allocation.orderItemId, "出库明细不存在").shippedQuantity -= allocation.quantity;
            movement("OUTBOUND_RETURN", balance.warehouseId, balance.locationId, balance.productId,
                    balance.batchNo, allocation.quantity, "OUTBOUND_ORDER", order.id, "客户退回整单入库", operator);
        }
        inventory.flush();
        order.shippedQuantity = 0L;
        order.status = "RETURNED";
        return outboundView(order);
    }

    DashboardView dashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        List<InventoryMovementEntity> recentWeek = movements.findByCreatedAtGreaterThanEqual(dayStart.minusDays(6));
        long inboundToday = sumMovement(recentWeek, "INBOUND_RECEIPT", dayStart);
        long outboundToday = -sumMovement(recentWeek, "OUTBOUND_SHIPMENT", dayStart);
        List<Long> inboundTrend = new ArrayList<>();
        List<Long> outboundTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDateTime start = today.minusDays(i).atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            inboundTrend.add(sumMovement(recentWeek, "INBOUND_RECEIPT", start, end));
            outboundTrend.add(-sumMovement(recentWeek, "OUTBOUND_SHIPMENT", start, end));
        }
        List<ActivityView> activities = movements.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::activityView).toList();
        return new DashboardView(products.count(), inventory.totalQuantity(), inboundToday, outboundToday,
                inboundOrders.countByStatus("PENDING"),
                outboundOrders.countByStatusNotIn(Set.of("SHIPPED", "CANCELLED")),
                inventory.countLowStock(), inventory.countByExpiryDateLessThanEqual(today.plusDays(30)),
                inboundTrend, outboundTrend, activities);
    }

    private List<InboundOrderEntity> loadInboundItems(List<InboundOrderEntity> page) {
        if (page.isEmpty()) return List.of();
        List<Long> ids = page.stream().map(e -> e.id).toList();
        Map<Long, InboundOrderEntity> loaded = index(inboundOrders.findWithItemsByIds(ids));
        return ids.stream().map(id -> requireFromMap(loaded, id, "入库单不存在")).toList();
    }

    private List<OutboundOrderEntity> loadOutboundItems(List<OutboundOrderEntity> page) {
        if (page.isEmpty()) return List.of();
        List<Long> ids = page.stream().map(e -> e.id).toList();
        Map<Long, OutboundOrderEntity> loaded = index(outboundOrders.findWithItemsByIds(ids));
        return ids.stream().map(id -> requireFromMap(loaded, id, "出库单不存在")).toList();
    }

    private List<InboundView> inboundViews(List<InboundOrderEntity> orders) {
        Map<Long, PartnerEntity> partnerMap = partnerMap(orders.stream().map(e -> e.supplierId).toList());
        Map<Long, WarehouseEntity> warehouseMap = warehouseMap(orders.stream().map(e -> e.warehouseId).toList());
        Map<Long, ProductEntity> productMap = productMap(orders.stream().flatMap(e -> e.items.stream()).map(e -> e.productId).toList());
        return orders.stream().map(e -> inboundView(e, partnerMap, warehouseMap, productMap)).toList();
    }

    private InboundView inboundView(InboundOrderEntity entity) {
        return inboundViews(List.of(entity)).get(0);
    }

    private InboundView inboundView(InboundOrderEntity entity, Map<Long, PartnerEntity> partnerMap,
                                    Map<Long, WarehouseEntity> warehouseMap, Map<Long, ProductEntity> productMap) {
        PartnerEntity supplier = requireFromMap(partnerMap, entity.supplierId, "供应商不存在");
        WarehouseEntity warehouse = requireFromMap(warehouseMap, entity.warehouseId, "仓库不存在");
        List<OrderLineView> items = entity.items.stream().map(item -> {
            ProductEntity product = requireFromMap(productMap, item.productId, "商品不存在");
            return new OrderLineView(item.id, item.productId, product.sku, product.name, item.quantity,
                    0L, item.receivedQuantity, 0L, item.batchNo);
        }).toList();
        return new InboundView(entity.id, entity.orderNo, entity.supplierId, supplier.name,
                entity.warehouseId, warehouse.name, entity.status, entity.expectedAt, entity.totalQuantity,
                entity.receivedQuantity, entity.remark, entity.createdAt, items);
    }

    private List<InventoryView> inventoryViews(List<InventoryBalanceEntity> balances) {
        Map<Long, WarehouseEntity> warehouseMap = warehouseMap(balances.stream().map(e -> e.warehouseId).toList());
        Map<Long, LocationEntity> locationMap = locationMap(balances.stream().map(e -> e.locationId).toList());
        Map<Long, ProductEntity> productMap = productMap(balances.stream().map(e -> e.productId).toList());
        return balances.stream().map(e -> inventoryView(e, warehouseMap, locationMap, productMap)).toList();
    }

    private InventoryView inventoryView(InventoryBalanceEntity entity) {
        return inventoryViews(List.of(entity)).get(0);
    }

    private InventoryView inventoryView(InventoryBalanceEntity entity, Map<Long, WarehouseEntity> warehouseMap,
                                        Map<Long, LocationEntity> locationMap, Map<Long, ProductEntity> productMap) {
        WarehouseEntity warehouse = requireFromMap(warehouseMap, entity.warehouseId, "仓库不存在");
        LocationEntity location = requireFromMap(locationMap, entity.locationId, "库位不存在");
        ProductEntity product = requireFromMap(productMap, entity.productId, "商品不存在");
        return new InventoryView(entity.id, entity.warehouseId, warehouse.name, entity.locationId, location.code,
                entity.productId, product.sku, product.name, entity.batchNo, entity.quantity, entity.available(),
                entity.allocatedQuantity, entity.lockedQuantity, entity.expiryDate, entity.updatedAt);
    }

    private List<InventoryMovementView> movementViews(List<InventoryMovementEntity> entries) {
        Map<Long, WarehouseEntity> warehouseMap = warehouseMap(entries.stream().map(e -> e.warehouseId).toList());
        Map<Long, LocationEntity> locationMap = locationMap(entries.stream().map(e -> e.locationId).filter(java.util.Objects::nonNull).toList());
        Map<Long, ProductEntity> productMap = productMap(entries.stream().map(e -> e.productId).toList());
        return entries.stream().map(e -> {
            WarehouseEntity warehouse = requireFromMap(warehouseMap, e.warehouseId, "仓库不存在");
            ProductEntity product = requireFromMap(productMap, e.productId, "商品不存在");
            LocationEntity location = e.locationId == null ? null : requireFromMap(locationMap, e.locationId, "库位不存在");
            return new InventoryMovementView(e.id, e.movementNo, e.type, e.warehouseId, warehouse.name,
                    e.locationId, location == null ? null : location.code, e.productId, product.sku, product.name,
                    e.batchNo, e.quantity, e.referenceType, e.referenceId, e.reason, e.operatorName, e.createdAt);
        }).toList();
    }

    private List<OutboundView> outboundViews(List<OutboundOrderEntity> orders) {
        Map<Long, PartnerEntity> partnerMap = partnerMap(orders.stream().map(e -> e.customerId).toList());
        Map<Long, WarehouseEntity> warehouseMap = warehouseMap(orders.stream().map(e -> e.warehouseId).toList());
        Map<Long, ProductEntity> productMap = productMap(orders.stream().flatMap(e -> e.items.stream()).map(e -> e.productId).toList());
        return orders.stream().map(e -> outboundView(e, partnerMap, warehouseMap, productMap)).toList();
    }

    private OutboundView outboundView(OutboundOrderEntity entity) {
        return outboundViews(List.of(entity)).get(0);
    }

    private OutboundView outboundView(OutboundOrderEntity entity, Map<Long, PartnerEntity> partnerMap,
                                      Map<Long, WarehouseEntity> warehouseMap, Map<Long, ProductEntity> productMap) {
        PartnerEntity customer = requireFromMap(partnerMap, entity.customerId, "客户不存在");
        WarehouseEntity warehouse = requireFromMap(warehouseMap, entity.warehouseId, "仓库不存在");
        List<OrderLineView> items = entity.items.stream().map(item -> {
            ProductEntity product = requireFromMap(productMap, item.productId, "商品不存在");
            return new OrderLineView(item.id, item.productId, product.sku, product.name, item.quantity,
                    item.allocatedQuantity, 0L, item.shippedQuantity, item.batchNo);
        }).toList();
        return new OutboundView(entity.id, entity.orderNo, entity.customerId, customer.name,
                entity.warehouseId, warehouse.name, entity.status, entity.requiredAt, entity.totalQuantity,
                entity.allocatedQuantity, entity.shippedQuantity, entity.remark, entity.createdAt, items);
    }

    private WarehouseView warehouseView(WarehouseEntity entity) {
        return new WarehouseView(entity.id, entity.code, entity.name, entity.address, entity.manager,
                entity.status, entity.createdAt);
    }

    private LocationView locationView(LocationEntity entity, Map<Long, WarehouseEntity> warehouseMap) {
        WarehouseEntity warehouse = requireFromMap(warehouseMap, entity.warehouseId, "仓库不存在");
        return new LocationView(entity.id, entity.warehouseId, warehouse.name, entity.code, entity.name,
                entity.type, entity.capacity, entity.status);
    }

    private ProductView productView(ProductEntity entity) {
        return new ProductView(entity.id, entity.sku, entity.name, entity.category, entity.unit,
                entity.barcode, entity.safetyStock, entity.status);
    }

    private PartnerView partnerView(PartnerEntity entity) {
        return new PartnerView(entity.id, entity.code, entity.name, entity.type, entity.contact,
                entity.phone, entity.status);
    }

    private LocationEntity resolveReceivingLocation(Long warehouseId, String code) {
        if (!blank(code)) {
            LocationEntity location = locations.findByWarehouseIdAndCodeIgnoreCase(warehouseId, code)
                    .orElseThrow(() -> bad("收货库位不属于入库仓库"));
            requireActive(location.status, "收货库位");
            return location;
        }
        List<LocationEntity> activeLocations = locations.findByWarehouseIdOrderById(warehouseId).stream()
                .filter(e -> active(e.status)).toList();
        return activeLocations.stream().filter(e -> "RECEIVING".equals(e.type)).findFirst()
                .orElseGet(() -> activeLocations.stream().findFirst()
                        .orElseThrow(() -> bad("仓库尚未配置启用库位")));
    }

    private InventoryBalanceEntity lockedBalance(Long warehouseId, Long locationId, Long productId,
                                                   String batchNo, LocalDate expiryDate) {
        return inventory.lockByDimension(warehouseId, locationId, productId, batchNo)
                .orElseGet(() -> newBalance(warehouseId, locationId, productId, batchNo, expiryDate));
    }

    private InventoryBalanceEntity newBalance(Long warehouseId, Long locationId, Long productId,
                                                String batchNo, LocalDate expiryDate) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.warehouseId = warehouseId;
        entity.locationId = locationId;
        entity.productId = productId;
        entity.batchNo = batchNo;
        entity.expiryDate = expiryDate;
        return entity;
    }

    private void mergeExpiry(InventoryBalanceEntity balance, LocalDate incoming) {
        if (incoming == null) return;
        if (balance.expiryDate != null && !balance.expiryDate.equals(incoming)) {
            throw conflict("相同仓库、库位、SKU 和批次的有效期必须一致");
        }
        balance.expiryDate = incoming;
    }

    private Map<Long, LocationEntity> lockLocations(Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        Map<Long, LocationEntity> locked = index(locations.lockByIds(distinct));
        if (locked.size() != distinct.size()) throw notFound("库位不存在");
        return locked;
    }

    private void movement(String type, Long warehouseId, Long locationId, Long productId, String batch,
                          long quantity, String referenceType, Long referenceId, String reason, String operator) {
        InventoryMovementEntity entity = new InventoryMovementEntity();
        entity.movementNo = number("MV");
        entity.type = type;
        entity.warehouseId = warehouseId;
        entity.locationId = locationId;
        entity.productId = productId;
        entity.batchNo = batch(batch);
        entity.quantity = quantity;
        entity.referenceType = referenceType;
        entity.referenceId = referenceId;
        entity.reason = reason;
        entity.operatorName = operator;
        movements.save(entity);
    }

    private WarehouseEntity requireActiveWarehouse(Long id) {
        WarehouseEntity entity = warehouses.findById(id).orElseThrow(() -> notFound("仓库不存在"));
        requireActive(entity.status, "仓库");
        return entity;
    }

    private ProductEntity requireActiveProduct(Long id) {
        ProductEntity entity = products.findById(id).orElseThrow(() -> notFound("商品不存在"));
        requireActive(entity.status, "商品");
        return entity;
    }

    private PartnerEntity requireActivePartner(Long id, String expectedType, String wrongTypeMessage) {
        PartnerEntity entity = partners.findById(id).orElseThrow(() -> notFound("往来单位不存在"));
        requireActive(entity.status, "往来单位");
        if (!expectedType.equals(entity.type)) throw bad(wrongTypeMessage);
        return entity;
    }

    private Map<Long, ProductEntity> activeProducts(Collection<Long> ids) {
        Map<Long, ProductEntity> result = productMap(ids);
        for (Long id : new LinkedHashSet<>(ids)) {
            ProductEntity product = requireFromMap(result, id, "商品不存在");
            requireActive(product.status, "商品 " + product.sku);
        }
        return result;
    }

    private Map<Long, WarehouseEntity> warehouseMap(Collection<Long> ids) {
        return index(warehouses.findAllById(new LinkedHashSet<>(ids)));
    }

    private Map<Long, LocationEntity> locationMap(Collection<Long> ids) {
        return index(locations.findAllById(new LinkedHashSet<>(ids)));
    }

    private Map<Long, ProductEntity> productMap(Collection<Long> ids) {
        return index(products.findAllById(new LinkedHashSet<>(ids)));
    }

    private Map<Long, PartnerEntity> partnerMap(Collection<Long> ids) {
        return index(partners.findAllById(new LinkedHashSet<>(ids)));
    }

    private static <T extends BaseEntity> Map<Long, T> index(Collection<T> entities) {
        Map<Long, T> result = new HashMap<>();
        for (T entity : entities) result.put(entity.id, entity);
        return result;
    }

    private static <T> T requireFromMap(Map<Long, T> map, Long id, String message) {
        T value = map.get(id);
        if (value == null) throw notFound(message);
        return value;
    }

    private static void requireActive(String status, String resource) {
        if (!active(status)) throw conflict(resource + "已停用");
    }

    private static boolean active(String status) {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    private long sumMovement(List<InventoryMovementEntity> list, String type, LocalDateTime since) {
        return list.stream().filter(e -> type.equals(e.type) && !e.createdAt.isBefore(since))
                .mapToLong(e -> e.quantity).sum();
    }

    private long sumMovement(List<InventoryMovementEntity> list, String type, LocalDateTime start, LocalDateTime end) {
        return list.stream().filter(e -> type.equals(e.type) && !e.createdAt.isBefore(start) && e.createdAt.isBefore(end))
                .mapToLong(e -> e.quantity).sum();
    }

    private ActivityView activityView(InventoryMovementEntity entity) {
        String title = switch (entity.type) {
            case "INBOUND_RECEIPT" -> "入库收货完成";
            case "OUTBOUND_SHIPMENT" -> "出库发运完成";
            case "ADJUSTMENT" -> "库存调整";
            case "TRANSFER_IN", "TRANSFER_OUT" -> "库存移库";
            default -> "库存状态更新";
        };
        String type = entity.type.startsWith("INBOUND") ? "inbound"
                : entity.type.startsWith("OUTBOUND") ? "outbound" : "inventory";
        return new ActivityView(entity.id, title, entity.reason, entity.createdAt, type);
    }

    private String number(String prefix) {
        return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private static Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        return PageRequest.of(safePage - 1, safeSize);
    }

    private static <T> PageResult<T> pageResult(Page<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    private static String query(String value) {
        return blank(value) ? "" : value.trim();
    }

    private static String normalizedStatus(String value) {
        return defaultText(value, "ACTIVE").toUpperCase(Locale.ROOT);
    }

    private static String batch(String value) {
        return blank(value) ? "" : value.trim();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String emptyToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private static String defaultText(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private static long nvl(Long value) {
        return value == null ? 0 : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> T saveUnique(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (DataIntegrityViolationException e) {
            throw conflict("编码或唯一字段已存在");
        }
    }

    interface SupplierWithException<T> {
        T get();
    }

    static BusinessException bad(String message) {
        return new BusinessException(400, message);
    }

    static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
