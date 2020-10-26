package com.xxx.service.lastprice;

import org.agrona.concurrent.EpochClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.xxx.service.lastprice.Configuration.MAX_ACTIVE_BATCHES_NUMBER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class BatchRunRepositoryTest {

    private static final long BATCH_RUN_ID = 100500L;
    private static final int INSTRUMENTS_NUMBER = 2;

    private final EpochClock clock = Mockito.mock(EpochClock.class);
    private final BatchRunRepository repository = new BatchRunRepositoryImpl(clock, INSTRUMENTS_NUMBER);


    @BeforeEach
    public void init() {
        repository.removeAll();
        Mockito.when(clock.time()).thenReturn(0L);
    }

    @Test
    public void shouldCreateBatchRun() {
        final BatchRun batchRun = repository.create(BATCH_RUN_ID);
        assertThat(batchRun.id(), is(BATCH_RUN_ID));
        assertThat(repository.size(), is(1));
    }

    @Test
    public void shouldRemoveOutdated() {
        repository.create(BATCH_RUN_ID);
        assertThat(repository.size(), is(1));
        Mockito.when(clock.time()).thenReturn(50L);
        repository.removeOutdated(40L);
        assertThat(repository.size(), is(0));
    }

    @Test
    public void shouldNotRemoveNotOutdated() {
        repository.create(BATCH_RUN_ID);
        assertThat(repository.size(), is(1));
        Mockito.when(clock.time()).thenReturn(30L);
        repository.removeOutdated(40L);
        assertThat(repository.size(), is(1));
    }

    @Test
    public void shouldUpdateLastUpdateTimestampWhenSave() {
        final BatchRun batchRun = repository.create(BATCH_RUN_ID);
        assertThat(repository.size(), is(1));
        Mockito.when(clock.time()).thenReturn(10L, 45L);
        repository.save(batchRun);
        repository.removeOutdated(40L);
        assertThat(repository.size(), is(1));
    }

    @Test
    public void shouldReturnSameObjectForGetOperation() {
        final BatchRun batchRun = repository.create(BATCH_RUN_ID);
        assertThat(repository.get(BATCH_RUN_ID), is(equalTo(batchRun)));
    }

    @Test
    public void shouldReturnNullItThereIsNoSuchBatchId() {
        assertThat(repository.get(100L), is(nullValue()));
    }

    @Test
    public void shouldThrowIllegalStateExceptionIfCanNotCreate() {
        for (long i = 0L; i < MAX_ACTIVE_BATCHES_NUMBER; i++) {
            repository.create(i);
        }
        assertThrows(IllegalStateException.class, () -> {
            repository.create(MAX_ACTIVE_BATCHES_NUMBER);
        });
    }

    @Test
    public void shouldRemoveAll() {
        for (long i = 0L; i < MAX_ACTIVE_BATCHES_NUMBER; i++) {
            repository.create(i);
        }
        assertThat(repository.size(), is(MAX_ACTIVE_BATCHES_NUMBER));
        repository.removeAll();
        assertThat(repository.size(), is(0));
    }

}
