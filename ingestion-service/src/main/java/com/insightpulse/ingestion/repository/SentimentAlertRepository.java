package com.insightpulse.ingestion.repository;

import com.insightpulse.ingestion.entity.SentimentAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for SentimentAlertEntity.
 * Provides automatic CRUD operations without writing SQL.
 */
@Repository
public interface SentimentAlertRepository extends JpaRepository<SentimentAlertEntity, Long> {
    // Spring automatically provides:
    // - save(entity)
    // - findById(id)
    // - findAll()
    // - delete(entity)
    // and many more...
}
