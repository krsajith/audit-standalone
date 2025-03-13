package com.example.accessingdatajpa.audit.domain;

public record EntityInfo(String tableName, String entityName, String uuid,String tenantId,String updatedBy) {
}
