package com.example.maidbooking.supply;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.zone.ZoneRepository;

@Service
public class MaidService {

    private final MaidRepository maids;
    private final ZoneRepository zones;

    public MaidService(MaidRepository maids, ZoneRepository zones) {
        this.maids = maids;
        this.zones = zones;
    }

    /** Creates the maid and activates her. New maids start unrated (rating 0). */
    public Maid onboard(OnboardMaidCommand command) {
        if (command.homeZoneId() == null || !zones.existsById(command.homeZoneId())) {
            throw new ValidationException("Unknown home zone '" + command.homeZoneId() + "'");
        }
        Maid maid = new Maid(newId(), command.name(), command.gender(), command.phone(), command.skills(),
                command.tags(), command.tier(), command.homeZoneId(), 0.0);
        if (maids.existsWithPhone(maid.phone())) {
            throw new ValidationException("A maid with phone " + maid.phone() + " is already registered");
        }
        maid.activate();
        return maids.save(maid);
    }

    public Maid get(String maidId) {
        return maids.getById(maidId);
    }

    private static String newId() {
        return "m-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
