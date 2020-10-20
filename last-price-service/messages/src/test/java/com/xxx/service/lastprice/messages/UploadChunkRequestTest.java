package com.xxx.service.lastprice.messages;

import com.xxx.core.protocol.Flyweight;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static com.xxx.service.lastprice.messages.PriceRecordsChunk.CHUNK_HEADER_SIZE;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class UploadChunkRequestTest {

    private static final MutableDirectBuffer PAYLOAD_BUFFER;
    private static final UploadChunkRequest UPLOAD_CHUNK_REQUEST = new UploadChunkRequest();
    private static final PriceRecordsChunk PRICE_RECORDS_CHUNK = new PriceRecordsChunk();

    static {
        PAYLOAD_BUFFER = new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));
        Flyweight.allocateMemoryForFlyweight(UPLOAD_CHUNK_REQUEST, UploadChunkRequest.maxSize());
        Flyweight.allocateMemoryForFlyweight(PRICE_RECORDS_CHUNK, PriceRecordsChunk.defineSize(1000));
    }

    @Test
    public void whenPrepareChunkThenReadItCorrectly() {
        final PriceRecordsChunk chunk = UPLOAD_CHUNK_REQUEST.getChunk();
        PriceRecordChunkTest.whenAddedRecordsToChunkThenReadThemCorrectly(chunk);
    }

    @Test
    public void whenPutAndGetChunkThenReadItCorrectly() {
        PRICE_RECORDS_CHUNK.reset();
        PAYLOAD_BUFFER.putInt(0, 1);
        PRICE_RECORDS_CHUNK.addRecord("AIR", 100500, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        PAYLOAD_BUFFER.putInt(0, 2);
        PRICE_RECORDS_CHUNK.addRecord("SAF", 100501, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        UPLOAD_CHUNK_REQUEST.putChunk(PRICE_RECORDS_CHUNK);
        final PriceRecordsChunk chunk = UPLOAD_CHUNK_REQUEST.getChunk();
        final PriceRecord airRecord = chunk.priceRecord(0);
        assertThat("AIR".contentEquals(airRecord.instrument()), is(true));
        assertThat(airRecord.asOfTimestamp(), is(100500L));
        final PriceRecord safRecord = chunk.priceRecord(1);
        assertThat("SAF".contentEquals(airRecord.instrument()), is(true));
        assertThat(airRecord.asOfTimestamp(), is(100501L));
        assertThat(chunk.sizeInBytes(), is(CHUNK_HEADER_SIZE + 2 * PriceRecord.SIZE_IN_BYTES));
    }

    @Test
    public void whenBatchIdIsWrittenThenReadItCorrectly() {
        UPLOAD_CHUNK_REQUEST.batchId(100500L);
        assertThat(UPLOAD_CHUNK_REQUEST.batchId(), is(100500L));
    }

}
