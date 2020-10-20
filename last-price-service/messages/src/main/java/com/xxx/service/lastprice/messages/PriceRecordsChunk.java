package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Flyweight;
import org.agrona.DirectBuffer;

import static com.xxx.service.lastprice.Configuration.MAX_CHUNK_SIZE;
import static org.agrona.BitUtil.SIZE_OF_INT;

public class PriceRecordsChunk extends Flyweight {

    public static final int RECORDS_NUMBER_FIELD_OFFSET = 0;
    public static final int CHUNK_HEADER_SIZE = RECORDS_NUMBER_FIELD_OFFSET + SIZE_OF_INT;

    private final PriceRecord priceRecord = new PriceRecord();

    public void reset() {
        recordsNumber(0);
    }

    public int recordsNumber() {
        return readBuffer.getInt(offset + RECORDS_NUMBER_FIELD_OFFSET);
    }

    private void recordsNumber(int recordsNumber) {
        writeBuffer.putInt(offset + RECORDS_NUMBER_FIELD_OFFSET, recordsNumber);
    }

    public void addRecord(CharSequence instrument, long asOfTimestamp, DirectBuffer payload, int pOffset, int pSize) {
        checkChunkIsNotFull();
        final int index = recordsNumber();
        recordsNumber(index + 1);
        priceRecord.wrapForWrite(writeBuffer, offset + CHUNK_HEADER_SIZE + index * PriceRecord.SIZE_IN_BYTES);
        priceRecord.instrument(instrument);
        priceRecord.asOfTimestamp(asOfTimestamp);
        priceRecord.putPayload(payload, pOffset, pSize);
    }

    public PriceRecord priceRecord(int index) {
        priceRecord.wrapForRead(readBuffer, offset + CHUNK_HEADER_SIZE + index * PriceRecord.SIZE_IN_BYTES);
        return priceRecord;
    }

    private void checkChunkIsNotFull() {
        if (recordsNumber() >= MAX_CHUNK_SIZE) {
            throw new IllegalStateException("Chunk is full, max number of records is: " + MAX_CHUNK_SIZE);
        }
    }

    @Override
    public int sizeInBytes() {
        return defineSize(recordsNumber());
    }

    public static int defineSize(int recordsNumber) {
        return CHUNK_HEADER_SIZE + recordsNumber * PriceRecord.SIZE_IN_BYTES;
    }

}
