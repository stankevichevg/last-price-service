package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static com.xxx.service.lastprice.Configuration.PRICE_MAX_PAYLOAD_SIZE;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class IndexedPriceRecordTest {

    @Test
    public void shouldWriteAndReadIndexCorrectly() {
        final IndexedPriceRecord priceRecord = new IndexedPriceRecord();
        Flyweight.allocateMemoryForFlyweight(priceRecord);
        final int index = 12345;
        priceRecord.index(index);
        assertThat(priceRecord.index(), is(index));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionIfWrongPayloadSize() {
        final IndexedPriceRecord priceRecord = new IndexedPriceRecord();
        Flyweight.allocateMemoryForFlyweight(priceRecord);
        final DirectBuffer buffer = new UnsafeBuffer(allocateDirectAligned(PRICE_MAX_PAYLOAD_SIZE + 1, CACHE_LINE_LENGTH));
        assertThrows(IllegalArgumentException.class, () -> {
            priceRecord.putPayload(buffer, 0, PRICE_MAX_PAYLOAD_SIZE + 1);
        });
    }

}
