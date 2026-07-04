package com.guardium_clone.evaluation_service.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    ADMIN,
    EMPLOYEE,
    GUEST,
    ETL_WORKER,
    REPORTING_SERVICE;

    @JsonCreator
    public static Role fromString(String value) {
        return Role.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}