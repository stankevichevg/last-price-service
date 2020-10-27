package com.xxx.service.lastprice;

import com.xxx.core.client.BaseServiceClient;
import com.xxx.core.client.AbstractServiceGateway;
import com.xxx.core.client.ConnectionTimeoutException;
import com.xxx.service.lastprice.messages.CancelBatchRunRequest;
import com.xxx.service.lastprice.messages.CancelBatchRunResponse;
import com.xxx.service.lastprice.messages.CompleteBatchRunRequest;
import com.xxx.service.lastprice.messages.CompleteBatchRunResponse;
import com.xxx.service.lastprice.messages.LastPriceRequest;
import com.xxx.service.lastprice.messages.LastPriceResponse;
import com.xxx.service.lastprice.messages.PriceRecord;
import com.xxx.service.lastprice.messages.PriceRecordsChunk;
import com.xxx.service.lastprice.messages.StartBatchRunRequest;
import com.xxx.service.lastprice.messages.StartBatchRunResponse;
import com.xxx.service.lastprice.messages.UploadChunkRequest;
import com.xxx.service.lastprice.messages.UploadChunkResponse;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;

import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * {@inheritDoc}
 */
public class LastPriceServiceClient extends BaseServiceClient {

    private final LastPriceRequest lastPriceRequest = new LastPriceRequest();
    private final StartBatchRunRequest startBatchRequest = new StartBatchRunRequest();
    private final UploadChunkRequest uploadChunkRequest = new UploadChunkRequest();
    private final CancelBatchRunRequest cancelBatchRunRequest = new CancelBatchRunRequest();
    private final CompleteBatchRunRequest completeBatchRunRequest = new CompleteBatchRunRequest();

    private final LastPriceResponse lastPriceResponse = new LastPriceResponse();
    private final StartBatchRunResponse batchStartedResponse = new StartBatchRunResponse();
    private final UploadChunkResponse uploadChunkResponse = new UploadChunkResponse();
    private final CancelBatchRunResponse cancelBatchRunResponse = new CancelBatchRunResponse();
    private final CompleteBatchRunResponse completeBatchRunResponse = new CompleteBatchRunResponse();

    public LastPriceServiceClient(
        int clientId,
        NanoClock nanoClock,
        AbstractServiceGateway serviceGateway,
        IdleStrategy idleStrategy,
        long waitTimeoutNs,
        RingBuffer inboundMessagesBuffer) {

        super(clientId, nanoClock, serviceGateway, idleStrategy, waitTimeoutNs, inboundMessagesBuffer);
        allocateMemoryForRequestMessages();
        wrapResponseBufferForRead();
    }

    /**
     * Requests last price for the given instrument.
     *
     * @param instrument instrument to receive price for
     * @return last price record
     * @throws ConnectionTimeoutException if response was not received in time
     */
    public PriceRecord requestLastPrice(CharSequence instrument) throws ConnectionTimeoutException {
        lastPriceRequest.instrument(instrument);
        makeCall(lastPriceRequest);
        final int status = lastPriceResponse.status();
        if (status != LastPriceResponse.SUCCESS_STATUS) {
            return null;
        }
        return lastPriceResponse.priceRecord();
    }

    /**
     * Send command to start a batch run.
     *
     * @return started batch id
     * @throws ConnectionTimeoutException if response was not received in time
     */
    public long startBatchRun() throws ConnectionTimeoutException {
        makeCall(startBatchRequest);
        return batchStartedResponse.batchId();
    }

    /**
     * Uploads given price records chunk to the service.
     *
     * @param batchRunId batch run id to upload the chunk to
     * @param priceRecordsChunk chunk to upload
     * @return {@code true} if chunk was uploaded, else {@code false}
     * @throws ConnectionTimeoutException if response was not received in time
     */
    public boolean uploadChunk(long batchRunId, PriceRecordsChunk priceRecordsChunk) throws ConnectionTimeoutException {
        uploadChunkRequest.batchId(batchRunId);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        makeCall(uploadChunkRequest);
        return uploadChunkResponse.status() == UploadChunkResponse.SUCCESS_STATUS;
    }

    /**
     * Cancels butch with the given id.
     *
     * @param batchRunId batch run id to cancel
     * @return {@code true} if batch was canceled, else {@code false}
     * @throws ConnectionTimeoutException if response was not received in time
     */
    public boolean cancelBatchRun(long batchRunId) throws ConnectionTimeoutException {
        cancelBatchRunRequest.batchId(batchRunId);
        makeCall(cancelBatchRunRequest);
        return cancelBatchRunResponse.status() == CancelBatchRunResponse.SUCCESS_STATUS;
    }

    /**
     * Completes batch run with the given id.
     * After this operation was applied price changes in batch should be visible for all consequent read requests.
     *
     * @param batchRunId batch run id to complete
     * @return {@code true} if batch was completed, else {@code false}
     * @throws ConnectionTimeoutException if response was not received in time
     */
    public boolean completeBatchRun(long batchRunId) throws ConnectionTimeoutException {
        completeBatchRunRequest.batchId(batchRunId);
        makeCall(completeBatchRunRequest);
        return completeBatchRunResponse.status() == CompleteBatchRunResponse.SUCCESS_STATUS;
    }

    private void allocateMemoryForRequestMessages() {
        allocateMemoryForMessage(lastPriceRequest);
        allocateMemoryForMessage(startBatchRequest);
        uploadChunkRequest.init(new UnsafeBuffer(allocateDirectAligned(UploadChunkRequest.maxSize(), CACHE_LINE_LENGTH)), 0);
        allocateMemoryForMessage(cancelBatchRunRequest);
        allocateMemoryForMessage(completeBatchRunRequest);
    }

    private void wrapResponseBufferForRead() {
        wrapResponseBufferForRead(lastPriceResponse);
        wrapResponseBufferForRead(batchStartedResponse);
        wrapResponseBufferForRead(uploadChunkResponse);
        wrapResponseBufferForRead(cancelBatchRunResponse);
        wrapResponseBufferForRead(completeBatchRunResponse);
    }
}
