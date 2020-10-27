package com.xxx.service.lastprice;


import com.xxx.core.client.ConnectionException;
import com.xxx.core.client.ConnectionTimeoutException;
import com.xxx.core.protocol.Flyweight;
import com.xxx.service.lastprice.messages.PriceRecordsChunk;
import io.aeron.Aeron;
import org.HdrHistogram.Histogram;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.SystemNanoClock;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.xxx.service.lastprice.SampleConfiguration.CLIENT_CHANNEL;
import static com.xxx.service.lastprice.SampleConfiguration.CLIENT_STREAM_ID;
import static com.xxx.service.lastprice.SampleConfiguration.MAX_LOCAL_CLIENTS_NUMBER;
import static com.xxx.service.lastprice.SampleConfiguration.SERVER_CHANNEL;
import static com.xxx.service.lastprice.SampleConfiguration.SERVER_STREAM_ID;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class UploadChunksPerformanceTest {

    private static final Random RANDOM = new Random();
    private static final List<String> INSTRUMENTS = new ArrayList<>();
    private static final MutableDirectBuffer PAYLOAD_BUFFER;

    private static final IdleStrategy RETRY_OPERATION_IDLE_STRATEGY = new BackoffIdleStrategy();

    static {
        INSTRUMENTS.add("AIR");
        INSTRUMENTS.add("TEAM");
        INSTRUMENTS.add("NEE");
        INSTRUMENTS.add("SAF");
        INSTRUMENTS.add("TKWY");
        PAYLOAD_BUFFER = new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));
    }

    private static final Histogram START_BATCH_HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
    private static final Histogram UPLOAD_CHUNK_HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);
    private static final Histogram COMPLETE_BATCH_HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

    public static void main(String[] args) throws ConnectionException, InterruptedException {

        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1000));

        final NanoClock nanoClock = SystemNanoClock.INSTANCE;
        final Supplier<IdleStrategy> busySpinIdleStrategyFactory = () -> BusySpinIdleStrategy.INSTANCE;
        try (Aeron aeron = Aeron.connect();
             final LastPriceServiceGateway priceServiceGateway = new LastPriceServiceGateway(
                 new ReentrantLock(),
                 aeron,
                 nanoClock,
                 busySpinIdleStrategyFactory.get(),
                 SERVER_CHANNEL, SERVER_STREAM_ID,
                 CLIENT_CHANNEL, CLIENT_STREAM_ID,
                 MAX_LOCAL_CLIENTS_NUMBER,
                 busySpinIdleStrategyFactory,
                 Executors.newSingleThreadExecutor()
             )
        ) {
            priceServiceGateway.connect();
            final LastPriceServiceClient client = priceServiceGateway.getClient();
            // heating up the client
            for (int i = 0; i < 100000; i++) {
                try {
                    final long batchId = client.startBatchRun();
                    client.completeBatchRun(batchId);
                } catch (ConnectionTimeoutException e) {
                    System.out.println(e);
                    // ignore
                }
            }
            // do performance test
            START_BATCH_HISTOGRAM.reset();
            UPLOAD_CHUNK_HISTOGRAM.reset();
            COMPLETE_BATCH_HISTOGRAM.reset();
            for (int i = 0; i < 10000; i++) {
                final long batchId;
                try {
                    final long startNs = nanoClock.nanoTime();
                    batchId = client.startBatchRun();
                    START_BATCH_HISTOGRAM.recordValue(nanoClock.nanoTime() - startNs);
                } catch (ConnectionTimeoutException e) {
                    break;
                }
                final long startNs = nanoClock.nanoTime();
                try {
                    prepareRandomPricesChunk(priceRecordsChunk);
                    client.uploadChunk(batchId, priceRecordsChunk);
                } catch (ConnectionTimeoutException e) {
                    // ignore
                } finally {
                    UPLOAD_CHUNK_HISTOGRAM.recordValue(nanoClock.nanoTime() - startNs);
                    final long startNsCompleteBatch = nanoClock.nanoTime();
                    completeBatchRun(client, batchId);
                    COMPLETE_BATCH_HISTOGRAM.recordValue(nanoClock.nanoTime() - startNsCompleteBatch);
                }
            }
            System.out.println("START_BATCH_HISTOGRAM");
            START_BATCH_HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
            System.out.println("UPLOAD_CHUNK_HISTOGRAM");
            UPLOAD_CHUNK_HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
            System.out.println("COMPLETE_BATCH_HISTOGRAM");
            COMPLETE_BATCH_HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
        }

    }

    private static void completeBatchRun(LastPriceServiceClient client, long batchId) {
        try {
            client.completeBatchRun(batchId);
        } catch (ConnectionTimeoutException e) {
            // ignore
        }
    }

    private static void prepareRandomPricesChunk(PriceRecordsChunk priceRecordsChunk) {
        priceRecordsChunk.reset();
        for (int instrumentIndex = 0; instrumentIndex < 5; instrumentIndex++) {
            final String instrument = INSTRUMENTS.get(instrumentIndex);
            long timestamp = 0;
            for (int i = 0; i < 200; i++) {
                final int price = RANDOM.nextInt(100);
                PAYLOAD_BUFFER.putInt(0, price);
                priceRecordsChunk.addRecord(instrument, timestamp + i, PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
            }
        }
    }

}
