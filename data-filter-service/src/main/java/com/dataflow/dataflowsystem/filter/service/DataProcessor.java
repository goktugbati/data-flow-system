package com.dataflow.dataflowsystem.filter.service;


import com.dataflow.model.DataRecordMessage;

public interface DataProcessor {
    void processData(DataRecordMessage record);
}
