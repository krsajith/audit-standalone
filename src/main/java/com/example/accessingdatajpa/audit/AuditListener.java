package com.example.accessingdatajpa.audit;

import com.example.accessingdatajpa.audit.domain.AuditMessage;
import com.example.accessingdatajpa.audit.domain.EntityInfo;
import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AuditListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final Set<String> excludedEntities = Set.of("AuditLog", "HttpTraceAuditLog");
    private final Set<String> keyFields = Set.of("tradeId");

    private final AuditService auditService;

    private final Logger log = LoggerFactory.getLogger(AuditListener.class);

    TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        String[] propertyNames = event.getPersister().getPropertyNames();
        Object[] oldState = event.getOldState();
        Object[] state = event.getState();

        Map<String, Object> changes = new HashMap<>();

        for (int i = 0; i < propertyNames.length; i++) {
            if (!Objects.equals(oldState[i], state[i])) {
                String fieldName = propertyNames[i];
                Object oldValue = oldState[i];
                Object newValue = state[i];

                if(keyFields.contains(fieldName)) {
                    changes.put(fieldName, newValue);
                    continue;
                }

                // Format: null -> newValue or oldValue -> null or oldValue -> newValue
                if (oldValue == null && newValue != null) {
                    changes.put(fieldName, newValue);
                } else if (oldValue != null && newValue == null) {
                    changes.put(fieldName, "NULL");  // Indicator for value set to null
                } else {
                    changes.put(fieldName, newValue);
//                    changes.put(fieldName, oldValue + " -> " + newValue);
                }
            }
        }
        saveAudit(changes, false,getEntityInfo(event.getEntity()));
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        saveAudit(event.getEntity(), false,getEntityInfo(event.getEntity()));
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void saveAudit(Object baseEntity, boolean isDelete,EntityInfo entityInfo) {
        if(entityInfo == null) return;
        try {
            auditService.saveAudit(new AuditMessage(entityInfo.tenantId(),entityInfo.entityName(), entityInfo.uuid(),
                    entityInfo.tableName(), objectMapper.convertValue(baseEntity, typeRef), "", isDelete,entityInfo.updatedBy()));
            log.debug("Updating {}", baseEntity);

        } catch (Throwable e) {
            log.debug("Error auditing ", e);
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        saveAudit(event.getEntity(), true,getEntityInfo(event.getEntity()));
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
            return new EntityInfo(tableName,simpleName,entity.getUuid().toString(),entity.getTenantId(),entity.getUpdatedBy());
    }
}
