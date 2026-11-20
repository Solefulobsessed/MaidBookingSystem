package com.example.maidbooking.discovery;

import com.example.maidbooking.common.error.ValidationException;

/** The one rule for how long a job may be: 30 to 240 minutes in 30-minute steps. */
public final class JobDurations {

    public static final int MIN_MINUTES = 30;
    public static final int MAX_MINUTES = 240;
    public static final int STEP_MINUTES = 30;

    private JobDurations() {
    }

    public static void validate(int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % STEP_MINUTES != 0) {
            throw new ValidationException("Duration must be 30 to 240 minutes in 30-minute steps, was " + minutes);
        }
    }
}
