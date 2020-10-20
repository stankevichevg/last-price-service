package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BatchRunTest {

    private static final int NUMBER_OF_INSTRUMENTS = 10;

    private static final MutableDirectBuffer PAYLOAD_BUFFER;
    private static final BatchRun BATCH_RUN = new BatchRun(0, NUMBER_OF_INSTRUMENTS);

    static {
        PAYLOAD_BUFFER = new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));
        Flyweight.allocateMemoryForFlyweight(BATCH_RUN);
    }

    @Test
    public void whenWriteRecordsThenReadItCorrectly() {
        BATCH_RUN.reset();
        for (int i = 0; i < NUMBER_OF_INSTRUMENTS; i++) {
            PAYLOAD_BUFFER.putInt(0, i);
            BATCH_RUN.tryUpdateRecord(i, 100500L + i, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        }
    }

}
