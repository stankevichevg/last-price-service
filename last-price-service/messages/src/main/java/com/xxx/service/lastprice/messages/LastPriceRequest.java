package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;
import org.agrona.AsciiSequenceView;

import static com.xxx.service.lastprice.Configuration.INSTRUMENT_MAX_TICKER_LENGTH;
import static com.xxx.service.lastprice.messages.MessageTypes.LAST_PRICE_REQUEST_MESSAGE_TYPE;
import static org.agrona.BitUtil.SIZE_OF_INT;

public class LastPriceRequest extends Message {

    public static final int INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    public static final int INSTRUMENT_TICKER_FIELD_OFFSET = INSTRUMENT_TICKER_LENGTH_FIELD_OFFSET + SIZE_OF_INT;
    public static final int MESSAGE_SIZE = INSTRUMENT_TICKER_FIELD_OFFSET + INSTRUMENT_MAX_TICKER_LENGTH;

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

    private void checkInstrument(CharSequence instrument) {
        if (instrument.length() > INSTRUMENT_MAX_TICKER_LENGTH) {
            throw new IllegalArgumentException(
                "Instrument length can not be longer than: " + INSTRUMENT_MAX_TICKER_LENGTH
            );
        }
    }

    @Override
    public int sizeInBytes() {
        return MESSAGE_SIZE;
    }

    @Override
    protected int uniqueType() {
        return LAST_PRICE_REQUEST_MESSAGE_TYPE;
    }
}
