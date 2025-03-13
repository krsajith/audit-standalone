package com.example.accessingdatajpa.audit;

import com.example.accessingdatajpa.audit.domain.AuditMessage;
import com.example.accessingdatajpa.audit.model.AuditLog;
import com.example.accessingdatajpa.audit.repo.AuditLogRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j

public class AuditService {
    private final AuditLogRepo auditLogRepo;
    private final Map<String, String> parentKeyMap = new HashMap<>();
    private final Map<String, Set<String>> excludedTables = Map.of("System", Set.of( "AuditLog"));
    private List<String> ignoreFields = List.of("uuid", "tenantId", "createdBy", "updatedBy", "updatedTimestamp", "createdTimestamp");

    @Value("${dateFormat:dd/MM/yyyy, h:m a}")
    private String dateFormat;

    public AuditService(AuditLogRepo auditLogRepo) throws JsonProcessingException {
        this.auditLogRepo = auditLogRepo;
   }




    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @Async
    public void saveAudit(AuditMessage audit) {
        var excludeTables = excludedTables.getOrDefault(audit.getTenantId(), excludedTables.get("System"));
        if (excludeTables.contains(audit.getTable())) {
            log.info("Excluding table {}", audit.getTable());
            return;
        }
        try {
            log.info("Received message {}", audit);
            var auditLog = new AuditLog();
            auditLog.setDifferenceList(audit.getPayload());
            auditLog.setEntityName(audit.getEntity());
            auditLog.setTableName(audit.getTable());
            auditLog.setEntityId(audit.getEntityId());
//            auditLog.setUpdatedBy(audit.getUpdateBy());
//            auditLog.setParentId(getParentId(audit));

            var prevLog = auditLogRepo.findFirstByEntityNameAndEntityIdAndLatest(auditLog.getEntityName(), auditLog.getEntityId(), true);
            if (prevLog != null) {
                prevLog.setLatest(false);
                auditLog.setAction(audit.isDelete() ? "Delete" : "Update");

                if (!audit.isDelete() && ObjectUtils.isEmpty(auditLog.getDifferenceList())) {
                    return;
                }
                auditLogRepo.save(prevLog);
            }

            auditLogRepo.save(auditLog);
        } catch (Exception e) {
            log.info("Error auditing {}", audit, e);
        }
    }

}
