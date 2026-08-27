package com.firefly.auth;

import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.repository.UserAccountRepository;
import com.firefly.auth.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
class UserManagementConcurrencyTest {
    @Autowired UserManagementService service;
    @Autowired UserAccountRepository repository;
    @Autowired PasswordEncoder encoder;

    @Test void concurrentCrossDisableCannotRemoveEveryAdministrator() throws Exception {
        UserAccount first = repository.findByUsername("admin").orElseThrow();
        first.updateProfile(null, "ADMIN", true);
        repository.saveAndFlush(first);
        String secondUsername = "concurrent_admin_" + System.nanoTime();
        UserAccount second = repository.saveAndFlush(
                new UserAccount(secondUsername, encoder.encode("Strong@123"), "并发管理员", "ADMIN"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstAttempt = executor.submit(() -> disableAfterSignal(ready, start, second.getId(), first.getId()));
            var secondAttempt = executor.submit(() -> disableAfterSignal(ready, start, first.getId(), second.getId()));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent workers did not become ready");
            start.countDown();

            Throwable firstFailure = firstAttempt.get(10, TimeUnit.SECONDS);
            Throwable secondFailure = secondAttempt.get(10, TimeUnit.SECONDS);
            long successes = Stream.of(firstFailure, secondFailure).filter(failure -> failure == null).count();
            Throwable rejected = firstFailure == null ? secondFailure : firstFailure;

            assertEquals(1, successes);
            assertInstanceOf(UserManagementService.ManagementException.class, rejected);
            assertEquals(1, repository.countByRoleInAndEnabledTrue(List.of("ADMIN", "WAREHOUSE_ADMIN")));
        } finally {
            executor.shutdownNow();
            UserAccount restored = repository.findById(first.getId()).orElseThrow();
            restored.updateProfile(null, "ADMIN", true);
            repository.saveAndFlush(restored);
            if (repository.existsById(second.getId())) repository.deleteById(second.getId());
        }
    }

    private Throwable disableAfterSignal(CountDownLatch ready, CountDownLatch start, Long targetId, Long operatorId) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) return new AssertionError("Start signal timed out");
            service.update(targetId, operatorId, null, null, null, "DISABLED", null);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }
}
