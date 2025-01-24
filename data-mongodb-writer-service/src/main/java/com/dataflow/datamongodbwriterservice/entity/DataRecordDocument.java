package com.dataflow.datamongodbwriterservice.entity;

import com.dataflow.model.DataRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "data_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecordDocument {
    @Id
    private String id;
    private Long timestamp;
    private Integer randomValue;
    private String hashValue;
    private List<DataRecordDocument> nestedRecords;

    public static DataRecordDocument fromDataRecord(DataRecord record) {
        return new DataRecordDocument(null, record.getTimestamp(),
                record.getRandomValue(), record.getHashValue(), new ArrayList<>());
    }
}
