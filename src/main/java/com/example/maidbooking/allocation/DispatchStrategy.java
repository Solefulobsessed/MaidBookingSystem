package com.example.maidbooking.allocation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.example.maidbooking.discovery.filter.MaidFilter;
import com.example.maidbooking.supply.ServiceType;

/** Who can be sent right now for an instant job, best first. Swap the bean for e.g. offer-and-accept. */
public interface DispatchStrategy {

    List<DispatchOption> options(String zoneId, ServiceType service, Duration duration, MaidFilter constraints,
                                 LocalDateTime now);
}
