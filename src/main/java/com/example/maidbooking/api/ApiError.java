package com.example.maidbooking.api;

import java.time.LocalDateTime;
import java.util.Map;

/** The single error body returned by every endpoint. */
public record ApiError(String code, String message, Map<String, Object> details, LocalDateTime timestamp) {
}
