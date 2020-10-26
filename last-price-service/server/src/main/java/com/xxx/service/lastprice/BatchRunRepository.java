package com.xxx.service.lastprice;

import java.util.function.Consumer;

/**
 * Repository to manage lifecycle of {@link BatchRun} objects.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public interface BatchRunRepository {

    Consumer<BatchRun> NO_OP_CONSUMER = br -> {/* do nothing */};

    /**
     * Creates batch run.
     *
     * @param id id of new batch run
     * @return created batch run
     */
    BatchRun create(long id);

    /**
     * Retrieves existing batch run.
     *
     * @param id identifier of the requested batch run
     * @return batch run with the specified id or {@code null} if there was no such batch found
     */
    BatchRun get(long id);

    /**
     * Removes presented batch run.
     *
     * @param batchRun batch run to delete.
     */
    void remove(BatchRun batchRun);

    /**
     * Saves changes of the given batch run.
     *
     * @param batchRun batch run to save
     */
    void save(BatchRun batchRun);

    /**
     * Returns current number of instances.
     *
     * @return number of active batch runs
     */
    int size();

    /**
     * Removes at most one batch runs which was updated more than specified time ago.
     *
     * @param evictionTimeout record eviction timeout
     * @return {@code true} if a record was removed, else returns {@code false}
     */
    default boolean removeOutdated(long evictionTimeout) {
        return removeOutdated(evictionTimeout, 1, NO_OP_CONSUMER) == 1;
    }

    /**
     * Removes batch runs which were updated more than specified time ago.
     *
     * @param evictionTime records eviction time
     * @param limit maximum number of records to delete
     * @param consumer consumer for removed batch runs. Consumer can be used to notify a client about batch run eviction.
     * @return number of removed records
     */
    int removeOutdated(long evictionTime, int limit, Consumer<BatchRun> consumer);

    /**
     * Removes all repository records.
     */
    void removeAll();

}
