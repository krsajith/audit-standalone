package com.example.accessingdatajpa.audit.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "ctrm_audit_log_v2")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String entityName;
    private String entityId;
    private String tableName;
    private Boolean latest = true;
    private String action = "Create";
    private String parentId;

    @JdbcTypeCode( SqlTypes.JSON )
    @Column(columnDefinition = "jsonb")
    private Map<String,Object> differenceList;

    @CreationTimestamp
    
    private LocalDateTime createdTimestamp;
}
