package com.example.maidbooking.common.error;

import java.util.Map;

public class NotFoundException extends DomainException {

    public NotFoundException(String entity, Object id) {
        super(ErrorCode.NOT_FOUND, entity + " " + id + " not found", Map.of("entity", entity, "id", String.valueOf(id)));
    }
}
