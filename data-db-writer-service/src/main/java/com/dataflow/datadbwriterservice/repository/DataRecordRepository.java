package com.dataflow.datadbwriterservice.repository;

import com.dataflow.datadbwriterservice.entity.DataRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRecordRepository extends JpaRepository<DataRecordEntity, Long> {
}
