package com.example.maidbooking.api.maid;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.supply.LiveStateService;
import com.example.maidbooking.supply.LiveStatus;
import com.example.maidbooking.supply.MaidLiveState;

@RestController
@RequestMapping("/api/v1/maids/{maidId}/live-state")
public class LiveStateController {

    public record LiveStateRequest(@NotNull LiveStatus status) {
    }

    public record LiveStateResponse(String maidId, LiveStatus status, String zone, LocalDateTime updatedAt) {
        static LiveStateResponse from(MaidLiveState state) {
            return new LiveStateResponse(state.maidId(), state.status(), state.zoneId(), state.updatedAt());
        }
    }

    private final LiveStateService liveStateService;

    public LiveStateController(LiveStateService liveStateService) {
        this.liveStateService = liveStateService;
    }

    @PutMapping
    public LiveStateResponse change(@PathVariable String maidId, @Valid @RequestBody LiveStateRequest request) {
        return LiveStateResponse.from(liveStateService.changeStatus(maidId, request.status()));
    }

    @GetMapping
    public LiveStateResponse get(@PathVariable String maidId) {
        return LiveStateResponse.from(liveStateService.get(maidId));
    }
}
