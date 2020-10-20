package com.xxx.core.client.pool;


import org.agrona.collections.IntArrayQueue;
import java.util.function.IntFunction;

/**
 * Fixed size object pool. This pool implementation is not thread safe, external synchronization should be used.
 *
 * @param <T> type of objects in the pool
 */
public class FixSizeObjectPool<T extends PoolInstance> implements ObjectPool<T> {

    private final Object[] instances;
    private final IntArrayQueue freeInstances;

    public FixSizeObjectPool(final int size, IntFunction<T> instanceFactory) {
        instances = new Object[size];
        freeInstances = new IntArrayQueue(-1);
        for (int i = 0; i < size; i++) {
            final T newInstance = instanceFactory.apply(i);
            instances[i] = newInstance;
            freeInstances.addInt(i);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getInstance(int id) {
        checkIdBounds(id);
        return (T) instances[id];
    }

    @SuppressWarnings("unchecked")
    @Override
    public T acquireInstance() {
        final int freeInstanceIndex = freeInstances.pollInt();
        if (freeInstanceIndex == -1) {
            return null;
        }
        return (T) instances[freeInstanceIndex];
    }

    @Override
    public void freeInstance(T instance) {
        final int instanceId = instance.getId();
        checkIdBounds(instanceId);

    }

    private void checkIdBounds(final int id) {
        if (id < 0 || id >= instances.length) {
            throw new IllegalArgumentException("Instance id is out of pool bounds: " + id);
        }
    }

}
