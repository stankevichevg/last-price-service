package com.xxx.service.lastprice;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayQueue;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import static com.xxx.service.lastprice.Configuration.MAX_ACTIVE_BATCHES_NUMBER;
import static java.util.Comparator.comparingLong;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 *
 *
 * TODO think about better class name =)
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BatchRunRepositoryImpl implements BatchRunRepository {

    private final EpochClock epochClock;
    private final MutableDirectBuffer buffer;
    private final Long2LongHashMap idToIndexMapping;
    private final List<BatchRun> batchRunsPool;
    private final IntArrayQueue freeIndexes;
    private final PriorityQueue<BatchRun> cleanUpQueue;

    public BatchRunRepositoryImpl(EpochClock epochClock, int instrumentsNumber) {
        this.epochClock = epochClock;
        this.buffer = allocateBatchRunsBuffer(instrumentsNumber);
        this.idToIndexMapping = new Long2LongHashMap(-1);
        this.freeIndexes = createFreeIndexesQueue();
        this.batchRunsPool = createBatchRunsPool(instrumentsNumber);
        this.cleanUpQueue = new PriorityQueue<>(
            MAX_ACTIVE_BATCHES_NUMBER, comparingLong(BatchRun::lastUpdateTimestamp)
        );
    }

    @Override
    public BatchRun create(long id) {
        if (freeIndexes.size() == 0) {
            throw new IndexOutOfBoundsException("New batch run can not be created, max number of them is reached");
        }
        final int index = freeIndexes.pollInt();
        final BatchRun batchRun = batchRunsPool.get(index);
        batchRun.id(id);
        batchRun.lastUpdateTimestamp(epochClock.time());
        idToIndexMapping.put(id, index);
        cleanUpQueue.add(batchRun);
        return batchRun;
    }

    @Override
    public BatchRun get(long id) {
        // we can be sure there will no overflow here, we use array indexes as values
        final int batchRunIndex = (int) idToIndexMapping.get(id);
        if (batchRunIndex == -1) {
            return null;
        }
        return batchRunsPool.get(batchRunIndex);
    }

    @Override
    public void remove(BatchRun batchRun) {
        batchRun.reset();
        freeIndexes.addInt(batchRun.getIndex());
        cleanUpQueue.remove(batchRun);
        idToIndexMapping.remove(batchRun.id());
    }

    @Override
    public void save(BatchRun batchRun) {
        cleanUpQueue.remove(batchRun);
        batchRun.lastUpdateTimestamp(epochClock.time());
        cleanUpQueue.add(batchRun);
    }

    @Override
    public int size() {
        return idToIndexMapping.size();
    }

    @Override
    public int removeOutdated(long evictionTime, int limit, Consumer<BatchRun> consumer) {
        final long lastAllowedMoment = epochClock.time() - evictionTime;
        int removedCounter = 0;
        while (removedCounter < limit && !cleanUpQueue.isEmpty()) {
            final BatchRun oldestBatchRun = cleanUpQueue.peek();
            if (lastAllowedMoment < oldestBatchRun.lastUpdateTimestamp()) {
                break;
            }
            remove(oldestBatchRun);
            removedCounter++;
            consumer.accept(oldestBatchRun);
        }
        return removedCounter;
    }

    private MutableDirectBuffer allocateBatchRunsBuffer(int instrumentsNumber) {
        final int bufferSize = MAX_ACTIVE_BATCHES_NUMBER *
            BatchRun.defineBlockSizeInBytes(BatchRun.BATCH_RUN_HEADER_SIZE, instrumentsNumber);
        return new UnsafeBuffer(allocateDirectAligned(bufferSize, CACHE_LINE_LENGTH));
    }

    private List<BatchRun> createBatchRunsPool(int instrumentsNumber) {
        final List<BatchRun> batchRunsPool = new ArrayList<>(MAX_ACTIVE_BATCHES_NUMBER);
        for (int i = 0; i < MAX_ACTIVE_BATCHES_NUMBER; i++) {
            final BatchRun batchRun = new BatchRun(i, instrumentsNumber);
            batchRun.wrapForWrite(buffer, i * batchRun.sizeInBytes());
            batchRunsPool.add(batchRun);
        }
        return batchRunsPool;
    }

    private IntArrayQueue createFreeIndexesQueue() {
        final IntArrayQueue freeIndexes = new IntArrayQueue(MAX_ACTIVE_BATCHES_NUMBER);
        for (int freeIndex = 0; freeIndex < MAX_ACTIVE_BATCHES_NUMBER; freeIndex++) {
            freeIndexes.addInt(freeIndex);
        }
        return freeIndexes;
    }

}
