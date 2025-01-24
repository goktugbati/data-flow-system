package com.dataflow.datamongodbwriterservice.repository;

import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataRecordMongoRepository extends MongoRepository<DataRecordDocument, String> {
    Optional<DataRecordDocument> findFirstByOrderByTimestampDesc();
}
