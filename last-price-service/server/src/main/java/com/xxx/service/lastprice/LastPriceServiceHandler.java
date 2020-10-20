package com.xxx.service.lastprice;

import com.xxx.core.protocol.Message;
import com.xxx.core.server.ServiceHandler;
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
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.Set;
import java.util.function.LongSupplier;

import static com.xxx.core.protocol.Flyweight.allocateMemoryForFlyweight;
import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static com.xxx.service.lastprice.Configuration.BATCH_EVICTION_TIMEOUT;
import static com.xxx.service.lastprice.Configuration.MAX_ACTIVE_BATCHES_NUMBER;
import static com.xxx.service.lastprice.messages.LastPriceResponse.WRONG_INSTRUMENT_STATUS;
import static com.xxx.service.lastprice.messages.MessageTypes.CANCEL_BATCH_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.MessageTypes.COMPLETE_BATCH_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.MessageTypes.LAST_PRICE_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.MessageTypes.START_BATCH_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.MessageTypes.UPLOAD_CHUNK_REQUEST_MESSAGE_TYPE;
import static com.xxx.service.lastprice.messages.StartBatchRunResponse.CAN_NOT_CREATE_BATCH_STATUS;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class LastPriceServiceHandler implements ServiceHandler {

    private final MarketState marketState;
    private final InstrumentIndexer instrumentIndexer;
    private final BatchRunRepository batchRunRepository;

    private final BatchRun operationalBatchRun;

    private final LastPriceRequest getLastPriceRequest = new LastPriceRequest();
    private final StartBatchRunRequest startBatchRunRequest = new StartBatchRunRequest();
    private final UploadChunkRequest uploadChunkRequest = new UploadChunkRequest();
    private final CancelBatchRunRequest cancelBatchRunRequest = new CancelBatchRunRequest();
    private final CompleteBatchRunRequest completeBatchRunRequest = new CompleteBatchRunRequest();

    private final LastPriceResponse lastPriceResponse = new LastPriceResponse();
    private final StartBatchRunResponse startBatchRunResponse = new StartBatchRunResponse();
    private final UploadChunkResponse uploadChunkResponse = new UploadChunkResponse();
    private final CancelBatchRunResponse cancelBatchRunResponse = new CancelBatchRunResponse();
    private final CompleteBatchRunResponse completeBatchRunResponse = new CompleteBatchRunResponse();

    public LastPriceServiceHandler(EpochClock epochClock, Set<String> instruments) {
        this.marketState = new MarketState(instruments.size());
        this.instrumentIndexer = new InstrumentIndexer(instruments);
        this.batchRunRepository = new BatchRunRepositoryImpl(epochClock, MAX_ACTIVE_BATCHES_NUMBER);
        this.operationalBatchRun = createOperationalBatchRun(instruments.size());
        allocateMemoryForMessages();
        allocateMemoryForFlyweight(marketState);
    }

    @Override
    public Message handleRequest(
        final int messageType,
        final LongSupplier idGenerator,
        final DirectBuffer buffer,
        final int offset,
        final int length) {

        try {
            switch (messageType) {
                case LAST_PRICE_REQUEST_MESSAGE_TYPE:
                    getLastPriceRequest.wrapForRead(buffer, offset);
                    return onLastPrice(getLastPriceRequest);
                case START_BATCH_REQUEST_MESSAGE_TYPE:
                    startBatchRunRequest.wrapForRead(buffer, offset);
                    return onStartBatchRun(startBatchRunRequest, idGenerator);
                case CANCEL_BATCH_REQUEST_MESSAGE_TYPE:
                    cancelBatchRunRequest.wrapForRead(buffer, offset);
                    return onCancelBatchRun(cancelBatchRunRequest);
                case UPLOAD_CHUNK_REQUEST_MESSAGE_TYPE:
                    uploadChunkRequest.wrapForRead(buffer, offset);
                    return onUploadChunk(uploadChunkRequest);
                case COMPLETE_BATCH_REQUEST_MESSAGE_TYPE:
                    completeBatchRunRequest.wrapForRead(buffer, offset);
                    return onCompleteBatch(completeBatchRunRequest);
                default:
                    throw new IllegalArgumentException("Unsupported message type");
            }
        } finally {
            // on each message try to clean up some resources
            cleanUpCycle();
        }
    }

    private LastPriceResponse onLastPrice(LastPriceRequest lastPriceRequest) {
        final CharSequence instrument = lastPriceRequest.instrument();
        final int instrumentIndex = instrumentIndexer.defineIndex(instrument);
        if (instrumentIndex < 0) {
            // index not found
            lastPriceResponse.status(WRONG_INSTRUMENT_STATUS);
        } else {
            final IndexedPriceRecord record = marketState.getPriceRecord(instrumentIndex);
            // price was not initialized, see method com.xxx.service.lastprice.MarketState.reset
            if (record.timestamp() == 0L) {
                lastPriceResponse.status(LastPriceResponse.PRICE_NOT_AVAILABLE_STATUS);
            } else {
                lastPriceResponse.status(LastPriceResponse.SUCCESS_STATUS);
                final PriceRecord priceRecord = lastPriceResponse.priceRecordForWrite();
                priceRecord.instrument(instrument);
                priceRecord.asOfTimestamp(record.timestamp());
                priceRecord.putPayload(record.getReadBuffer(), record.absolutePayloadOffset(), record.payloadSize());
            }
        }
        return lastPriceResponse;
    }

    public StartBatchRunResponse onStartBatchRun(StartBatchRunRequest request, LongSupplier idGenerator) {
        if (batchRunRepository.size() == MAX_ACTIVE_BATCHES_NUMBER) {
            startBatchRunResponse.status(CAN_NOT_CREATE_BATCH_STATUS);
        } else {
            final long batchId = idGenerator.getAsLong();
            batchRunRepository.create(batchId);
            startBatchRunResponse.status(StartBatchRunResponse.SUCCESS_STATUS);
            startBatchRunResponse.batchId(batchId);
        }
        return startBatchRunResponse;
    }

    private UploadChunkResponse onUploadChunk(UploadChunkRequest uploadChunkRequest) {
        final long batchId = uploadChunkRequest.batchId();
        final BatchRun batchRun = batchRunRepository.get(batchId);
        if (batchRun == null) {
            uploadChunkResponse.status(UploadChunkResponse.BATCH_RUN_NOT_FOUND_STATUS);
        } else {
            operationalBatchRun.reset();
            final PriceRecordsChunk chunk = uploadChunkRequest.getChunkToRead();
            boolean chunkProcessed = true;
            for (int index = 0; index < chunk.recordsNumber(); index++) {
                final PriceRecord priceRecord = chunk.priceRecord(index);
                final int instrumentIndex = instrumentIndexer.defineIndex(priceRecord.instrument());
                if (instrumentIndex < 0) {
                    uploadChunkResponse.status(UploadChunkResponse.INSTRUMENT_NOT_FOUND_STATUS);
                    chunkProcessed = false;
                    break;
                } else {
                    operationalBatchRun.tryUpdateRecord(
                        instrumentIndex, priceRecord.asOfTimestamp(),
                        priceRecord.getReadBuffer(), priceRecord.absolutePayloadOffset(), priceRecord.payloadSize()
                    );
                }
            }
            if (chunkProcessed) {
                operationalBatchRun.mergeTo(batchRun);
                batchRunRepository.save(batchRun);
            }
        }
        return uploadChunkResponse;
    }

    private CompleteBatchRunResponse onCompleteBatch(CompleteBatchRunRequest completeBatchRunRequest) {
        final long batchId = completeBatchRunRequest.batchId();
        final BatchRun batchRun = batchRunRepository.get(batchId);
        if (batchRun == null) {
            completeBatchRunResponse.status(CompleteBatchRunResponse.BATCH_RUN_NOT_FOUND_STATUS);
        } else {
            batchRun.mergeTo(marketState);
            batchRunRepository.remove(batchRun);
            completeBatchRunResponse.status(CompleteBatchRunResponse.SUCCESS_STATUS);
        }
        return completeBatchRunResponse;
    }

    private CancelBatchRunResponse onCancelBatchRun(CancelBatchRunRequest cancelBatchRunRequest) {
        final long batchId = cancelBatchRunRequest.batchId();
        final BatchRun batchRun = batchRunRepository.get(batchId);
        if (batchRun != null) {
            batchRunRepository.remove(batchRun);
        }
        cancelBatchRunResponse.status(CancelBatchRunResponse.SUCCESS_STATUS);
        return cancelBatchRunResponse;
    }

    private void cleanUpCycle() {
        batchRunRepository.removeOutdated(BATCH_EVICTION_TIMEOUT);
    }

    private BatchRun createOperationalBatchRun(int instrumentsNumber) {
        final BatchRun operationalBatchRun = new BatchRun(-1, instrumentsNumber);
        final MutableDirectBuffer buffer = new UnsafeBuffer(allocateDirectAligned(operationalBatchRun.sizeInBytes(), CACHE_LINE_LENGTH));
        operationalBatchRun.wrapForWrite(buffer, 0);
        return operationalBatchRun;
    }

    private void allocateMemoryForMessages() {
        allocateMemoryForMessage(lastPriceResponse);
        allocateMemoryForMessage(startBatchRunResponse);
        allocateMemoryForMessage(uploadChunkResponse);
        allocateMemoryForMessage(cancelBatchRunResponse);
        allocateMemoryForMessage(completeBatchRunResponse);
    }

}
