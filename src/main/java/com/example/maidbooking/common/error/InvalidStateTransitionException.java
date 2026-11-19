package com.example.maidbooking.common.error;

import java.util.Map;

/** Thrown by entities when an action is not allowed from their current state. */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String entity, Enum<?> from, String action) {
        super(ErrorCode.INVALID_STATE,
                "Cannot " + action + " " + entity + " in state " + from.name(),
                Map.of("entity", entity, "from", from.name(), "action", action));
    }
}
