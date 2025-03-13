package com.example.accessingdatajpa.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@MappedSuperclass
public abstract class BaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(updatable = false)
    private String createdBy;

    private String updatedBy;

    @Column(updatable = false)
    private LocalDateTime createdTimestamp;

    private LocalDateTime updatedTimestamp;

    private String tenantId;
}
