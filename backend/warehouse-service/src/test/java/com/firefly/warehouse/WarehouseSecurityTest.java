package com.firefly.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "firefly.security.enabled=true")
@AutoConfigureMockMvc
class WarehouseSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired WarehouseRepository warehouses;
    @Autowired LocationRepository locations;
    @Autowired ProductRepository products;
    @Autowired MovementRepository movements;

    @Test
    void rejectsAnonymousAndInvalidTokens() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void allowsReadRolesButRejectsUnauthorizedWrites() throws Exception {
        String pickerToken = jwtService.create(new TokenClaims(7L, "picker", "PICKER"));
        mvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + pickerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String receiverToken = jwtService.create(new TokenClaims(8L, "receiver", "RECEIVER"));
        mvc.perform(post("/api/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + receiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mvc.perform(get("/api/carrier-accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + receiverToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void acceptsWarehouseAdminAlias() throws Exception {
        String token = jwtService.create(new TokenClaims(9L, "legacy-admin", "WAREHOUSE_ADMIN"));
        mvc.perform(get("/api/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Username", "forged-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void matchesReceiverAndPickerCommandPaths() throws Exception {
        String receiverToken = jwtService.create(new TokenClaims(10L, "receiver", "RECEIVER"));
        mvc.perform(post("/api/inbound-orders/999999/receive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + receiverToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        String pickerToken = jwtService.create(new TokenClaims(11L, "picker", "PICKER"));
        mvc.perform(post("/api/outbound-orders/999999/ship")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + pickerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void replacesForgedOperatorHeaderWithJwtUsername() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.code = "WH-SEC-" + suffix;
        warehouse.name = "安全测试仓";
        warehouse = warehouses.save(warehouse);

        LocationEntity location = new LocationEntity();
        location.warehouseId = warehouse.id;
        location.code = "LOC-" + suffix;
        location.name = "安全测试货位";
        locations.save(location);

        ProductEntity product = new ProductEntity();
        product.sku = "SKU-SEC-" + suffix;
        product.name = "安全测试商品";
        product = products.save(product);
        Long productId = product.id;

        var request = new ApiModels.AdjustmentRequest(
                warehouse.id, location.code, productId, 1L, "SEC-" + suffix, null, "身份头测试");
        String token = jwtService.create(new TokenClaims(12L, "trusted-manager", "WAREHOUSE_MANAGER"));
        mvc.perform(post("/api/inventory/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Username", "forged-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        InventoryMovementEntity movement = movements.findTop10ByOrderByCreatedAtDesc().stream()
                .filter(item -> productId.equals(item.productId))
                .findFirst()
                .orElseThrow();
        assertEquals("trusted-manager", movement.operatorName);
    }
}
