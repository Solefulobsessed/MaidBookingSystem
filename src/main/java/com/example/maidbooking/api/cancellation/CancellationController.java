package com.example.maidbooking.api.cancellation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.cancellation.CancellationService;
import com.example.maidbooking.cancellation.CancelledBy;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}")
public class CancellationController {

    private final CancellationService cancellationService;

    public CancellationController(CancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @GetMapping("/cancellation-quote")
    public CancellationResponse quote(@PathVariable String jobId) {
        return CancellationResponse.quote(jobId, cancellationService.quote(jobId));
    }

    @PostMapping("/cancel")
    public CancellationResponse cancel(@PathVariable String jobId) {
        return CancellationResponse.from(cancellationService.cancelJob(jobId, CancelledBy.CUSTOMER));
    }
}
