package com.example.accessingdatajpa.audit.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditMessage {
    private String tenantId;
    private String entity;
    private String entityId;
    private String table;
    private Map<String,Object> payload;
    private String updateLocation;
    private boolean delete;
    private String updateBy;
}
