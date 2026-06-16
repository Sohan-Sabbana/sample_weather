package com.example.validation.model;

import java.util.List;

/**
 * Outcome of a downstream validation check. {@code reasons} is empty when valid.
 */
public record ValidationResult(boolean valid, List<String> reasons, String checkedBy) {

    public static ValidationResult ok(String checkedBy) {
        return new ValidationResult(true, List.of(), checkedBy);
    }

    public static ValidationResult invalid(String checkedBy, List<String> reasons) {
        return new ValidationResult(false, reasons, checkedBy);
    }
}
