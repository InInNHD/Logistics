package com.firefly.warehouse;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface WarehouseRepository extends JpaRepository<WarehouseEntity, Long> {
    Optional<WarehouseEntity> findByCodeIgnoreCase(String code);

    @Query("""
            select w from WarehouseEntity w
            where :keyword = ''
               or lower(w.code) like lower(concat('%', :keyword, '%'))
               or lower(w.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(w.address, '')) like lower(concat('%', :keyword, '%'))
            """)
    Page<WarehouseEntity> search(@Param("keyword") String keyword, Pageable pageable);
}

interface LocationRepository extends JpaRepository<LocationEntity, Long> {
    Optional<LocationEntity> findByWarehouseIdAndCodeIgnoreCase(Long warehouseId, String code);
    List<LocationEntity> findByWarehouseIdOrderById(Long warehouseId);

    @Query(value = """
            select l from LocationEntity l, WarehouseEntity w
            where w.id = l.warehouseId
              and (:warehouseId is null or l.warehouseId = :warehouseId)
              and (:keyword = ''
                   or lower(l.code) like lower(concat('%', :keyword, '%'))
                   or lower(l.name) like lower(concat('%', :keyword, '%'))
                   or lower(w.name) like lower(concat('%', :keyword, '%')))
            """, countQuery = """
            select count(l) from LocationEntity l, WarehouseEntity w
            where w.id = l.warehouseId
              and (:warehouseId is null or l.warehouseId = :warehouseId)
              and (:keyword = ''
                   or lower(l.code) like lower(concat('%', :keyword, '%'))
                   or lower(l.name) like lower(concat('%', :keyword, '%'))
                   or lower(w.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<LocationEntity> search(@Param("keyword") String keyword, @Param("warehouseId") Long warehouseId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LocationEntity l where l.id in :ids order by l.id")
    List<LocationEntity> lockByIds(@Param("ids") Collection<Long> ids);
}

interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findBySkuIgnoreCase(String sku);

    @Query("""
            select p from ProductEntity p
            where :keyword = ''
               or lower(p.sku) like lower(concat('%', :keyword, '%'))
               or lower(p.name) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.category, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.barcode, '')) like lower(concat('%', :keyword, '%'))
            """)
    Page<ProductEntity> search(@Param("keyword") String keyword, Pageable pageable);
}

interface PartnerRepository extends JpaRepository<PartnerEntity, Long> {
    Optional<PartnerEntity> findByCodeIgnoreCase(String code);

    @Query("""
            select p from PartnerEntity p
            where (:type = '' or upper(p.type) = upper(:type))
              and (:keyword = ''
                   or lower(p.code) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.contact, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.phone, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<PartnerEntity> search(@Param("keyword") String keyword, @Param("type") String type, Pageable pageable);
}

