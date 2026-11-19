package com.example.maidbooking.common.repository;

import java.util.List;
import java.util.Optional;

import com.example.maidbooking.common.error.NotFoundException;

/** Storage contract for one aggregate type. In-memory now; swappable for a database later. */
public interface Repository<T, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    boolean existsById(ID id);

    void deleteById(ID id);

    /** Name used in "not found" errors, e.g. "Maid". */
    String entityName();

    default T getById(ID id) {
        return findById(id).orElseThrow(() -> new NotFoundException(entityName(), id));
    }
}
