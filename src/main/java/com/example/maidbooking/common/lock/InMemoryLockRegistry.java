package com.example.maidbooking.common.lock;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/** One {@link ReentrantLock} per key, plus a per-thread check that enforces the lock order. */
@Component
public class InMemoryLockRegistry implements LockRegistry {

    private final Map<LockKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<LockKey>> held = ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public <T> T callWith(LockKey key, Supplier<T> action) {
        checkOrder(key);
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        held.get().push(key);
        try {
            return action.get();
        } finally {
            held.get().pop();
            lock.unlock();
        }
    }

    @Override
    public <T> T callWithAll(Collection<LockKey> keys, Supplier<T> action) {
        List<LockKey> ordered = keys.stream().distinct().sorted().toList();
        return acquireFrom(ordered, 0, action);
    }

    private <T> T acquireFrom(List<LockKey> ordered, int index, Supplier<T> action) {
        if (index == ordered.size()) {
            return action.get();
        }
        return callWith(ordered.get(index), () -> acquireFrom(ordered, index + 1, action));
    }

    private void checkOrder(LockKey key) {
        Deque<LockKey> current = held.get();
        if (current.contains(key)) {
            return;
        }
        LockKey latest = current.peek();
        if (latest != null && key.compareTo(latest) < 0) {
            throw new IllegalStateException("Lock order violation: " + key + " requested while holding " + latest);
        }
    }
}