interface InboundOrderRepository extends JpaRepository<InboundOrderEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InboundOrderEntity o left join fetch o.items where o.id = :id")
    Optional<InboundOrderEntity> lockById(@Param("id") Long id);

    @Query(value = """
            select o from InboundOrderEntity o, PartnerEntity p
            where p.id = o.supplierId
              and (:status = '' or upper(o.status) = upper(:status))
              and (:keyword = ''
                   or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%')))
            order by o.createdAt desc
            """, countQuery = """
            select count(o) from InboundOrderEntity o, PartnerEntity p
            where p.id = o.supplierId
              and (:status = '' or upper(o.status) = upper(:status))
              and (:keyword = ''
                   or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<InboundOrderEntity> search(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);

    @Query("select distinct o from InboundOrderEntity o left join fetch o.items where o.id in :ids")
    List<InboundOrderEntity> findWithItemsByIds(@Param("ids") Collection<Long> ids);

    long countByStatus(String status);
}

interface InventoryRepository extends JpaRepository<InventoryBalanceEntity, Long> {
    Optional<InventoryBalanceEntity> findByWarehouseIdAndLocationIdAndProductIdAndBatchNo(Long warehouseId, Long locationId, Long productId, String batchNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from InventoryBalanceEntity i
            where i.warehouseId = :warehouseId and i.locationId = :locationId
              and i.productId = :productId and i.batchNo = :batchNo
            """)
    Optional<InventoryBalanceEntity> lockByDimension(@Param("warehouseId") Long warehouseId,
                                                     @Param("locationId") Long locationId,
                                                     @Param("productId") Long productId,
                                                     @Param("batchNo") String batchNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from InventoryBalanceEntity i, LocationEntity l
            where l.id = i.locationId
              and i.warehouseId = :warehouseId and i.productId = :productId
              and (:batchNo = '' or i.batchNo = :batchNo)
              and i.quantity - i.allocatedQuantity - i.lockedQuantity > 0
              and (i.expiryDate is null or i.expiryDate >= :today)
              and l.status = 'ACTIVE'
            order by case when i.expiryDate is null then 1 else 0 end, i.expiryDate asc, i.id asc
            """)
    List<InventoryBalanceEntity> lockAvailable(@Param("warehouseId") Long warehouseId,
                                               @Param("productId") Long productId,
                                               @Param("batchNo") String batchNo,
                                               @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryBalanceEntity i where i.id = :id")
    Optional<InventoryBalanceEntity> lockById(@Param("id") Long id);

    @Query(value = """
            select i from InventoryBalanceEntity i, WarehouseEntity w, LocationEntity l, ProductEntity p
            where w.id = i.warehouseId and l.id = i.locationId and p.id = i.productId
              and (:warehouseId is null or i.warehouseId = :warehouseId)
              and (:keyword = ''
                   or lower(p.sku) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(i.batchNo) like lower(concat('%', :keyword, '%'))
                   or lower(l.code) like lower(concat('%', :keyword, '%')))
            order by i.updatedAt desc, i.id desc
            """, countQuery = """
            select count(i) from InventoryBalanceEntity i, WarehouseEntity w, LocationEntity l, ProductEntity p
            where w.id = i.warehouseId and l.id = i.locationId and p.id = i.productId
              and (:warehouseId is null or i.warehouseId = :warehouseId)
              and (:keyword = ''
                   or lower(p.sku) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(i.batchNo) like lower(concat('%', :keyword, '%'))
                   or lower(l.code) like lower(concat('%', :keyword, '%')))
            """)
    Page<InventoryBalanceEntity> search(@Param("keyword") String keyword, @Param("warehouseId") Long warehouseId, Pageable pageable);

    @Query("select coalesce(sum(i.quantity), 0) from InventoryBalanceEntity i")
    long totalQuantity();

    @Query("""
            select count(i) from InventoryBalanceEntity i, ProductEntity p
            where p.id = i.productId
              and i.quantity - i.allocatedQuantity - i.lockedQuantity < p.safetyStock
            """)
    long countLowStock();

    long countByExpiryDateLessThanEqual(LocalDate date);
}

interface MovementRepository extends JpaRepository<InventoryMovementEntity, Long> {
    List<InventoryMovementEntity> findTop10ByOrderByCreatedAtDesc();
    List<InventoryMovementEntity> findByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query(value = """
            select m from InventoryMovementEntity m, WarehouseEntity w, ProductEntity p
            where w.id = m.warehouseId and p.id = m.productId
              and (:warehouseId is null or m.warehouseId = :warehouseId)
              and (:type = '' or upper(m.type) = upper(:type))
              and (:keyword = ''
                   or lower(m.movementNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.sku) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(m.batchNo) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.referenceType, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.operatorName, '')) like lower(concat('%', :keyword, '%')))
            order by m.createdAt desc, m.id desc
            """, countQuery = """
            select count(m) from InventoryMovementEntity m, WarehouseEntity w, ProductEntity p
            where w.id = m.warehouseId and p.id = m.productId
              and (:warehouseId is null or m.warehouseId = :warehouseId)
              and (:type = '' or upper(m.type) = upper(:type))
              and (:keyword = ''
                   or lower(m.movementNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.sku) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%'))
                   or lower(m.batchNo) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.referenceType, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(m.operatorName, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<InventoryMovementEntity> search(@Param("keyword") String keyword,
                                         @Param("warehouseId") Long warehouseId,
                                         @Param("type") String type,
                                         Pageable pageable);
}

interface OutboundOrderRepository extends JpaRepository<OutboundOrderEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutboundOrderEntity o left join fetch o.items where o.id = :id")
    Optional<OutboundOrderEntity> lockById(@Param("id") Long id);

    @Query(value = """
            select o from OutboundOrderEntity o, PartnerEntity p
            where p.id = o.customerId
              and (:status = '' or upper(o.status) = upper(:status))
              and (:keyword = ''
                   or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%')))
            order by o.createdAt desc
            """, countQuery = """
            select count(o) from OutboundOrderEntity o, PartnerEntity p
            where p.id = o.customerId
              and (:status = '' or upper(o.status) = upper(:status))
              and (:keyword = ''
                   or lower(o.orderNo) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<OutboundOrderEntity> search(@Param("keyword") String keyword, @Param("status") String status, Pageable pageable);

    @Query("select distinct o from OutboundOrderEntity o left join fetch o.items where o.id in :ids")
    List<OutboundOrderEntity> findWithItemsByIds(@Param("ids") Collection<Long> ids);

    long countByStatusNotIn(Collection<String> statuses);
}

interface AllocationRepository extends JpaRepository<OutboundAllocationEntity, Long> {
    List<OutboundAllocationEntity> findByOrderIdOrderByInventoryIdAscIdAsc(Long orderId);
}

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {
    Optional<IdempotencyRecordEntity> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}
