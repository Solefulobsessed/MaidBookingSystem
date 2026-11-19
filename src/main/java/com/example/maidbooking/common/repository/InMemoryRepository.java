package com.example.maidbooking.common.repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** Thread-safe map-backed repository. Subclasses supply the entity name and how to read its id. */
public abstract class InMemoryRepository<T, ID> implements Repository<T, ID> {

    private final Map<ID, T> store = new ConcurrentHashMap<>();
    private final String entityName;
    private final Function<T, ID> idOf;

    protected InMemoryRepository(String entityName, Function<T, ID> idOf) {
        this.entityName = entityName;
        this.idOf = idOf;
    }

    @Override
    public T save(T entity) {
        store.put(Objects.requireNonNull(idOf.apply(entity), "id"), entity);
        return entity;
    }

    @Override
    public Optional<T> findById(ID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<T> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public boolean existsById(ID id) {
        return store.containsKey(id);
    }

    @Override
    public void deleteById(ID id) {
        store.remove(id);
    }

    @Override
    public String entityName() {
        return entityName;
    }

    /** For subclass finders such as "shifts of a maid on a date". */
    protected List<T> findAllMatching(Predicate<T> filter) {
        return store.values().stream().filter(filter).toList();
    }
}
