package com.firefly.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CarrierIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired WarehouseRepository warehouses;
    @Autowired CarrierAccountRepository accounts;
    @Autowired CarrierSyncCoordinator coordinator;

    @Test
    void createsEncryptedAccountAndSynchronizesOrdersIdempotently() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.code = "WH-CAR-" + suffix;
        warehouse.name = "快递集成测试仓";
        warehouse = warehouses.save(warehouse);
        String secret = "mock-token-" + suffix;
        String body = """
                {"warehouseId":%d,"carrierCode":"SF","accountName":"沙箱账号-%s",
                 "apiBaseUrl":"mock://sf-express","credential":"%s","status":"ACTIVE"}
                """.formatted(warehouse.id, suffix, secret);

        String response = mvc.perform(post("/api/carrier-accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialHint").value("••••" + suffix.substring(4)))
                .andExpect(jsonPath("$.data.credential").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        long accountId = json.path("data").path("id").asLong();
        CarrierAccountEntity stored = accounts.findById(accountId).orElseThrow();
        assertNotEquals(secret, stored.credentialCiphertext);
        assertFalse(stored.credentialCiphertext.contains(secret));

        mvc.perform(post("/api/carrier-accounts/{id}/test", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.connectionStatus").value("AVAILABLE"));
        mvc.perform(post("/api/carrier-accounts/{id}/sync", accountId)).andExpect(status().isOk());
        mvc.perform(post("/api/carrier-accounts/{id}/sync", accountId)).andExpect(status().isOk());

        mvc.perform(get("/api/carrier-orders").param("keyword", "沙箱账号-" + suffix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(get("/api/carrier-sync-logs").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void schedulesDueAccountsAndPersistsCircuitBreakerFailures() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.code = "WH-SCH-" + suffix;
        warehouse.name = "定时同步测试仓";
        warehouse = warehouses.save(warehouse);
        String body = """
                {"warehouseId":%d,"carrierCode":"ZTO","accountName":"定时账号-%s",
                 "apiBaseUrl":"mock://zto","credential":"mock-scheduled-%s","status":"ACTIVE",
                 "syncEnabled":true,"syncIntervalMinutes":1}
                """.formatted(warehouse.id, suffix, suffix);
        String response = mvc.perform(post("/api/carrier-accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.syncEnabled").value(true))
                .andReturn().getResponse().getContentAsString();
        long accountId = mapper.readTree(response).path("data").path("id").asLong();

        coordinator.syncDueAccounts();
        CarrierAccountEntity synced = accounts.findById(accountId).orElseThrow();
        assertNotNull(synced.lastSyncedAt);
        assertNotNull(synced.nextSyncAt);
        assertNull(synced.leaseUntil);
        mvc.perform(get("/api/carrier-sync-logs").param("accountId", String.valueOf(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].triggerType").value("SCHEDULED"));

        synced.credentialCiphertext = "corrupted-ciphertext";
        synced.nextSyncAt = null;
        accounts.saveAndFlush(synced);
        assertThrows(RuntimeException.class, () -> coordinator.manual(accountId));
        CarrierAccountEntity failed = accounts.findById(accountId).orElseThrow();
        assertEquals(1, failed.consecutiveFailures);
        assertEquals("FAILED", failed.connectionStatus);
        assertNull(failed.leaseUntil);
    }
}
