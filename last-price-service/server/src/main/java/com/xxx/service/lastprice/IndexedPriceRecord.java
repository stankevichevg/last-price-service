package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static com.xxx.service.lastprice.Configuration.PRICE_MAX_PAYLOAD_SIZE;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

/**
 * Represents price record stored in {@link BaseIndexedPriceRecordsBlock}.
 * Size of this structure is static per runtime. Variational payload size can be defined by system property
 * {@link Configuration#PRICE_MAX_PAYLOAD_SIZE}.
 * Trying to write payload of bigger size will cause {@link IllegalArgumentException}.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class IndexedPriceRecord extends Flyweight {

    public static final int INDEX_FIELD_OFFSET = 0;
    public static final int TIMESTAMP_FIELD_OFFSET = INDEX_FIELD_OFFSET + SIZE_OF_INT;
    public static final int PAYLOAD_SIZE_FIELD_OFFSET = TIMESTAMP_FIELD_OFFSET + SIZE_OF_LONG;
    public static final int PAYLOAD_FIELD_OFFSET = PAYLOAD_SIZE_FIELD_OFFSET + SIZE_OF_INT;
    public static final int MESSAGE_SIZE = PAYLOAD_FIELD_OFFSET + PRICE_MAX_PAYLOAD_SIZE;

    public int index() {
        return readBuffer.getInt(offset + INDEX_FIELD_OFFSET);
    }

    void index(int index) {
        writeBuffer.putInt(offset + INDEX_FIELD_OFFSET, index);
    }

    public long timestamp() {
        return readBuffer.getLong(offset + TIMESTAMP_FIELD_OFFSET);
    }

    void timestamp(long timestamp) {
        writeBuffer.putLong(offset + TIMESTAMP_FIELD_OFFSET, timestamp);
    }

    public int payloadSize() {
        return readBuffer.getInt(offset + PAYLOAD_SIZE_FIELD_OFFSET);
    }

    private void payloadSize(int payloadSize) {
        if (payloadSize > PRICE_MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size can not be more than: " + PRICE_MAX_PAYLOAD_SIZE);
        }
        writeBuffer.putInt(offset + PAYLOAD_SIZE_FIELD_OFFSET, payloadSize);
    }

    public void getPayload(MutableDirectBuffer writeTo, int writeOffset) {
        writeTo.putBytes(writeOffset, readBuffer, offset + PAYLOAD_FIELD_OFFSET, payloadSize());
    }

    public int absolutePayloadOffset() {
        return offset + PAYLOAD_FIELD_OFFSET;
    }

    void putPayload(DirectBuffer readFrom, int readOffset, int payloadSize) {
        payloadSize(payloadSize);
        writeBuffer.putBytes(offset + PAYLOAD_FIELD_OFFSET, readFrom, readOffset, payloadSize);
    }

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

}
