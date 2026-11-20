package com.example.maidbooking.booking.type;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.common.error.ValidationException;

/** Finds the handler for a booking type; built from every {@link BookingTypeHandler} bean. */
@Component
public class BookingTypeRegistry {

    private final Map<BookingType, BookingTypeHandler> handlers = new EnumMap<>(BookingType.class);

    public BookingTypeRegistry(List<BookingTypeHandler> handlers) {
        for (BookingTypeHandler handler : handlers) {
            BookingTypeHandler previous = this.handlers.putIfAbsent(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers for booking type " + handler.type() + ": "
                        + previous.getClass().getSimpleName() + " and " + handler.getClass().getSimpleName());
            }
        }
    }

    public BookingTypeHandler handlerFor(BookingType type) {
        BookingTypeHandler handler = handlers.get(type);
        if (handler == null) {
            throw new ValidationException("Booking type " + type + " is not supported yet");
        }
        return handler;
    }
}
