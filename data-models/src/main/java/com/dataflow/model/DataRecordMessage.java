package com.dataflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataRecordMessage implements Serializable {
    private Long timestamp;
    private Integer randomValue;
    private String hashValue;
}
