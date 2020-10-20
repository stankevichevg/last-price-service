package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static com.xxx.service.lastprice.messages.MessageTypes.START_BATCH_REQUEST_MESSAGE_TYPE;


public class StartBatchRunRequest extends Message {

    public static final int MESSAGE_SIZE = MESSAGE_HEADER_LENGTH;

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

    @Override
    protected int uniqueType() {
        return START_BATCH_REQUEST_MESSAGE_TYPE;
    }
}
