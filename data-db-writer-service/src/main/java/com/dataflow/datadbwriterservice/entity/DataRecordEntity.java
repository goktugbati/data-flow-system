package com.dataflow.datadbwriterservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Table(name = "data_records")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant timestamp;
    private int randomValue;
    private String hashValue;
}
