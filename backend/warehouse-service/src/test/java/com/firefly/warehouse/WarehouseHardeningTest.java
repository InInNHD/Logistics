package com.firefly.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.warehouse.ApiModels.AdjustmentRequest;
import com.firefly.warehouse.ApiModels.InventoryView;
import com.firefly.warehouse.ApiModels.InboundRequest;
import com.firefly.warehouse.ApiModels.LineRequest;
import com.firefly.warehouse.ApiModels.OutboundRequest;
import com.firefly.warehouse.ApiModels.OutboundView;
import com.firefly.warehouse.ApiModels.ReceiveRequest;
import com.firefly.warehouse.ApiModels.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WarehouseHardeningTest {
    @Autowired WarehouseService service;
    @Autowired IdempotencyCoordinator idempotency;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WarehouseRepository warehouses;
    @Autowired LocationRepository locations;
    @Autowired ProductRepository products;
    @Autowired PartnerRepository partners;
    @Autowired InventoryRepository inventory;
    @Autowired MovementRepository movements;
    @Autowired AllocationRepository allocations;

    Long warehouseId;
    Long productId;
    Long supplierId;
    Long customerId;
    Long receivingLocationId;
    Long storageLocationId;

    @BeforeEach
    void setup() {
        WarehouseEntity warehouse = warehouses.findByCodeIgnoreCase("WH-HARDEN")
                .orElseGet(() -> {
                    WarehouseEntity entity = new WarehouseEntity();
                    entity.code = "WH-HARDEN";
                    entity.name = "加固测试仓";
                    return warehouses.save(entity);
                });
        warehouse.status = "ACTIVE";
        warehouse = warehouses.save(warehouse);
        warehouseId = warehouse.id;

        LocationEntity receiving = locations.findByWarehouseIdAndCodeIgnoreCase(warehouseId, "REC-HARDEN")
                .orElseGet(() -> {
                    LocationEntity entity = new LocationEntity();
                    entity.warehouseId = warehouseId;
                    entity.code = "REC-HARDEN";
                    entity.name = "加固收货位";
                    entity.type = "RECEIVING";
                    return locations.save(entity);
                });
        receiving.status = "ACTIVE";
        receiving = locations.save(receiving);
        receivingLocationId = receiving.id;

        LocationEntity storage = locations.findByWarehouseIdAndCodeIgnoreCase(warehouseId, "A-HARDEN")
                .orElseGet(() -> {
                    LocationEntity entity = new LocationEntity();
                    entity.warehouseId = warehouseId;
                    entity.code = "A-HARDEN";
                    entity.name = "加固存储位";
                    return locations.save(entity);
                });
        storage.status = "ACTIVE";
        storage = locations.save(storage);
        storageLocationId = storage.id;

        ProductEntity product = products.findBySkuIgnoreCase("SKU-HARDEN")
                .orElseGet(() -> {
                    ProductEntity entity = new ProductEntity();
                    entity.sku = "SKU-HARDEN";
                    entity.name = "加固测试商品";
                    return products.save(entity);
                });
        product.status = "ACTIVE";
        product = products.save(product);
        productId = product.id;

        PartnerEntity supplier = partners.findByCodeIgnoreCase("SUP-HARDEN")
                .orElseGet(() -> {
                    PartnerEntity entity = new PartnerEntity();
                    entity.code = "SUP-HARDEN";
                    entity.name = "加固测试供应商";
                    entity.type = "SUPPLIER";
                    return partners.save(entity);
                });
        supplier.status = "ACTIVE";
        supplier = partners.save(supplier);
        supplierId = supplier.id;

        PartnerEntity customer = partners.findByCodeIgnoreCase("CUS-HARDEN")
                .orElseGet(() -> {
                    PartnerEntity entity = new PartnerEntity();
                    entity.code = "CUS-HARDEN";
                    entity.name = "加固测试客户";
                    entity.type = "CUSTOMER";
                    return partners.save(entity);
                });
        customer.status = "ACTIVE";
        customer = partners.save(customer);
        customerId = customer.id;
    }

    @Test
    void appliesConcurrentAdjustmentsToOneNewInventoryDimensionWithoutLostUpdates() throws Exception {
        String batch = unique("CONCURRENT");
        int workers = 6;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<InventoryView>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.adjust(new AdjustmentRequest(warehouseId, "A-HARDEN", productId,
                            1L, batch, null, "并发调整"), "concurrency-test");
                }));
            }
            ready.await();
            start.countDown();
            for (Future<InventoryView> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        var result = service.inventory(batch, warehouseId, 1, 20);
        assertEquals(1, result.total());
        assertEquals(workers, result.records().get(0).quantity());
    }

    @Test
    void replaysConcurrentIdempotentAdjustmentAndPostsOnlyOneMovement() throws Exception {
        String batch = unique("IDEMPOTENT");
        String key = unique("adjust-key");
        AdjustmentRequest request = new AdjustmentRequest(warehouseId, "A-HARDEN", productId,
                3L, batch, null, "幂等调整");
        long movementCountBefore = movements.count();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return idempotency.execute(key, "ADJUST_INVENTORY", request, InventoryView.class,
                        () -> service.adjust(request, "idempotency-test"));
            });
            var second = executor.submit(() -> {
                start.await();
                return idempotency.execute(key, "ADJUST_INVENTORY", request, InventoryView.class,
                        () -> service.adjust(request, "idempotency-test"));
            });
            start.countDown();
            assertEquals(first.get().id(), second.get().id());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(3L, service.inventory(batch, warehouseId, 1, 20).records().get(0).quantity());
        assertEquals(movementCountBefore + 1, movements.count());
    }

    @Test
    void exposesIdempotencyHeaderAndRejectsKeyReuseWithDifferentPayload() throws Exception {
        String batch = unique("HTTP-IDEMPOTENT");
        String key = unique("http-key");
        String body = adjustmentJson(batch, 4);

        String first = mvc.perform(post("/api/inventory/adjustments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/inventory/adjustments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstJson = objectMapper.readTree(first);
        JsonNode secondJson = objectMapper.readTree(second);
        assertEquals(firstJson.at("/data/id").asLong(), secondJson.at("/data/id").asLong());
        assertEquals(4L, service.inventory(batch, warehouseId, 1, 20).records().get(0).quantity());

        mvc.perform(post("/api/inventory/adjustments")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentJson(batch, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同一 Idempotency-Key 不能用于不同请求"));
    }

    @Test
    void makesTheCompleteInboundTransferOutboundCommandChainIdempotent() throws Exception {
        String batch = unique("CHAIN");
        var inboundRequest = new InboundRequest(supplierId, warehouseId, null, "幂等链路入库",
                List.of(new LineRequest(productId, 10L, batch, null)));
        String inboundBody = objectMapper.writeValueAsString(inboundRequest);
        String createInboundKey = unique("create-in");
        String inboundFirst = mvc.perform(post("/api/inbound-orders")
                        .header("Idempotency-Key", createInboundKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inboundBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String inboundReplay = mvc.perform(post("/api/inbound-orders")
                        .header("Idempotency-Key", createInboundKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inboundBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long inboundId = objectMapper.readTree(inboundFirst).at("/data/id").asLong();
        assertEquals(inboundId, objectMapper.readTree(inboundReplay).at("/data/id").asLong());

        String receiveBody = objectMapper.writeValueAsString(new ReceiveRequest("REC-HARDEN"));
        String receiveKey = unique("receive");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/inbound-orders/{id}/receive", inboundId)
                            .header("Idempotency-Key", receiveKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(receiveBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RECEIVED"));
        }

        InventoryView received = service.inventory(batch, warehouseId, 1, 20).records().get(0);
        String transferBody = objectMapper.writeValueAsString(new TransferRequest(received.id(), "REC-HARDEN",
                "A-HARDEN", 4L, "幂等移库"));
        String transferKey = unique("transfer");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/inventory/transfers")
                            .header("Idempotency-Key", transferKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantity").value(4));
        }

        var outboundRequest = new OutboundRequest(customerId, warehouseId, null, "幂等链路出库",
                List.of(new LineRequest(productId, 4L, batch, null)));
        String outboundBody = objectMapper.writeValueAsString(outboundRequest);
        String createOutboundKey = unique("create-out");
        String outboundFirst = mvc.perform(post("/api/outbound-orders")
                        .header("Idempotency-Key", createOutboundKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outboundBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String outboundReplay = mvc.perform(post("/api/outbound-orders")
                        .header("Idempotency-Key", createOutboundKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outboundBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long outboundId = objectMapper.readTree(outboundFirst).at("/data/id").asLong();
        assertEquals(outboundId, objectMapper.readTree(outboundReplay).at("/data/id").asLong());

        String allocateKey = unique("allocate");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/outbound-orders/{id}/allocate", outboundId)
                            .header("Idempotency-Key", allocateKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ALLOCATED"));
        }
        String shipKey = unique("ship");
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/outbound-orders/{id}/ship", outboundId)
                            .header("Idempotency-Key", shipKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SHIPPED"));
        }

        long remaining = service.inventory(batch, warehouseId, 1, 20).records().stream()
                .mapToLong(InventoryView::quantity).sum();
        assertEquals(6L, remaining);
    }

    @Test
    void excludesExpiredInventoryAndKeepsFefoOrdering() {
        String batch = unique("EXPIRY");
        service.adjust(new AdjustmentRequest(warehouseId, "REC-HARDEN", productId, 10L, batch,
                LocalDate.now().minusDays(1), "过期库存"), "expiry-test");
        service.adjust(new AdjustmentRequest(warehouseId, "A-HARDEN", productId, 5L, batch,
                LocalDate.now().plusDays(10), "有效库存"), "expiry-test");

        OutboundView insufficient = service.createOutbound(new OutboundRequest(customerId, warehouseId, null,
                "过期库存不得分配", List.of(new LineRequest(productId, 6L, batch, null))));
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.allocate(insufficient.id(), "expiry-test"));
        assertTrue(failure.getMessage().contains("未过期库存不足"));

        OutboundView outbound = service.createOutbound(new OutboundRequest(customerId, warehouseId, null,
                "仅分配有效库存", List.of(new LineRequest(productId, 5L, batch, null))));
        service.allocate(outbound.id(), "expiry-test");
        var assigned = allocations.findByOrderIdOrderByInventoryIdAscIdAsc(outbound.id());
        assertFalse(assigned.isEmpty());
        for (OutboundAllocationEntity allocation : assigned) {
            InventoryBalanceEntity balance = inventory.findById(allocation.inventoryId).orElseThrow();
            assertTrue(balance.expiryDate == null || !balance.expiryDate.isBefore(LocalDate.now()));
        }
    }

    @Test
    void allocatesOrdersWithOppositeBatchInputOrderUsingOneCanonicalLockOrder() throws Exception {
        String batchX = unique("LOCK-X");
        String batchY = unique("LOCK-Y");
        service.adjust(new AdjustmentRequest(warehouseId, "A-HARDEN", productId, 4L, batchX,
                LocalDate.now().plusDays(20), "并发分配批次 X"), "allocation-lock-test");
        service.adjust(new AdjustmentRequest(warehouseId, "A-HARDEN", productId, 4L, batchY,
                LocalDate.now().plusDays(20), "并发分配批次 Y"), "allocation-lock-test");

        OutboundView firstOrder = service.createOutbound(new OutboundRequest(customerId, warehouseId, null,
                "X 后 Y", List.of(
                new LineRequest(productId, 2L, batchX, null),
                new LineRequest(productId, 2L, batchY, null))));
        OutboundView secondOrder = service.createOutbound(new OutboundRequest(customerId, warehouseId, null,
                "Y 后 X", List.of(
                new LineRequest(productId, 2L, batchY, null),
                new LineRequest(productId, 2L, batchX, null))));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OutboundView> first = executor.submit(() -> {
                start.await();
                return service.allocate(firstOrder.id(), "allocation-lock-test");
            });
            Future<OutboundView> second = executor.submit(() -> {
                start.await();
                return service.allocate(secondOrder.id(), "allocation-lock-test");
            });
            start.countDown();
            assertEquals("ALLOCATED", first.get(10, TimeUnit.SECONDS).status());
            assertEquals("ALLOCATED", second.get(10, TimeUnit.SECONDS).status());
        } finally {
            executor.shutdownNow();
        }

        InventoryView stockX = service.inventory(batchX, warehouseId, 1, 20).records().get(0);
        InventoryView stockY = service.inventory(batchY, warehouseId, 1, 20).records().get(0);
        assertEquals(4L, stockX.allocatedQuantity());
        assertEquals(0L, stockX.availableQuantity());
        assertEquals(4L, stockY.allocatedQuantity());
        assertEquals(0L, stockY.availableQuantity());
    }

    @Test
    void pagesInDatabaseAndExposesMovementLedgerEndpoint() throws Exception {
        String prefix = unique("PAGE");
        for (int i = 0; i < 3; i++) {
            ProductEntity entity = new ProductEntity();
            entity.sku = prefix + '-' + i;
            entity.name = "分页商品 " + i;
            products.save(entity);
        }
        var firstPage = service.products(prefix, 1, 2);
        var secondPage = service.products(prefix, 2, 2);
        assertEquals(3, firstPage.total());
        assertEquals(2, firstPage.records().size());
        assertEquals(1, secondPage.records().size());

        String batch = unique("MOVEMENT");
        service.adjust(new AdjustmentRequest(warehouseId, "A-HARDEN", productId, 1L, batch,
                null, "流水分页测试"), "ledger-test");
        mvc.perform(get("/api/inventory/movements")
                        .param("keyword", batch)
                        .param("warehouseId", warehouseId.toString())
                        .param("type", "ADJUSTMENT")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].batchNo").value(batch))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("ledger-test"));
    }

    private String adjustmentJson(String batch, long quantity) throws Exception {
        return objectMapper.writeValueAsString(new AdjustmentRequest(warehouseId, "A-HARDEN", productId,
                quantity, batch, null, "HTTP 幂等测试"));
    }

    private static String unique(String prefix) {
        return prefix + '-' + UUID.randomUUID().toString().substring(0, 8);
    }
}
