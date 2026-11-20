package com.example.maidbooking.api.payment;

import com.example.maidbooking.booking.JobStatus;
import com.example.maidbooking.payment.PaymentReceipt;
import com.example.maidbooking.payment.PaymentStatus;

/** {@code maid} is null while the job is CONFIRMED and still waiting for one. */
public record PaymentResponse(String paymentId, PaymentStatus status, String jobId, JobStatus jobStatus,
                              MaidView maid, String otp) {

    public record MaidView(String id, String name, double rating) {
    }

    static PaymentResponse from(PaymentReceipt receipt) {
        MaidView maid = receipt.maidId() == null ? null
                : new MaidView(receipt.maidId(), receipt.maidName(), receipt.maidRating());
        return new PaymentResponse(receipt.paymentId(), receipt.status(), receipt.jobId(), receipt.jobStatus(), maid,
                receipt.otp());
    }
}
