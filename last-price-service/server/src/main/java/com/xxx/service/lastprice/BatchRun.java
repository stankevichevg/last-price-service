package com.xxx.service.lastprice;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;

import java.util.Objects;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;

/**
 * Represents aggregation view of all previous successful chunk uploading operations.
 *
 * {@inheritDoc}
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BatchRun extends BaseIndexedPriceRecordsBlock {

    public static final int ID_FIELD_OFFSET = 0;
    public static final int LAST_UPDATE_TIMESTAMP_FIELD_OFFSET = ID_FIELD_OFFSET + SIZE_OF_INT;
    public static final int BATCH_RUN_HEADER_SIZE = LAST_UPDATE_TIMESTAMP_FIELD_OFFSET + SIZE_OF_LONG;

    private final int index;
    private final IntHashSet includedRecordIndexes;

    private final RecordsUpdater recordsUpdater = new RecordsUpdater();

    /**
     * Creates batch run price records block.
     *
     * @param index batch run index
     * @param recordsNumber number of price records in the block
     */
    public BatchRun(int index, int recordsNumber) {
        super(recordsNumber, BATCH_RUN_HEADER_SIZE);
        this.index = index;
        this.includedRecordIndexes = new IntHashSet(recordsNumber);
    }

    /**
     * {@inheritDoc}
     */
    public void tryUpdateRecord(int priceRecordIndex, long timestamp, DirectBuffer payload, int payloadOffset, int payloadSize) {
        includedRecordIndexes.add(priceRecordIndex);
        super.tryUpdateRecord(priceRecordIndex, timestamp, payload, payloadOffset, payloadSize);
    }

    /**
     * Merge state of the current batch run to the provided bloc.
     * All tracking records of the batch will be populated to other block.
     *
     * @param priceRecordsBlock block to merge current state to
     */
    public void mergeTo(BaseIndexedPriceRecordsBlock priceRecordsBlock) {
        recordsUpdater.setBlockToUpdate(priceRecordsBlock);
        iterateRecords(recordsUpdater);
    }

    public int getIndex() {
        return index;
    }

    /**
     * Returns batch run id.
     *
     * @return batch run id
     */
    public long id() {
        return readBuffer.getLong(offset + ID_FIELD_OFFSET);
    }

    /**
     * Sets batch run id.
     *
     * @param id batch run id to set
     */
    void id(long id) {
        writeBuffer.putLong(offset + ID_FIELD_OFFSET, id);
    }

    /**
     * Returns last update timestamp for the batch run.
     *
     * @return last update timestamp
     */
    public long lastUpdateTimestamp() {
        return readBuffer.getLong(offset + LAST_UPDATE_TIMESTAMP_FIELD_OFFSET);
    }

    /**
     * Sets last update timestamp for the batch run.
     *
     * @param lastUpdateTimestamp last update timestamp to set
     */
    void lastUpdateTimestamp(long lastUpdateTimestamp) {
        writeBuffer.putLong(offset + LAST_UPDATE_TIMESTAMP_FIELD_OFFSET, lastUpdateTimestamp);
    }

    /**
     * Iterates all updated records since the last call of {@link #reset()} method.
     *
     * @param recordConsumer consumer for updated records traversal.
     */
    public void iterateRecords(IntIndexedEntityConsumer<IndexedPriceRecord> recordConsumer) {
        for (int index : includedRecordIndexes) {
            selectRecord(index);
            recordConsumer.accept(index, priceRecord);
        }
    }

    /**
     * Resets internal state of updated records tracking.
     */
    public void reset() {
        includedRecordIndexes.clear();
        super.reset();
    }

    // define equals/hashCode since we are going to use BatchRun objects as heap structure elements.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchRun batchRun = (BatchRun) o;
        return index == batchRun.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }

    private static class RecordsUpdater implements IntIndexedEntityConsumer<IndexedPriceRecord> {

        private BaseIndexedPriceRecordsBlock blockToUpdate;

        public void setBlockToUpdate(BaseIndexedPriceRecordsBlock blockToUpdate) {
            this.blockToUpdate = blockToUpdate;
        }

        @Override
        public void accept(int index, IndexedPriceRecord indexedPriceRecord) {
            blockToUpdate.tryUpdateRecord(
                index,
                indexedPriceRecord.timestamp(),
                indexedPriceRecord.getReadBuffer(),
                indexedPriceRecord.absolutePayloadOffset(),
                indexedPriceRecord.payloadSize()
            );
        }
    }

}
