package com.example.maidbooking.supply;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.maidbooking.zone.Zone;
import com.example.maidbooking.zone.ZoneRepository;

/**
 * Demo data, loaded at startup with the {@code demo} profile (the default): three Bangalore zones, twelve maids
 * with weekly hours, and their shifts for the next 14 days, which fill the capacity ledger.
 */
@Component
@Profile("demo")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);
    /** Two weeks, so a recurring booking's first 14-day window has capacity. */
    private static final int DAYS = 14;

    private record SeedMaid(String id, String name, Gender gender, String zoneId, Tier tier, double rating,
                            int fromHour, int toHour, ServiceType... skills) {
    }

    private static final List<Zone> ZONES = List.of(
            new Zone("kor-5", "Koramangala", Set.of("koramangala", "koramangala 5th block", "st johns road"),
                    Set.of("hsr-2", "ind-1")),
            new Zone("hsr-2", "HSR Layout", Set.of("hsr layout", "hsr sector 2", "agara"), Set.of("kor-5")),
            new Zone("ind-1", "Indiranagar", Set.of("indiranagar", "domlur", "hal 2nd stage"), Set.of("kor-5")));

    private static final List<SeedMaid> MAIDS = List.of(
            new SeedMaid("m-lakshmi", "Lakshmi R", Gender.FEMALE, "kor-5", Tier.PREMIUM, 4.8, 7, 15,
                    ServiceType.CLEANING, ServiceType.COOKING),
            new SeedMaid("m-asha", "Asha K", Gender.FEMALE, "kor-5", Tier.STANDARD, 4.4, 7, 13, ServiceType.CLEANING),
            new SeedMaid("m-ravi", "Ravi S", Gender.MALE, "kor-5", Tier.STANDARD, 4.1, 12, 20,
                    ServiceType.CLEANING, ServiceType.DISHWASHING, ServiceType.LAUNDRY),
            new SeedMaid("m-meena", "Meena P", Gender.FEMALE, "kor-5", Tier.PREMIUM, 4.9, 9, 17,
                    ServiceType.BABYSITTING, ServiceType.COOKING),
            new SeedMaid("m-sunita", "Sunita D", Gender.FEMALE, "hsr-2", Tier.STANDARD, 4.3, 7, 15,
                    ServiceType.CLEANING, ServiceType.LAUNDRY),
            new SeedMaid("m-kavya", "Kavya N", Gender.FEMALE, "hsr-2", Tier.PREMIUM, 4.7, 8, 16,
                    ServiceType.COOKING, ServiceType.CLEANING),
            new SeedMaid("m-imran", "Imran A", Gender.MALE, "hsr-2", Tier.STANDARD, 3.9, 12, 20,
                    ServiceType.DISHWASHING, ServiceType.CLEANING),
            new SeedMaid("m-geetha", "Geetha M", Gender.FEMALE, "hsr-2", Tier.STANDARD, 0.0, 7, 13,
                    ServiceType.CLEANING),
            new SeedMaid("m-priya", "Priya V", Gender.FEMALE, "ind-1", Tier.PREMIUM, 4.6, 7, 15,
                    ServiceType.CLEANING, ServiceType.COOKING, ServiceType.BABYSITTING),
            new SeedMaid("m-rekha", "Rekha T", Gender.FEMALE, "ind-1", Tier.STANDARD, 4.2, 8, 16,
                    ServiceType.CLEANING, ServiceType.DISHWASHING),
            new SeedMaid("m-anil", "Anil B", Gender.MALE, "ind-1", Tier.STANDARD, 4.0, 12, 20,
                    ServiceType.LAUNDRY, ServiceType.CLEANING),
            new SeedMaid("m-fatima", "Fatima S", Gender.FEMALE, "ind-1", Tier.PREMIUM, 4.5, 9, 17,
                    ServiceType.COOKING));

    private final ZoneRepository zones;
    private final MaidRepository maids;
    private final ShiftService shiftService;
    private final Clock clock;

    public SeedDataLoader(ZoneRepository zones, MaidRepository maids, ShiftService shiftService, Clock clock) {
        this.zones = zones;
        this.maids = maids;
        this.shiftService = shiftService;
        this.clock = clock;
    }

    /** Maids work Monday to Saturday; ids are readable (e.g. {@code m-lakshmi}) so the demo is easy to follow. */
    @Override
    public void run(ApplicationArguments args) {
        ZONES.forEach(zones::save);
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < MAIDS.size(); i++) {
            SeedMaid seed = MAIDS.get(i);
            Maid maid = new Maid(seed.id(), seed.name(), seed.gender(), "98450000" + String.format("%02d", i),
                    Set.copyOf(Arrays.asList(seed.skills())), Set.of(), seed.tier(), seed.zoneId(), seed.rating());
            maid.activate();
            maids.save(maid);
            shiftService.setTemplate(maid.id(), Arrays.stream(DayOfWeek.values())
                    .filter(day -> day != DayOfWeek.SUNDAY)
                    .map(day -> new ShiftTemplate(day, seed.zoneId(), LocalTime.of(seed.fromHour(), 0),
                            LocalTime.of(seed.toHour(), 0)))
                    .toList());
            shiftService.generateShifts(maid.id(), today, today.plusDays(DAYS - 1L));
        }
        log.info("Demo data loaded: {} zones, {} maids, shifts {} to {}", ZONES.size(), MAIDS.size(), today,
                today.plusDays(DAYS - 1L));
    }
}
