package com.example.maidbooking.booking.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.booking.BookingType;
import com.example.maidbooking.common.error.ValidationException;

class BookingTypeRegistryTest {

    private final ScheduledBookingHandler scheduled = new ScheduledBookingHandler(120, 30);

    @Test
    void findsTheHandlerForItsType() {
        BookingTypeRegistry registry = new BookingTypeRegistry(List.of(scheduled));

        assertThat(registry.handlerFor(BookingType.SCHEDULED)).isSameAs(scheduled);
    }

    @Test
    void aTypeWithoutAHandlerIsRejectedAsInvalidInput() {
        BookingTypeRegistry registry = new BookingTypeRegistry(List.of(scheduled));

        assertThatThrownBy(() -> registry.handlerFor(BookingType.RECURRING))
                .isInstanceOf(ValidationException.class).hasMessageContaining("RECURRING");
    }

    @Test
    void twoHandlersForOneTypeFailAtStartup() {
        assertThatThrownBy(() -> new BookingTypeRegistry(List.of(scheduled, new ScheduledBookingHandler(60, 7))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SCHEDULED");
    }
}
