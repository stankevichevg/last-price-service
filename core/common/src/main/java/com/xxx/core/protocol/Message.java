package com.xxx.core.protocol;

import org.agrona.concurrent.UnsafeBuffer;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.allocateDirectAligned;

public abstract class Message extends Flyweight {

    public static final int TYPE_FIELD_OFFSET = 0;
    public static final int CONNECTION_ID_FIELD_OFFSET = TYPE_FIELD_OFFSET + SIZE_OF_INT;
    public static final int CLIENT_ID_FIELD_OFFSET = CONNECTION_ID_FIELD_OFFSET + SIZE_OF_LONG;
    public static final int CORRELATION_ID_FIELD_OFFSET = CLIENT_ID_FIELD_OFFSET + SIZE_OF_INT;
    public static final int MESSAGE_HEADER_LENGTH = CORRELATION_ID_FIELD_OFFSET + SIZE_OF_LONG;

    public void init(UnsafeBuffer buffer, int offset) {
        wrapForWrite(buffer, offset);
        type(uniqueType());
    }

    public static void allocateMemoryForMessage(Message message) {
        message.init(new UnsafeBuffer(allocateDirectAligned(message.sizeInBytes(), CACHE_LINE_LENGTH)), 0);
    }

    public int type() {
        return readBuffer.getInt(offset + TYPE_FIELD_OFFSET);
    }

    public void type(int type) {
        writeBuffer.putInt(offset + TYPE_FIELD_OFFSET, type);
    }

    public long connectionId() {
        return readBuffer.getLong(offset + CONNECTION_ID_FIELD_OFFSET);
    }

    public void connectionId(long connectionId) {
        writeBuffer.putLong(offset + CONNECTION_ID_FIELD_OFFSET, connectionId);
    }

    public int clientId() {
        return readBuffer.getInt(offset + CLIENT_ID_FIELD_OFFSET);
    }

    public void clientId(int clientId) {
        writeBuffer.putInt(offset + CLIENT_ID_FIELD_OFFSET, clientId);
    }

    public long correlationId() {
        return readBuffer.getLong(offset + CORRELATION_ID_FIELD_OFFSET);
    }

    public void correlationId(long correlationId) {
        writeBuffer.putLong(offset + CORRELATION_ID_FIELD_OFFSET, correlationId);
    }

    public abstract int uniqueType();

}
