package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static com.xxx.service.lastprice.messages.MessageTypes.CANCEL_BATCH_REQUEST_MESSAGE_TYPE;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class CancelBatchRunRequest extends Message {

    public static final int BATCH_ID_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    public static final int MESSAGE_SIZE = BATCH_ID_FIELD_OFFSET + SIZE_OF_LONG;

    public long batchId() {
        return readBuffer.getLong(offset + BATCH_ID_FIELD_OFFSET);
    }

    public void batchId(long batchId) {
        writeBuffer.putLong(offset + BATCH_ID_FIELD_OFFSET, batchId);
    }

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

    @Override
    protected int uniqueType() {
        return CANCEL_BATCH_REQUEST_MESSAGE_TYPE;
    }
}
