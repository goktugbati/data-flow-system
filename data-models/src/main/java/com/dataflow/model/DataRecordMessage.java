package com.dataflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataRecordMessage implements Serializable {
    private static final long serialVersionUID = 6942318612266764949L;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("randomValue")
    private Integer randomValue;

    @JsonProperty("hashValue")
    private String hashValue;
}
