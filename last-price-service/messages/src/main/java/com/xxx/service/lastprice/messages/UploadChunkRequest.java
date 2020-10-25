package com.xxx.service.lastprice.messages;


import com.xxx.core.protocol.Message;

import static com.xxx.service.lastprice.Configuration.MAX_CHUNK_SIZE;
import static com.xxx.service.lastprice.messages.MessageTypes.UPLOAD_CHUNK_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.PriceRecordsChunk.CHUNK_HEADER_SIZE;
import static org.agrona.BitUtil.SIZE_OF_LONG;

public class UploadChunkRequest extends Message {

    private static final int BATCH_ID_FIELD_OFFSET = MESSAGE_HEADER_LENGTH;
    private static final int REQUEST_HEADER_LENGTH = BATCH_ID_FIELD_OFFSET + SIZE_OF_LONG;

    private final PriceRecordsChunk chunk = new PriceRecordsChunk();

    public PriceRecordsChunk getChunkToRead() {
        chunk.wrapForRead(readBuffer, offset + REQUEST_HEADER_LENGTH);
        return chunk;
    }

    public PriceRecordsChunk getChunk() {
        chunk.wrapForWrite(writeBuffer, offset + REQUEST_HEADER_LENGTH);
        return chunk;
    }

    public long batchId() {
        return readBuffer.getLong(offset + BATCH_ID_FIELD_OFFSET);
    }

    public void batchId(long batchId) {
        writeBuffer.putLong(offset + BATCH_ID_FIELD_OFFSET, batchId);
    }

    public void putChunk(PriceRecordsChunk chunk) {
        writeBuffer.putBytes(offset + REQUEST_HEADER_LENGTH, chunk.getReadBuffer(), chunk.getOffset(), chunk.sizeInBytes());
    }

    @Override
    public int sizeInBytes() {
        chunk.wrapForRead(readBuffer, offset + REQUEST_HEADER_LENGTH);
        return REQUEST_HEADER_LENGTH + chunk.recordsNumber() * PriceRecord.SIZE_IN_BYTES;
    }

    public static int maxSize() {
        return REQUEST_HEADER_LENGTH + CHUNK_HEADER_SIZE + MAX_CHUNK_SIZE * PriceRecord.SIZE_IN_BYTES;
    }

    @Override
    public int uniqueType() {
        return UPLOAD_CHUNK_REQUEST_MESSAGE_TYPE;
    }
}
