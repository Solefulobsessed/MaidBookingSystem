package com.example.maidbooking.payment;

import com.example.maidbooking.booking.JobStatus;

/**
 * What a successful payment returned, captured at that moment so a replayed request gets the same answer.
 * The maid fields are null while the job still waits for one.
 */
public record PaymentReceipt(String paymentId, PaymentStatus status, String jobId, JobStatus jobStatus,
                             String maidId, String maidName, Double maidRating, String otp) {
}
