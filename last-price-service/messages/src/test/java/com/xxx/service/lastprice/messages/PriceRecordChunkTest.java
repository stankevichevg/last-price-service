package com.xxx.service.lastprice.messages;

import com.xxx.core.protocol.Flyweight;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static com.xxx.service.lastprice.Configuration.MAX_CHUNK_SIZE;
import static com.xxx.service.lastprice.messages.PriceRecordsChunk.CHUNK_HEADER_SIZE;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class PriceRecordChunkTest {

    private static final MutableDirectBuffer PAYLOAD_BUFFER;
    private static final PriceRecordsChunk PRICE_RECORDS_CHUNK = new PriceRecordsChunk();

    static {
        PAYLOAD_BUFFER = new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));
        Flyweight.allocateMemoryForFlyweight(PRICE_RECORDS_CHUNK, PriceRecordsChunk.defineSize(1000));
    }

    @Test
    public void whenResetIsCalledThenSizeRecordsNumberReset() {
        PRICE_RECORDS_CHUNK.reset();
        PAYLOAD_BUFFER.putInt(0, 123);
        PRICE_RECORDS_CHUNK.addRecord("AIR", 100500, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        assertThat(PRICE_RECORDS_CHUNK.recordsNumber(), is(1));
        PRICE_RECORDS_CHUNK.reset();
        assertThat(PRICE_RECORDS_CHUNK.recordsNumber(), is(0));
    }

    @Test
    public void whenAddedRecordsToChunkThenReadThemCorrectly() {
        whenAddedRecordsToChunkThenReadThemCorrectly(PRICE_RECORDS_CHUNK);
    }

    public static void whenAddedRecordsToChunkThenReadThemCorrectly(PriceRecordsChunk chunk) {
        chunk.reset();
        PAYLOAD_BUFFER.putInt(0, 100);
        chunk.addRecord("AIR", 100500, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        PAYLOAD_BUFFER.putInt(0, 200);
        chunk.addRecord("SAF", 100501, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        assertThat(chunk.recordsNumber(), is(2));
        final PriceRecord airRecord = chunk.priceRecord(0);
        assertThat("AIR".contentEquals(airRecord.instrument()), is(true));
        assertThat(airRecord.asOfTimestamp(), is(100500L));
        airRecord.getPayload(PAYLOAD_BUFFER, 0);
        assertThat(PAYLOAD_BUFFER.getInt(0), is(100));
        final PriceRecord safRecord = chunk.priceRecord(1);
        assertThat("SAF".contentEquals(safRecord.instrument()), is(true));
        assertThat(safRecord.asOfTimestamp(), is(100501L));
        safRecord.getPayload(PAYLOAD_BUFFER, 0);
        assertThat(PAYLOAD_BUFFER.getInt(0), is(200));
        assertThat(chunk.sizeInBytes(), is(CHUNK_HEADER_SIZE + 2 * PriceRecord.SIZE_IN_BYTES));
    }

    @Test
    public void whenTryToSaveMoreThenMaxChunkSizeThenException() {
        PAYLOAD_BUFFER.putInt(0, 1);
        for (int i = 0; i < MAX_CHUNK_SIZE; i++) {
            PRICE_RECORDS_CHUNK.addRecord("AIR", 100500, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        }
        assertThrows(IllegalStateException.class, () -> {
            PRICE_RECORDS_CHUNK.addRecord("AIR", 100500, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        });
    }

}
