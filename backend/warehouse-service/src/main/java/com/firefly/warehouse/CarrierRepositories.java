package com.firefly.warehouse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface CarrierAccountRepository extends JpaRepository<CarrierAccountEntity, Long> {
    @Query(value = """
            select a from CarrierAccountEntity a, WarehouseEntity w
            where w.id = a.warehouseId
              and (:carrierCode = '' or a.carrierCode = :carrierCode)
              and (:keyword = '' or lower(a.accountName) like lower(concat('%', :keyword, '%'))
                   or lower(a.carrierCode) like lower(concat('%', :keyword, '%'))
                   or lower(w.name) like lower(concat('%', :keyword, '%')))
            order by a.updatedAt desc
            """, countQuery = """
            select count(a) from CarrierAccountEntity a, WarehouseEntity w
            where w.id = a.warehouseId
              and (:carrierCode = '' or a.carrierCode = :carrierCode)
              and (:keyword = '' or lower(a.accountName) like lower(concat('%', :keyword, '%'))
                   or lower(a.carrierCode) like lower(concat('%', :keyword, '%'))
                   or lower(w.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<CarrierAccountEntity> search(@Param("keyword") String keyword, @Param("carrierCode") String carrierCode, Pageable pageable);

    @Query("""
            select a.id from CarrierAccountEntity a
            where a.status = 'ACTIVE' and a.syncEnabled = true
              and (a.nextSyncAt is null or a.nextSyncAt <= :now)
              and (a.leaseUntil is null or a.leaseUntil <= :now)
              and (a.circuitOpenedUntil is null or a.circuitOpenedUntil <= :now)
            order by a.nextSyncAt, a.id
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update CarrierAccountEntity a set a.leaseUntil = :leaseUntil
            where a.id = :id and a.status = 'ACTIVE' and a.syncEnabled = true
              and (a.nextSyncAt is null or a.nextSyncAt <= :now)
              and (a.leaseUntil is null or a.leaseUntil <= :now)
              and (a.circuitOpenedUntil is null or a.circuitOpenedUntil <= :now)
            """)
    int claimDue(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update CarrierAccountEntity a set a.leaseUntil = :leaseUntil
            where a.id = :id and a.status = 'ACTIVE'
              and (a.leaseUntil is null or a.leaseUntil <= :now)
              and (a.circuitOpenedUntil is null or a.circuitOpenedUntil <= :now)
              and (a.lastSyncedAt is null or a.lastSyncedAt <= :cooldownBefore)
            """)
    int claimManual(@Param("id") Long id, @Param("now") LocalDateTime now,
                    @Param("cooldownBefore") LocalDateTime cooldownBefore,
                    @Param("leaseUntil") LocalDateTime leaseUntil);
}

interface CarrierOrderRepository extends JpaRepository<CarrierOrderEntity, Long> {
    Optional<CarrierOrderEntity> findByAccountIdAndExternalOrderNo(Long accountId, String externalOrderNo);

    @Query(value = """
            select o from CarrierOrderEntity o, CarrierAccountEntity a
            where a.id = o.accountId
              and (:status = '' or o.status = :status)
              and (:keyword = '' or lower(o.externalOrderNo) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(o.trackingNo, '')) like lower(concat('%', :keyword, '%'))
                   or lower(a.accountName) like lower(concat('%', :keyword, '%')))
            order by o.syncedAt desc, o.id desc
            """, countQuery = """
            select count(o) from CarrierOrderEntity o, CarrierAccountEntity a
            where a.id = o.accountId
              and (:status = '' or o.status = :status)
              and (:keyword = '' or lower(o.externalOrderNo) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(o.trackingNo, '')) like lower(concat('%', :keyword, '%'))
                   or lower(a.accountName) like lower(concat('%', :keyword, '%')))
            """)
    Page<CarrierOrderEntity> search(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);
}

interface CarrierSyncLogRepository extends JpaRepository<CarrierSyncLogEntity, Long> {
    @Query(value = """
            select l from CarrierSyncLogEntity l, CarrierAccountEntity a
            where a.id = l.accountId
              and (:accountId is null or l.accountId = :accountId)
            order by l.startedAt desc, l.id desc
            """, countQuery = """
            select count(l) from CarrierSyncLogEntity l
            where :accountId is null or l.accountId = :accountId
            """)
    Page<CarrierSyncLogEntity> search(@Param("accountId") Long accountId, Pageable pageable);
}
