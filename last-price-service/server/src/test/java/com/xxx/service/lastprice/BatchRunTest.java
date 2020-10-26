package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.xxx.service.lastprice.BatchRun.BATCH_RUN_HEADER_SIZE;
import static java.util.Objects.hash;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BatchRunTest {

    private static final int BATCH_RUN_INDEX = 100500;
    private static final int NUMBER_OF_INSTRUMENTS = 10;

    private static final MutableDirectBuffer PAYLOAD_BUFFER;
    private static final BatchRun BATCH_RUN = new BatchRun(BATCH_RUN_INDEX, NUMBER_OF_INSTRUMENTS);

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

    @Test
    public void shouldReturnAssignedId() {
        assertThat(BATCH_RUN.getIndex(), is(BATCH_RUN_INDEX));
    }

    @Test
    public void shouldMergeToRecordsBlockCorrectly() {
        BATCH_RUN.reset();
        for (int i = 0; i < NUMBER_OF_INSTRUMENTS; i++) {
            PAYLOAD_BUFFER.putInt(0, i);
            BATCH_RUN.tryUpdateRecord(i, 100500L + i, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        }
        final MarketState marketState = new MarketState(NUMBER_OF_INSTRUMENTS);
        Flyweight.allocateMemoryForFlyweight(marketState);
        BATCH_RUN.mergeTo(marketState);
        for (int i = 0; i < NUMBER_OF_INSTRUMENTS; i++) {
            final IndexedPriceRecord record = marketState.getPriceRecord(i);
            record.getPayload(PAYLOAD_BUFFER, 0);
            assertThat(record.timestamp(), is(100500L + i));
            assertThat(PAYLOAD_BUFFER.getInt(0), is(i));
        }
    }

    @Test
    public void shouldWriteAndReadIdCorrectly() {
        final long id = 1234L;
        BATCH_RUN.id(id);
        assertThat(BATCH_RUN.id(), is(id));
    }

    @Test
    public void shouldWriteAndReadLastUpdateTimestampCorrectly() {
        final long lastUpdateTimestamp = 1234L;
        BATCH_RUN.lastUpdateTimestamp(lastUpdateTimestamp);
        assertThat(BATCH_RUN.lastUpdateTimestamp(), is(lastUpdateTimestamp));
    }

    @Test
    public void shouldImplementEqualsAndHashcodeCorrectly() {
        assertThat(BATCH_RUN, is(equalTo(new BatchRun(BATCH_RUN_INDEX, NUMBER_OF_INSTRUMENTS))));
        assertThat(BATCH_RUN.hashCode(), is(hash(BATCH_RUN_INDEX)));
    }

    @Test
    public void shouldThrowIndexOutOfBoundsExceptionIfRecordRequestedByWrongIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            BATCH_RUN.getPriceRecord(NUMBER_OF_INSTRUMENTS);
        });
    }

}
