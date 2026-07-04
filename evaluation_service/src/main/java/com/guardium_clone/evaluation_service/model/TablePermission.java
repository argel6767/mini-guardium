package com.guardium_clone.evaluation_service.model;

public enum TablePermission {
    READ,
    WRITE,
    DELETE,
    UPDATE;

    public static TablePermission fromString(String queryType) {
        return TablePermission.valueOf(queryType.toUpperCase());
    }
    
}