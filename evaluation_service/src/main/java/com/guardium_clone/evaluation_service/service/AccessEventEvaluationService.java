package com.guardium_clone.evaluation_service.service;

import com.guardium_clone.evaluation_service.messaging.AccessEventCreatedMessage;
import com.guardium_clone.evaluation_service.model.Role;
import com.guardium_clone.evaluation_service.model.Table;
import com.guardium_clone.evaluation_service.model.TableAccess;
import com.guardium_clone.evaluation_service.model.TablePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccessEventEvaluationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessEventEvaluationService.class);

    private static final Map<Role, List<TableAccess>> roleTableAccessMap = Map.of(
            Role.ADMIN, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.values())),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.values())),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.values())),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.values())),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.values()))),
            Role.EMPLOYEE, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.values())),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ, TablePermission.WRITE)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.values()))),
            Role.GUEST, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of()),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.AUDIT_LOGS, Set.of()),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of()),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ))),
            Role.ETL_WORKER, List.of(
                    new TableAccess(Table.CUSTOMERS_ACCOUNTS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ, TablePermission.WRITE, TablePermission.UPDATE))),
            Role.REPORTING_SERVICE, List.of(
                    new TableAccess(Table.ORDERS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.AUDIT_LOGS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.EMPLOYEES_RECORDS, Set.of(TablePermission.READ)),
                    new TableAccess(Table.INVENTORY, Set.of(TablePermission.READ)))
    );

    public void evaluate(AccessEventCreatedMessage message) {
        LOGGER.debug("Access event {} accepted for future alert evaluation", message.accessEventId());
    }
}
