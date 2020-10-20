package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static org.agrona.BitUtil.SIZE_OF_INT;

public class LastPriceResponse extends Message {

    public static final int SUCCESS_STATUS = 0;
    public static final int WRONG_INSTRUMENT_STATUS = 1;
    public static final int PRICE_NOT_AVAILABLE_STATUS = 2;

    public static final int STATUS_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    public static final int PRICE_RECORD_FIELD_OFFSET = STATUS_FIELD_OFFSET + SIZE_OF_INT;
    public static final int MESSAGE_SIZE = PRICE_RECORD_FIELD_OFFSET + PriceRecord.SIZE_IN_BYTES;

    private final PriceRecord priceRecord = new PriceRecord();

    public PriceRecord priceRecord() {
        priceRecord.wrapForRead(readBuffer, offset + PRICE_RECORD_FIELD_OFFSET);
        return priceRecord;
    }

    public PriceRecord priceRecordForWrite() {
        priceRecord.wrapForWrite(writeBuffer, offset + PRICE_RECORD_FIELD_OFFSET);
        return priceRecord;
    }

    public int status() {
        return readBuffer.getInt(offset + STATUS_FIELD_OFFSET);
    }

    public void status(int status) {
        writeBuffer.putInt(offset + STATUS_FIELD_OFFSET, status);
    }

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

    @Override
    protected int uniqueType() {
        return MessageTypes.LAST_PRICE_RESPONSE_MESSAGE_TYPE;
    }
}
