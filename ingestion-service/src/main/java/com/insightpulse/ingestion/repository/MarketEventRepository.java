package com.insightpulse.ingestion.repository;

import com.insightpulse.ingestion.entity.MarketEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for MarketEventEntity.
 * Provides automatic CRUD operations without writing SQL.
 */
@Repository
public interface MarketEventRepository extends JpaRepository<MarketEventEntity, String> {
    // Spring automatically provides:
    // - save(entity)
    // - findById(id)
    // - findAll()
    // - delete(entity)
    // and many more...
}
