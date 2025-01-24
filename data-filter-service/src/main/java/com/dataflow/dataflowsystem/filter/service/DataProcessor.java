package com.dataflow.dataflowsystem.filter.service;


import com.dataflow.model.DataRecord;

public interface DataProcessor {
    void processData(DataRecord record);
}
