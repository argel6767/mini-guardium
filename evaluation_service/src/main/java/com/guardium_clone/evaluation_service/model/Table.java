package com.guardium_clone.evaluation_service.model;

import java.util.Locale;

public enum Table {
    CUSTOMERS_ACCOUNTS,
    ORDERS,
    PAYMENT_CARDS,
    AUDIT_LOGS,
    EMPLOYEES_RECORDS,
    INVENTORY;

    public static Table fromString(String tableName) {
        return Table.valueOf(tableName.toUpperCase(Locale.ROOT));
    }
    
}
