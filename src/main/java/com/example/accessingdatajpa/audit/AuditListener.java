package com.example.accessingdatajpa.audit;

import com.example.accessingdatajpa.audit.domain.AuditMessage;
import com.example.accessingdatajpa.audit.domain.EntityInfo;
import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.taomish.utils.JsonUtils;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final Set<String> excludedEntities = Set.of("AuditLog", "HttpTraceAuditLog");
    private final Set<String> keyFields = Set.of("tradeId");

    private final AuditService auditService;

    private final EntityManager entityManager;

    private final Logger log = LoggerFactory.getLogger(AuditListener.class);

    TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = JsonUtils.buildObjectMapper();

    public AuditListener(AuditService auditService, EntityManager entityManager) {
        this.auditService = auditService;
        this.entityManager = entityManager;
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        // Early return if entity should be excluded
        if (shouldExclude(event.getEntity())) return;

        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] oldState = event.getOldState();
        Object[] state = event.getState();
        Map<String, Object> changes = new HashMap<>();

        // Process each property in a single loop
        for (int i = 0; i < propertyNames.length; i++) {
            String fieldName = propertyNames[i];
            Object oldValue = oldState[i];
            Object newValue = state[i];

            // Skip if values are effectively equal (handles LocalDateTime precision issues)
            if (isEffectivelyEqual(oldValue, newValue)) {
                continue;
            }

            // Handle key fields differently
            if (keyFields.contains(fieldName)) {
                changes.put(fieldName, newValue);
                continue;
            }

            // Process primitive values
            if (ObjectFlattener.isPrimitiveOrWrapper(newValue) || ObjectFlattener.isPrimitiveOrWrapper(oldValue)) {
                changes.put(fieldName, oldValue + "->" + newValue);
                continue;
            }

            // Process complex objects
            processComplexObject(fieldName, oldValue, newValue, changes);
        }

        // Only save audit if there are actual changes
        if (!changes.isEmpty()) {
            saveAudit(changes, false, getEntityInfo(event.getEntity()));
        }
    }

    /**
     * Checks if two values are effectively equal, handling special cases like LocalDateTime
     */
    private boolean isEffectivelyEqual(Object oldValue, Object newValue) {
        if (oldValue == newValue) return true;
        if (oldValue == null || newValue == null) return false;

        // Handle LocalDateTime special case
        if (oldValue instanceof LocalDateTime && newValue instanceof LocalDateTime) {
            LocalDateTime oldTime = (LocalDateTime) oldValue;
            LocalDateTime newTime = (LocalDateTime) newValue;
            // Consider equal if they're within 1 millisecond of each other
            return Math.abs(ChronoUnit.MILLIS.between(oldTime, newTime)) < 1;
        }

        return oldValue.equals(newValue);
    }

    /**
     * Process complex (non-primitive) objects by flattening and comparing them
     */
    private void processComplexObject(String fieldName, Object oldValue, Object newValue, Map<String, Object> changes) {
        try {
            if (oldValue == null) {
                changes.put(fieldName, newValue);
                return;
            }

            // Flatten both objects and find differences
            Map<String, Object> oldMap = ObjectFlattener.flatten(oldValue, fieldName + '.');
            Map<String, Object> newMap = ObjectFlattener.flatten(newValue, fieldName + '.');
            var difference = Maps.difference(oldMap, newMap);

            // Process differences
            difference.entriesDiffering().forEach((k, v) -> {
                if (!ObjectUtils.isEmpty(v.leftValue()) || !ObjectUtils.isEmpty(v.rightValue())) {
                    changes.put(k, v.leftValue() + "->" + v.rightValue());
                }
            });

            // Process removals (only in old)
            difference.entriesOnlyOnLeft().forEach((k, v) -> {
                if (!ObjectUtils.isEmpty(v)) changes.put(k, "-" + v);
            });

            // Process additions (only in new)
            difference.entriesOnlyOnRight().forEach((k, v) -> {
                if (!ObjectUtils.isEmpty(v)) changes.put(k, "+" + v);
            });
        } catch (Exception e) {
            log.error("Error processing field: " + fieldName, e);
        }
    }

    private static boolean isEquals(Object oldValue, Object newValue) {
        return Objects.equals(oldValue, newValue);
    }

    private boolean shouldExclude(Object event) {
        return excludedEntities.contains(event.getClass().getSimpleName());
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        if (shouldExclude(event.getEntity())) return;
        saveAudit(event.getEntity(), false, getEntityInfo(event.getEntity()));
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void saveAudit(Object baseEntity, boolean isDelete, EntityInfo entityInfo) {
        if (entityInfo == null) return;
        try {
            auditService.saveAudit(new AuditMessage(entityInfo.tenantId(), entityInfo.entityName(), entityInfo.uuid(),
                    entityInfo.tableName(), objectMapper.convertValue(baseEntity, typeRef), "", isDelete, entityInfo.updatedBy()));
            log.debug("Updating {}", baseEntity);

        } catch (Throwable e) {
            log.debug("Error auditing ", e);
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        saveAudit(event.getEntity(), true, getEntityInfo(event.getEntity()));
    }

    private EntityInfo getEntityInfo(Object baseEntity) {
        String simpleName = baseEntity.getClass().getSimpleName();

        String tableName;
        var table = baseEntity.getClass().getAnnotation(Table.class);
        if (table != null) {
            tableName = table.name();
        } else {
            var entity = baseEntity.getClass().getAnnotation(Entity.class);
            tableName = entity.name();
        }
        if (excludedEntities.contains(simpleName)) {
            return null;
        }
        AbstractBaseEntity entity = (AbstractBaseEntity) baseEntity;
        return new EntityInfo(tableName, simpleName, entity.getUuid().toString(), entity.getTenantId(), entity.getUpdatedBy());
    }

    public String camelCaseToTitleCase(String camelCase) {
        // First, use Guava's CaseFormat to convert from camelCase to space-separated words
        String spaceDelimited = com.google.common.base.CaseFormat.LOWER_CAMEL
                .to(com.google.common.base.CaseFormat.LOWER_UNDERSCORE, camelCase)
                .replace('_', ' ');

        // Use Apache Commons Text's capitalization method
        String[] words = spaceDelimited.split(" ");
        for (int i = 0; i < words.length; i++) {
            words[i] = org.apache.commons.lang3.StringUtils.capitalize(words[i].toLowerCase());
        }

        return String.join(" ", words);
    }
}
