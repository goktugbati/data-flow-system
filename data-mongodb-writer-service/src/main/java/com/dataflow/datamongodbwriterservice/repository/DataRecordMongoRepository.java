package com.dataflow.datamongodbwriterservice.repository;

import com.dataflow.datamongodbwriterservice.entity.DataRecordDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DataRecordMongoRepository extends MongoRepository<DataRecordDocument, String> {
    Optional<DataRecordDocument> findFirstByOrderByTimestampDesc();
    boolean existsById(String hash);
    @Query(value = "{ '_id' : ?0 }", fields = "{ 'nestedRecords' : 1 }")
    @Update("{ '$push' : { 'nestedRecords' : ?1 } }")
    void addNestedRecord(String id, DataRecordDocument nestedRecord);

}
