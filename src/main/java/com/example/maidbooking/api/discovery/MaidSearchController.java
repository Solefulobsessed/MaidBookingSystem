package com.example.maidbooking.api.discovery;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.maidbooking.discovery.MaidSearchResult;
import com.example.maidbooking.discovery.MaidSearchService;
import com.example.maidbooking.supply.ServiceType;

/** {@code GET /api/v1/maids/search}: every query parameter not listed below is treated as a filter. */
@RestController
public class MaidSearchController {

    private static final Set<String> SEARCH_PARAMS = Set.of("locality", "serviceType", "start", "durationMinutes",
            "page", "size");

    private final MaidSearchService searchService;

    public MaidSearchController(MaidSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/v1/maids/search")
    public MaidSearchResult search(@RequestParam String locality, @RequestParam ServiceType serviceType,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                   @RequestParam int durationMinutes,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam Map<String, String> allParams) {
        Map<String, String> filters = new HashMap<>(allParams);
        filters.keySet().removeAll(SEARCH_PARAMS);
        return searchService.search(locality, serviceType, start, durationMinutes, filters, page, size);
    }
}
