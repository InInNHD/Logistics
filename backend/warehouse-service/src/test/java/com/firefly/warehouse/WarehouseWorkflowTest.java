package com.firefly.warehouse;

import com.firefly.warehouse.ApiModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WarehouseWorkflowTest {
    @Autowired WarehouseService service;
    @Autowired MockMvc mvc;
    @Autowired WarehouseRepository warehouseRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PartnerRepository partnerRepository;
    Long warehouseId; Long productId; Long supplierId; Long customerId;

    @BeforeEach void setup() {
        if (warehouseRepository.count() > 0) {
            warehouseId=warehouseRepository.findAll().get(0).id; productId=productRepository.findAll().get(0).id;
            supplierId=partnerRepository.findAll().stream().filter(p->"SUPPLIER".equals(p.type)).findFirst().orElseThrow().id;
            customerId=partnerRepository.findAll().stream().filter(p->"CUSTOMER".equals(p.type)).findFirst().orElseThrow().id; return;
        }
        WarehouseEntity warehouse=new WarehouseEntity(); warehouse.code="WH-TEST";warehouse.name="测试仓";warehouse=warehouseRepository.save(warehouse);warehouseId=warehouse.id;
        LocationEntity receiving=new LocationEntity();receiving.warehouseId=warehouseId;receiving.code="REC-01";receiving.name="收货位";receiving.type="RECEIVING";locationRepository.save(receiving);
        LocationEntity storage=new LocationEntity();storage.warehouseId=warehouseId;storage.code="A-01";storage.name="存储位";locationRepository.save(storage);
        ProductEntity product=new ProductEntity();product.sku="SKU-TEST";product.name="测试商品";product=productRepository.save(product);productId=product.id;
        PartnerEntity supplier=new PartnerEntity();supplier.code="SUP-TEST";supplier.name="测试供应商";supplier.type="SUPPLIER";supplier=partnerRepository.save(supplier);supplierId=supplier.id;
        PartnerEntity customer=new PartnerEntity();customer.code="CUS-TEST";customer.name="测试客户";customer.type="CUSTOMER";customer=partnerRepository.save(customer);customerId=customer.id;
    }

    @Test void completesInboundAllocationAndShipment() {
        InboundView inbound=service.createInbound(new InboundRequest(supplierId,warehouseId,null,"测试入库",List.of(new LineRequest(productId,20L,"B001",null))));
        inbound=service.receive(inbound.id(),new ReceiveRequest("REC-01",null),"tester");
        assertEquals("RECEIVED",inbound.status());
        assertEquals(20L,service.inventory("B001",warehouseId,1,20).records().stream()
                .filter(item -> "B001".equals(item.batchNo())).findFirst().orElseThrow().quantity());

        OutboundView outbound=service.createOutbound(new OutboundRequest(customerId,warehouseId,null,"测试出库",List.of(new LineRequest(productId,8L,"B001",null))));
        outbound=service.allocate(outbound.id(),"tester");
        assertEquals("ALLOCATED",outbound.status());
        outbound=service.pick(outbound.id(),"tester");
        assertEquals("PICKED",outbound.status());
        outbound=service.pack(outbound.id(),"tester");
        assertEquals("PACKED",outbound.status());
        outbound=service.ship(outbound.id(),"tester");
        assertEquals("SHIPPED",outbound.status());
        InventoryView stock=service.inventory("B001",warehouseId,1,20).records().stream()
                .filter(item -> "B001".equals(item.batchNo())).findFirst().orElseThrow();
        assertEquals(12L,stock.quantity()); assertEquals(0L,stock.allocatedQuantity());
    }

    @Test void supportsPartialReceiptCancellationAndReturn() {
        InboundView inbound=service.createInbound(new InboundRequest(supplierId,warehouseId,null,"分批收货",List.of(new LineRequest(productId,10L,"B-PARTIAL",null))));
        Long itemId=inbound.items().get(0).id();
        inbound=service.receive(inbound.id(),new ReceiveRequest("REC-01",List.of(new ReceiveLineRequest(itemId,4L))),"tester");
        assertEquals("PARTIALLY_RECEIVED",inbound.status());
        assertEquals(4L,inbound.receivedQuantity());
        inbound=service.receive(inbound.id(),new ReceiveRequest("REC-01",null),"tester");
        assertEquals("RECEIVED",inbound.status());
        assertEquals(10L,inbound.receivedQuantity());

        OutboundView cancelled=service.createOutbound(new OutboundRequest(customerId,warehouseId,null,"取消测试",List.of(new LineRequest(productId,2L,"B-PARTIAL",null))));
        cancelled=service.allocate(cancelled.id(),"tester");
        cancelled=service.cancel(cancelled.id(),"tester");
        assertEquals("CANCELLED",cancelled.status());
        assertEquals(0L,cancelled.allocatedQuantity());

        OutboundView returned=service.createOutbound(new OutboundRequest(customerId,warehouseId,null,"退货测试",List.of(new LineRequest(productId,3L,"B-PARTIAL",null))));
        returned=service.allocate(returned.id(),"tester");
        returned=service.ship(returned.id(),"tester");
        returned=service.returnShipment(returned.id(),"tester");
        assertEquals("RETURNED",returned.status());
        assertEquals(0L,returned.shippedQuantity());

        InventoryView counted=service.inventory("B-PARTIAL",warehouseId,1,20).records().stream()
                .filter(item -> "B-PARTIAL".equals(item.batchNo())).findFirst().orElseThrow();
        long actual=counted.quantity()+1;
        counted=service.stocktake(new StocktakeRequest(counted.id(),actual,"测试盘点"),"tester");
        assertEquals(actual,counted.quantity());
    }

    @Test void rejectsAllocationWhenStockIsInsufficient() {
        OutboundView outbound=service.createOutbound(new OutboundRequest(customerId,warehouseId,null,null,List.of(new LineRequest(productId,999L,"",null))));
        assertThrows(BusinessException.class,()->service.allocate(outbound.id(),"tester"));
    }

    @Test void bindsEveryGetEndpointParameterWithoutCompilerNameInference() throws Exception {
        String[] urls = {
                "/api/warehouses?keyword=WH&page=1&size=20",
                "/api/locations?keyword=REC&warehouseId=" + warehouseId + "&page=1&size=20",
                "/api/products?keyword=SKU&page=1&size=20",
                "/api/partners?keyword=TEST&type=SUPPLIER&page=1&size=20",
                "/api/inbound-orders?keyword=IN&status=PENDING&page=1&size=20",
                "/api/inventory?keyword=SKU&warehouseId=" + warehouseId + "&page=1&size=20",
                "/api/outbound-orders?keyword=OUT&status=PENDING&page=1&size=20",
                "/api/dashboard/summary"
        };
        for (String url : urls) {
            mvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
