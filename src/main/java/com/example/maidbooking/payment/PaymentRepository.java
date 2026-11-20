package com.example.maidbooking.payment;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.maidbooking.common.repository.InMemoryRepository;

@Repository
public class PaymentRepository extends InMemoryRepository<Payment, String> {

    public PaymentRepository() {
        super("Payment", Payment::id);
    }

    public List<Payment> findByJob(String jobId) {
        return findAllMatching(payment -> payment.jobId().equals(jobId));
    }
}
