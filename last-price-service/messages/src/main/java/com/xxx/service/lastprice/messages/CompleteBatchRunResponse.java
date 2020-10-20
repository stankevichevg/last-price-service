package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static com.xxx.service.lastprice.messages.MessageTypes.COMPLETE_BATCH_RESPONSE_MESSAGE_TYPE;
import static org.agrona.BitUtil.SIZE_OF_INT;

public class CompleteBatchRunResponse extends Message {

    public static final int SUCCESS_STATUS = 0;
    public static final int BATCH_RUN_NOT_FOUND_STATUS = 1;

    private static final int STATUS_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    private static final int BATCH_ID_FIELD_OFFSET = STATUS_FIELD_OFFSET + SIZE_OF_INT;
    private static final int MESSAGE_SIZE = BATCH_ID_FIELD_OFFSET + SIZE_OF_INT;

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
        return COMPLETE_BATCH_RESPONSE_MESSAGE_TYPE;
    }
}
