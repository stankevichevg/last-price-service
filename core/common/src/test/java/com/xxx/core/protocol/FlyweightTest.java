package com.xxx.core.protocol;

import org.agrona.DirectBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class FlyweightTest {

    private static final TestFlyweight TEST_FLYWEIGHT = new TestFlyweight();

    @BeforeAll
    public static void init() {
        Flyweight.allocateMemoryForFlyweight(TEST_FLYWEIGHT);
    }

    @Test
    public void allocatedRightAmountOfMemoryTest() {
        assertThat(TEST_FLYWEIGHT.getReadBuffer().capacity(), is(SIZE_OF_LONG));
        assertThat(TEST_FLYWEIGHT.getOffset(), is(0));
    }

    @Test
    public void whenWrappedForReadThenWriteBufferIsNull() {
        final DirectBuffer buffer = TEST_FLYWEIGHT.getReadBuffer();
        TEST_FLYWEIGHT.wrapForRead(buffer, 0);
        assertThat(TEST_FLYWEIGHT.writeBuffer, is(nullValue()));
    }

    private static class TestFlyweight extends Flyweight {
        @Override
        public int sizeInBytes() {
            return SIZE_OF_LONG;
        }
    }

}
