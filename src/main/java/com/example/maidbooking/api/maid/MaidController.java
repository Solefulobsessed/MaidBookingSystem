package com.example.maidbooking.api.maid;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.supply.Maid;
import com.example.maidbooking.supply.MaidService;

@RestController
@RequestMapping("/api/v1/maids")
public class MaidController {

    private final MaidService maidService;

    public MaidController(MaidService maidService) {
        this.maidService = maidService;
    }

    @PostMapping
    public ResponseEntity<MaidResponse> onboard(@Valid @RequestBody OnboardMaidRequest request) {
        Maid maid = maidService.onboard(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/maids/" + maid.id())).body(MaidResponse.from(maid));
    }

    @GetMapping("/{maidId}")
    public MaidResponse get(@PathVariable String maidId) {
        return MaidResponse.from(maidService.get(maidId));
    }
}
