package com.xxx.service.lastprice;

import com.xxx.core.protocol.Flyweight;
import com.xxx.core.protocol.Message;
import com.xxx.service.lastprice.messages.CancelBatchRunRequest;
import com.xxx.service.lastprice.messages.CancelBatchRunResponse;
import com.xxx.service.lastprice.messages.CompleteBatchRunRequest;
import com.xxx.service.lastprice.messages.CompleteBatchRunResponse;
import com.xxx.service.lastprice.messages.LastPriceRequest;
import com.xxx.service.lastprice.messages.LastPriceResponse;
import com.xxx.service.lastprice.messages.PriceRecordsChunk;
import com.xxx.service.lastprice.messages.StartBatchRunRequest;
import com.xxx.service.lastprice.messages.StartBatchRunResponse;
import com.xxx.service.lastprice.messages.UploadChunkRequest;
import com.xxx.service.lastprice.messages.UploadChunkResponse;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;
import java.util.function.LongSupplier;

import static com.xxx.service.lastprice.Configuration.MAX_ACTIVE_BATCHES_NUMBER;
import static java.util.Set.of;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class LastPriceServiceHandlerTest {


    private static final MutableDirectBuffer PAYLOAD_BUFFER =
        new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));

    private static final Set<String> INSTRUMENTS = of(
        "AAPL",
        "AMZN",
        "MSFT"
    );

    private final LastPriceRequest getLastPriceRequest = new LastPriceRequest();
    private final StartBatchRunRequest startBatchRunRequest = new StartBatchRunRequest();
    private final UploadChunkRequest uploadChunkRequest = new UploadChunkRequest();
    private final CancelBatchRunRequest cancelBatchRunRequest = new CancelBatchRunRequest();
    private final CompleteBatchRunRequest completeBatchRunRequest = new CompleteBatchRunRequest();

    private final EpochClock clock = Mockito.mock(EpochClock.class);
    private final LastPriceServiceHandler serviceHandler = new LastPriceServiceHandler(clock, INSTRUMENTS);

    @BeforeEach
    public void init() {
        Message.allocateMemoryForMessage(getLastPriceRequest);
        Message.allocateMemoryForMessage(startBatchRunRequest);
        uploadChunkRequest.init(new UnsafeBuffer(allocateDirectAligned(UploadChunkRequest.maxSize(), CACHE_LINE_LENGTH)), 0);
        Message.allocateMemoryForMessage(cancelBatchRunRequest);
        Message.allocateMemoryForMessage(completeBatchRunRequest);
        serviceHandler.reset();
    }

    @Test
    public void shouldStartBatchRun() {
        final long batchId = 100500L;
        final StartBatchRunResponse response = call(startBatchRunRequest, () -> batchId);
        assertThat(response.status(), is(StartBatchRunResponse.SUCCESS_STATUS));
        assertThat(response.batchId(), is(batchId));
    }

    @Test
    public void shouldCancelBatchRun() {
        final long batchId = 100500L;
        call(startBatchRunRequest, () -> batchId);
        cancelBatchRunRequest.batchId(batchId);
        final CancelBatchRunResponse response = call(cancelBatchRunRequest, null);
        assertThat(response.status(), is(CancelBatchRunResponse.SUCCESS_STATUS));
        completeBatchRunRequest.batchId(batchId);
        final CompleteBatchRunResponse completeResponse = call(completeBatchRunRequest, null);
        assertThat(completeResponse.status(), is(CompleteBatchRunResponse.BATCH_RUN_NOT_FOUND_STATUS));
    }

    @Test
    public void shouldUploadBatchRunAndGetPrice() {
        final long batchId = 100500L;
        call(startBatchRunRequest, () -> batchId);

        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1));
        PAYLOAD_BUFFER.putInt(0, 12345);
        priceRecordsChunk.addRecord("AAPL", 100L, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        uploadChunkRequest.batchId(batchId);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        final UploadChunkResponse response = call(uploadChunkRequest, null);
        assertThat(response.status(), is(UploadChunkResponse.SUCCESS_STATUS));

        completeBatchRunRequest.batchId(batchId);
        final CompleteBatchRunResponse completeResponse = call(completeBatchRunRequest, null);
        assertThat(completeResponse.status(), is(CompleteBatchRunResponse.SUCCESS_STATUS));

        getLastPriceRequest.instrument("AAPL");
        final LastPriceResponse lastPriceResponse = call(getLastPriceRequest, null);
        lastPriceResponse.priceRecord().getPayload(PAYLOAD_BUFFER, 0);
        assertThat("AAPL".contentEquals(lastPriceResponse.priceRecord().instrument()), is(true));
        assertThat(lastPriceResponse.priceRecord().asOfTimestamp(), is(100L));
        assertThat(PAYLOAD_BUFFER.getInt(0), is(12345));
    }

    @Test
    public void shouldReturnBatchNotFoundIfUploadToWrongBatch() {
        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1));
        PAYLOAD_BUFFER.putInt(0, 12345);
        priceRecordsChunk.addRecord("AAPL", 100L, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        uploadChunkRequest.batchId(123L);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        final UploadChunkResponse response = call(uploadChunkRequest, null);
        assertThat(response.status(), is(UploadChunkResponse.BATCH_RUN_NOT_FOUND_STATUS));
    }

    @Test
    public void shouldReturnInstrumentNotFoundIfUploadWrongInstrument() {
        final long batchId = 100500L;
        call(startBatchRunRequest, () -> batchId);
        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1));
        PAYLOAD_BUFFER.putInt(0, 12345);
        priceRecordsChunk.addRecord("XXX", 100L, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        uploadChunkRequest.batchId(batchId);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        final UploadChunkResponse response = call(uploadChunkRequest, null);
        assertThat(response.status(), is(UploadChunkResponse.INSTRUMENT_NOT_FOUND_STATUS));
    }

    @Test
    public void shouldNotReturnPriceIfBatchIsNotComplete() {
        final long batchId = 100500L;
        call(startBatchRunRequest, () -> batchId);

        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1));
        PAYLOAD_BUFFER.putInt(0, 12345);
        priceRecordsChunk.addRecord("AAPL", 100L, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        uploadChunkRequest.batchId(batchId);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        final UploadChunkResponse response = call(uploadChunkRequest, null);
        assertThat(response.status(), is(UploadChunkResponse.SUCCESS_STATUS));

        getLastPriceRequest.instrument("AAPL");
        final LastPriceResponse lastPriceResponse = call(getLastPriceRequest, null);
        assertThat(lastPriceResponse.status(), is(LastPriceResponse.PRICE_NOT_AVAILABLE_STATUS));
    }

    @Test
    public void shouldReturnWrongInstrumentStatusIfUnsupportedInstrument() {
        getLastPriceRequest.instrument("XXX");
        final LastPriceResponse lastPriceResponse = call(getLastPriceRequest, null);
        assertThat(lastPriceResponse.status(), is(LastPriceResponse.WRONG_INSTRUMENT_STATUS));
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionIfWrongRequest() {
        getLastPriceRequest.instrument("XXX");
        assertThrows(IllegalArgumentException.class, () -> {
            serviceHandler.handleRequest(
                100500,
                null,
                getLastPriceRequest.getReadBuffer(),
                getLastPriceRequest.getOffset(),
                getLastPriceRequest.sizeInBytes()
            );
        });
    }

    @Test
    public void shouldReturnCanNotCreateBatchStatusIfMaxNumberCreated() {
        for (long i = 0; i < MAX_ACTIVE_BATCHES_NUMBER; i++) {
            final long batchId = i;
            call(startBatchRunRequest, () -> batchId);
        }
        final StartBatchRunResponse response = call(startBatchRunRequest, () -> MAX_ACTIVE_BATCHES_NUMBER);
        assertThat(response.status(), is(StartBatchRunResponse.CAN_NOT_CREATE_BATCH_STATUS));
    }

    @SuppressWarnings("unchecked")
    private <T extends Message> T call(Message request, LongSupplier idGenerator) {
        return (T) serviceHandler.handleRequest(
            request.uniqueType(), idGenerator, request.getReadBuffer(), request.getOffset(), request.sizeInBytes()
        );
    }

}
