package com.example.maidbooking.common.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.maidbooking.common.error.NotFoundException;

class InMemoryRepositoryTest {

    record Thing(String id, String colour) {
    }

    static class ThingRepository extends InMemoryRepository<Thing, String> {
        ThingRepository() {
            super("Thing", Thing::id);
        }

        List<Thing> findByColour(String colour) {
            return findAllMatching(t -> t.colour().equals(colour));
        }
    }

    private final ThingRepository repo = new ThingRepository();

    @Test
    void saveThenFindAndReplaceById() {
        repo.save(new Thing("t-1", "red"));
        repo.save(new Thing("t-1", "blue"));

        assertThat(repo.findById("t-1")).contains(new Thing("t-1", "blue"));
        assertThat(repo.findAll()).hasSize(1);
        assertThat(repo.existsById("t-1")).isTrue();
    }

    @Test
    void getByIdThrowsNotFoundWithEntityName() {
        assertThatThrownBy(() -> repo.getById("missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Thing missing not found");
    }

    @Test
    void deleteRemovesEntity() {
        repo.save(new Thing("t-1", "red"));
        repo.deleteById("t-1");

        assertThat(repo.findById("t-1")).isEmpty();
    }

    @Test
    void findAllMatchingFiltersAndFindAllIsASnapshot() {
        repo.save(new Thing("t-1", "red"));
        repo.save(new Thing("t-2", "blue"));
        List<Thing> snapshot = repo.findAll();
        repo.save(new Thing("t-3", "red"));

        assertThat(repo.findByColour("red")).extracting(Thing::id).containsExactlyInAnyOrder("t-1", "t-3");
        assertThat(snapshot).hasSize(2);
    }

    @Test
    void rejectsEntityWithoutId() {
        assertThatThrownBy(() -> repo.save(new Thing(null, "red"))).isInstanceOf(NullPointerException.class);
    }
}
