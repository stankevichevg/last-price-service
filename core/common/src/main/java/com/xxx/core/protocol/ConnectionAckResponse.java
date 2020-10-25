package com.xxx.core.protocol;


import static com.xxx.core.protocol.SystemMessageTypes.CONNECTION_ACK_MESSAGE_TYPE;

public class ConnectionAckResponse extends Message {

    public static final int MESSAGE_SIZE = MESSAGE_HEADER_LENGTH;

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

    @Override
    public int uniqueType() {
        return CONNECTION_ACK_MESSAGE_TYPE;
    }

}
