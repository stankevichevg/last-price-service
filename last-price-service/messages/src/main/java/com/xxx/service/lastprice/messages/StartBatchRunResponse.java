package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static com.xxx.service.lastprice.messages.MessageTypes.START_BATCH_RESPONSE_MESSAGE_TYPE;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class StartBatchRunResponse extends Message {

    public static final int SUCCESS_STATUS = 0;
    public static final int CAN_NOT_CREATE_BATCH_STATUS = 1;

    private static final int STATUS_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    public static final int BATCH_ID_FIELD_OFFSET = STATUS_FIELD_OFFSET + SIZE_OF_INT;
    public static final int MESSAGE_SIZE = BATCH_ID_FIELD_OFFSET + SIZE_OF_LONG;

    public int status() {
        return readBuffer.getInt(offset + STATUS_FIELD_OFFSET);
    }

    public void status(int status) {
        writeBuffer.putInt(offset + STATUS_FIELD_OFFSET, status);
    }

    public long batchId() {
        return readBuffer.getLong(offset + BATCH_ID_FIELD_OFFSET);
    }

    public void batchId(long batchId) {
        writeBuffer.putLong(offset + BATCH_ID_FIELD_OFFSET, batchId);
    }

    @Override
    public int uniqueType() {
        return START_BATCH_RESPONSE_MESSAGE_TYPE;
    }

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }
}
