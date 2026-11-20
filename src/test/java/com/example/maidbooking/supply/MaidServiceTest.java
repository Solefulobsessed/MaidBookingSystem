package com.example.maidbooking.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.NotFoundException;
import com.example.maidbooking.common.error.ValidationException;
import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

class MaidServiceTest {

    private final MaidRepository maids = new MaidRepository();
    private final ZoneRepository zones = new ZoneRepository();
    private final MaidService service = new MaidService(maids, zones);

    @BeforeEach
    void seedZone() {
        zones.save(new Zone("kor-5", "Koramangala", Set.of("koramangala"), Set.of()));
    }

    private static OnboardMaidCommand command(String phone, String zone) {
        return new OnboardMaidCommand("Lakshmi R", Gender.FEMALE, phone, zone,
                Set.of(ServiceType.CLEANING, ServiceType.COOKING), Set.of("deep-cleaning"), Tier.PREMIUM);
    }

    @Test
    void onboardCreatesAnActiveUnratedMaid() {
        Maid maid = service.onboard(command("9876543210", "kor-5"));

        assertThat(maid.id()).startsWith("m-").hasSize(10);
        assertThat(maid.status()).isEqualTo(MaidStatus.ACTIVE);
        assertThat(maid.rating()).isZero();
        assertThat(service.get(maid.id())).isSameAs(maid);
    }

    @Test
    void unknownHomeZoneIsRejected() {
        assertThatThrownBy(() -> service.onboard(command("9876543210", "nowhere")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("nowhere");
        assertThat(maids.findAll()).isEmpty();
    }

    @Test
    void samePhoneCannotBeRegisteredTwiceEvenWithCountryCode() {
        service.onboard(command("9876543210", "kor-5"));

        assertThatThrownBy(() -> service.onboard(command("+919876543210", "kor-5")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("already registered");
        assertThat(maids.findAll()).hasSize(1);
    }

    @Test
    void getUnknownMaidIsNotFound() {
        assertThatThrownBy(() -> service.get("m-missing")).isInstanceOf(NotFoundException.class);
    }
}
