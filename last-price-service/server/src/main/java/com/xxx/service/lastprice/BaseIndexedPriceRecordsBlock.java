package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Base implementation for price records block. Block holds specified {@link #recordsNumber} memory blocks
 * for {@link IndexedPriceRecord} structures. Price records can be written to the block and read from it using
 * internal codec object {@link #priceRecord}.
 * Clients are not allowed to write to {@link #priceRecord} directly,
 * method {@link #tryUpdateRecord} should be used for this.
 *
 * Structure is cache friendly since it works on single {@link DirectBuffer} which should be provided by
 * the {@link #wrapForWrite(MutableDirectBuffer, int)} operation before any method of the block is used.
 *
 * To avoid any garbage generation the structure does not use {@link String} keys to index price records.
 * {@code int} indexes are used instead. The structure assumed the instrument String key to int index mapping
 * is done externally. You can use {@link InstrumentIndexer} for this.
 *
 * @see BatchRun
 * @see MarketState
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BaseIndexedPriceRecordsBlock extends Flyweight {

    final int recordsNumber;

    /**
     * Price record to read information from the block and write to.
     * Before using {@link #priceRecord} method {@link #selectRecord(int)} should be called.
     */
    final IndexedPriceRecord priceRecord = new IndexedPriceRecord();

    private final int headerSize;

    protected BaseIndexedPriceRecordsBlock(int recordsNumber, int headerSize) {
        this.recordsNumber = recordsNumber;
        this.headerSize = headerSize;
    }

    /**
     * Resets timestamps of all records to zeros.
     */
    public void reset() {
        for (int index = 0; index < recordsNumber; index++) {
            getPriceRecord(index).timestamp(0L);
        }
    }

    /**
     * Wrap given buffer for write.
     * Method adds additional logic to validate if size of the buffer is sufficient to be used by this block.
     *
     * @param buffer buffer to wrap
     * @param offset
     */
    @Override
    public void wrapForWrite(MutableDirectBuffer buffer, int offset) {
        final int requiredCapacity = sizeInBytes();
        final int effectiveCapacity = buffer.capacity() - offset;
        if (effectiveCapacity < requiredCapacity) {
            throw new IllegalArgumentException(
                "Insufficient effective buffer capacity, required " + requiredCapacity + ", got " +
                    effectiveCapacity + "bytes"
            );
        }
        super.wrapForWrite(buffer, offset);
    }

    /**
     * Updates block price record with the given index if provided timestamp greater or equal the current one.
     *
     * @param priceRecordIndex index of the record to update
     * @param timestamp new timestamp to set to the record
     * @param payload buffer with new payload of the record
     * @param payloadOffset offset to read payload from
     * @param payloadSize payload recordsNumber to read from the given payload buffer
     */
    public void tryUpdateRecord(int priceRecordIndex, long timestamp, DirectBuffer payload, int payloadOffset, int payloadSize) {
        checkRecordIndex(priceRecordIndex);
        selectRecord(priceRecordIndex);
        if (timestamp >= priceRecord.timestamp()) {
            priceRecord.timestamp(timestamp);
            priceRecord.putPayload(payload, payloadOffset, payloadSize);
        }
    }

    /**
     * Retrieves price record to read from.
     *
     * @param index price record index.
     * @return price record with specified index
     */
    public IndexedPriceRecord getPriceRecord(int index) {
        checkRecordIndex(index);
        selectRecord(index);
        return priceRecord;
    }

    @Override
    public int sizeInBytes() {
        return defineBlockSizeInBytes(headerSize, recordsNumber);
    }

    /**
     * Defines block recordsNumber in bytes for a block with given header recordsNumber and records number.
     *
     * @param headerSizeInBytes block header recordsNumber in bytes
     * @param recordsNumber number of records in block
     * @return recordsNumber of the specified block in bytes
     */
    public static int defineBlockSizeInBytes(int headerSizeInBytes, int recordsNumber) {
        return headerSizeInBytes + recordsNumber * IndexedPriceRecord.MESSAGE_SIZE;
    }

    /**
     * Prepares record with the given index for write and read operations.
     * Method wraps specific buffer region by the record codec {@link #priceRecord}.
     *
     * @param index record of the index to prepare for writes. Method assumes index param to be validated externally.
     */
    final void selectRecord(int index) {
        priceRecord.wrapForWrite(writeBuffer, offset + headerSize + index * priceRecord.sizeInBytes());
    }

    private void checkRecordIndex(int priceRecordIndex) {
        if (priceRecordIndex < 0 || priceRecordIndex >= recordsNumber) {
            throw new IndexOutOfBoundsException("Price record index is out of bounds: " + priceRecordIndex);
        }
    }

}
