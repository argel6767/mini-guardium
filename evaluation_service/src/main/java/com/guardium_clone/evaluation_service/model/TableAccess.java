package com.guardium_clone.evaluation_service.model;

import java.util.Set;

public record TableAccess(Table table, Set<TablePermission> permissions) {
} 