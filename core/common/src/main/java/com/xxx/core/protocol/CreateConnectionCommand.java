package com.xxx.core.protocol;


import static com.xxx.core.protocol.SystemMessageTypes.CREATE_CONNECTION_MESSAGE_TYPE;
import static org.agrona.BitUtil.SIZE_OF_INT;

public class CreateConnectionCommand extends Message {

    private static final int MAX_BUFFER_SIZE = 128;

    public static final int CLIENT_STREAM_ID_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    public static final int CLIENT_CHANNEL_FIELD_OFFSET = CLIENT_STREAM_ID_FIELD_OFFSET + SIZE_OF_INT;

    public int clientStreamId() {
        return readBuffer.getInt(offset + CLIENT_STREAM_ID_FIELD_OFFSET);
    }

    public void clientStreamId(int clientStreamId) {
        writeBuffer.putInt(offset + CLIENT_STREAM_ID_FIELD_OFFSET, clientStreamId);
    }

    public String clientChannel() {
        return readBuffer.getStringUtf8(offset + CLIENT_CHANNEL_FIELD_OFFSET);
    }

    public void clientChannel(String clientChannel) {
        writeBuffer.putStringUtf8(offset + CLIENT_CHANNEL_FIELD_OFFSET, clientChannel);
    }

    @Override
    public int sizeInBytes() {
        return MAX_BUFFER_SIZE;
    }

    @Override
    protected int uniqueType() {
        return CREATE_CONNECTION_MESSAGE_TYPE;
    }
}
