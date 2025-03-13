
package com.example.accessingdatajpa.audit.repo;


import com.example.accessingdatajpa.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepo extends CrudRepository<AuditLog, Long>, JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    AuditLog findFirstByEntityNameAndEntityIdAndLatest(String entityName, String entityId, Boolean latest);
    List<AuditLog> findAllByEntityNameAndEntityId(String entityName, String entityId);

    List<AuditLog> findAllByEntityName(String entityName);

}
