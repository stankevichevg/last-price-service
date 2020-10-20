package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Flyweight;
import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static com.xxx.service.lastprice.Configuration.INSTRUMENT_MAX_TICKER_LENGTH;
import static com.xxx.service.lastprice.Configuration.PRICE_MAX_PAYLOAD_SIZE;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class PriceRecord extends Flyweight {

    public static final int INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET = 0;
    public static final int INSTRUMENT_TICKER_FIELD_OFFSET = INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET + SIZE_OF_INT;
    public static final int AS_OF_FIELD_OFFSET = INSTRUMENT_TICKER_FIELD_OFFSET + INSTRUMENT_MAX_TICKER_LENGTH;
    public static final int PAYLOAD_SIZE_FIELD_OFFSET = AS_OF_FIELD_OFFSET + SIZE_OF_LONG;
    public static final int PAYLOAD_FIELD_OFFSET = PAYLOAD_SIZE_FIELD_OFFSET + SIZE_OF_INT;
    public static final int SIZE_IN_BYTES = PAYLOAD_FIELD_OFFSET + PRICE_MAX_PAYLOAD_SIZE;

    private final AsciiSequenceView asciiSequenceView = new AsciiSequenceView();

    public int instrumentTickerLength() {
        return readBuffer.getInt(offset + INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET);
    }

    private void instrumentTickerLength(int instrumentTickerLength) {
        writeBuffer.putInt(offset + INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET, instrumentTickerLength);
    }

    public CharSequence instrument() {
        asciiSequenceView.wrap(readBuffer, offset + INSTRUMENT_TICKER_FIELD_OFFSET, instrumentTickerLength());
        return asciiSequenceView;
    }

    public void instrument(CharSequence instrument) {
        checkInstrument(instrument);
        instrumentTickerLength(instrument.length());
        for(int i = 0; i < instrument.length(); i++) {
            writeBuffer.putByte(offset + INSTRUMENT_TICKER_FIELD_OFFSET + i, (byte) instrument.charAt(i));
        }
    }

    public long asOfTimestamp() {
        return readBuffer.getLong(offset + AS_OF_FIELD_OFFSET);
    }

    public void asOfTimestamp(long asOfTimestamp) {
        writeBuffer.putLong(offset + AS_OF_FIELD_OFFSET, asOfTimestamp);
    }

    public int payloadSize() {
        return readBuffer.getInt(offset + PAYLOAD_SIZE_FIELD_OFFSET);
    }

    private void payloadSize(int payloadSize) {
        if (payloadSize > PRICE_MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size can not be more than: " + PRICE_MAX_PAYLOAD_SIZE);
        }
        writeBuffer.putInt(offset + PAYLOAD_SIZE_FIELD_OFFSET, payloadSize);
    }

    public void getPayload(MutableDirectBuffer writeTo, int writeOffset) {
        writeTo.putBytes(writeOffset, readBuffer, offset + PAYLOAD_FIELD_OFFSET, payloadSize());
    }

    public void putPayload(DirectBuffer readFrom, int readOffset, int payloadSize) {
        payloadSize(payloadSize);
        writeBuffer.putBytes(offset + PAYLOAD_FIELD_OFFSET, readFrom, readOffset, payloadSize);
    }

    public int absolutePayloadOffset() {
        return offset + PAYLOAD_FIELD_OFFSET;
    }

    private void checkInstrument(CharSequence instrument) {
        if (instrument.length() > INSTRUMENT_MAX_TICKER_LENGTH) {
            throw new IllegalArgumentException(
                "Instrument length can not be longer than: " + INSTRUMENT_MAX_TICKER_LENGTH
            );
        }
    }

    @Override
    public int sizeInBytes() {
        return SIZE_IN_BYTES;
    }

}
