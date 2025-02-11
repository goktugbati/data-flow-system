package com.dataflow.datamongodbwriterservice.entity;

import com.dataflow.model.DataRecordMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Document(collection = "data_records")
@CompoundIndex(def = "{'timestamp': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecordDocument {
    @Id
    private String id;
    private Long timestamp;
    private Integer randomValue;
    private String hashValue;
    private List<DataRecordDocument> nestedRecords = new ArrayList<>();

    public static DataRecordDocument fromDataRecord(DataRecordMessage record) {
        DataRecordDocument doc = new DataRecordDocument();
        doc.setId(String.valueOf(UUID.randomUUID()));
        doc.setTimestamp(record.getTimestamp());
        doc.setRandomValue(record.getRandomValue());
        doc.setHashValue(record.getHashValue());
        return doc;
    }
}
