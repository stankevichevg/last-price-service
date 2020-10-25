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
     * @return
     * @throws ConnectionTimeoutException
     */
    public long startBatchRun() throws ConnectionTimeoutException {
        makeCall(startBatchRequest);
        return batchStartedResponse.batchId();
    }

    public boolean uploadChunk(long batchRunId, PriceRecordsChunk priceRecordsChunk) throws ConnectionTimeoutException {
        uploadChunkRequest.batchId(batchRunId);
        uploadChunkRequest.putChunk(priceRecordsChunk);
        makeCall(uploadChunkRequest);
        return uploadChunkResponse.status() == UploadChunkResponse.SUCCESS_STATUS;
    }

    public boolean cancelBatchRun(long batchRun) throws ConnectionTimeoutException {
        cancelBatchRunRequest.batchId(batchRun);
        makeCall(cancelBatchRunRequest);
        return cancelBatchRunResponse.status() == CancelBatchRunResponse.SUCCESS_STATUS;
    }

    public boolean completeBatchRun(long batchRun) throws ConnectionTimeoutException {
        completeBatchRunRequest.batchId(batchRun);
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
