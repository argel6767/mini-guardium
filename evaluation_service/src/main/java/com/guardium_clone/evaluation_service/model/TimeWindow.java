package com.guardium_clone.evaluation_service.model;

import java.time.LocalTime;

public record TimeWindow(LocalTime start, LocalTime end) {

    public boolean contains(LocalTime time) {
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }

        // Handles overnight windows like 10 PM–6 AM
        return !time.isBefore(start) || time.isBefore(end);
    }
}