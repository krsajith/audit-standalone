package com.example.accessingdatajpa.audit.domain;

import lombok.Data;

@Data
public class EntityRelation {
    private String relatedEntityName;
    private String linkAttributeInRelatedEntity;
    private String linkAttributeInMainEntity;
}
