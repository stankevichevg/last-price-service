package com.xxx.core.client.pool;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class FixSizeObjectPoolTest {

    private static final int POOL_SIZE = 5;

    @Test
    public void whenThereIsNoInstancesThenReturnNull() {
        final ObjectPool<TestPoolInstance> pool = new FixSizeObjectPool<>(POOL_SIZE, TestPoolInstance::new);
        for (int i = 0; i < POOL_SIZE; i++) {
            assertThat(pool.acquireInstance(), is(notNullValue()));
        }
        assertThat(pool.acquireInstance(), is(nullValue()));
    }

    @Test
    public void whenInstanceIsReturnedThenItCanBeAcquiredAgain() {
        final ObjectPool<TestPoolInstance> pool = new FixSizeObjectPool<>(1, TestPoolInstance::new);
        final TestPoolInstance instance = pool.acquireInstance();
        assertThat(pool.acquireInstance(), is(nullValue()));
        pool.freeInstance(instance);
        final TestPoolInstance reusedInstance = pool.acquireInstance();
        assertThat(reusedInstance, is(equalTo(instance)));
    }

    @Test
    public void whenRequestedByIndexThenIllegalArgumentException() {
        final ObjectPool<TestPoolInstance> pool = new FixSizeObjectPool<>(1, TestPoolInstance::new);
        final TestPoolInstance instance = pool.acquireInstance();
        assertThat(pool.getInstance(0), is(equalTo(instance)));
    }

    @Test
    public void whenRequestedWrongIndexThenIllegalArgumentException() {
        final ObjectPool<TestPoolInstance> pool = new FixSizeObjectPool<>(1, TestPoolInstance::new);
        assertThrows(IllegalArgumentException.class, () -> {
            pool.getInstance(1);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            pool.getInstance(-1);
        });
    }

    private static class TestPoolInstance implements PoolInstance {

        private final int id;

        public TestPoolInstance(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }
    }
}
