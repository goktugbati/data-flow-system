package com.dataflow.dataflowsystem.filter.service;


import com.dataflow.model.DataRecordMessage;

import java.util.List;

public interface DataProcessor {
    void processBatch(List<DataRecordMessage> record);
}
