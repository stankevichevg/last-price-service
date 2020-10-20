package com.xxx.core.protocol;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * Flyweight object.
 */
public abstract class Flyweight {

    protected MutableDirectBuffer writeBuffer;
    protected DirectBuffer readBuffer;
    protected int offset;

    public static void allocateMemoryForFlyweight(Flyweight flyweight) {
        allocateMemoryForFlyweight(flyweight, flyweight.sizeInBytes());
    }

    public static void allocateMemoryForFlyweight(Flyweight flyweight, int bufferSize) {
        flyweight.wrapForWrite(new UnsafeBuffer(allocateDirectAligned(bufferSize, CACHE_LINE_LENGTH)), 0);
    }

    public void wrapForWrite(MutableDirectBuffer buffer, int offset) {
        this.readBuffer = buffer;
        this.writeBuffer = buffer;
        this.offset = offset;
    }

    public void wrapForRead(DirectBuffer buffer, int offset) {
        this.readBuffer = buffer;
        this.writeBuffer = null;
        this.offset = offset;
    }

    public DirectBuffer getReadBuffer() {
        return readBuffer;
    }

    public int getOffset() {
        return offset;
    }

    public abstract int sizeInBytes();

}
