package com.example.repository;

import com.example.entity.ProcessedData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedDataRepository extends JpaRepository<ProcessedData, Long> {
}
