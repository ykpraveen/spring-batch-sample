package com.example.repository;

import com.example.entity.SalesData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesDataRepository extends JpaRepository<SalesData, Long> {
    
    @Query("SELECT s FROM SalesData s WHERE s.id BETWEEN :minId AND :maxId AND s.processed = false")
    Page<SalesData> findUnprocessedByIdRange(@Param("minId") Long minId, @Param("maxId") Long maxId, Pageable pageable);
    
    @Query("SELECT COUNT(s) FROM SalesData s WHERE s.processed = false")
    Long countUnprocessed();
    
    @Query("SELECT MAX(s.id) FROM SalesData s")
    Long findMaxId();

    @Query("SELECT MIN(s.id) FROM SalesData s WHERE s.processed = false")
    Long findMinUnprocessedId();
}
