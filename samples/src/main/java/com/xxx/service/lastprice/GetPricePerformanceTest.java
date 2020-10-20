package com.xxx.service.lastprice;


import com.xxx.core.client.ConnectionException;
import com.xxx.core.client.ConnectionTimeoutException;
import com.xxx.core.protocol.Flyweight;
import com.xxx.service.lastprice.messages.PriceRecord;
import com.xxx.service.lastprice.messages.PriceRecordsChunk;
import io.aeron.Aeron;
import org.HdrHistogram.Histogram;
import org.agrona.MutableDirectBuffer;
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
public class GetPricePerformanceTest {

    private static final Random RANDOM = new Random();
    private static final MutableDirectBuffer PAYLOAD_BUFFER;

    private static final List<String> INSTRUMENTS = new ArrayList<>();

    private static final Histogram LAST_PRICE_HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

    static {
        INSTRUMENTS.add("AIR");
        INSTRUMENTS.add("TEAM");
        INSTRUMENTS.add("NEE");
        INSTRUMENTS.add("SAF");
        INSTRUMENTS.add("TKWY");
        PAYLOAD_BUFFER = new UnsafeBuffer(allocateDirectAligned(SIZE_OF_INT, CACHE_LINE_LENGTH));
    }

    public static void main(String[] args) throws ConnectionException, InterruptedException, ConnectionTimeoutException {

        final PriceRecordsChunk priceRecordsChunk = new PriceRecordsChunk();
        Flyweight.allocateMemoryForFlyweight(priceRecordsChunk, PriceRecordsChunk.defineSize(1000));

        final NanoClock nanoClock = SystemNanoClock.INSTANCE;
        final EpochClock epochClock = SystemEpochClock.INSTANCE;
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

            prepareServiceData(client, priceRecordsChunk, epochClock);

            // try to receive prices and print them
            for (int i = 0; i < INSTRUMENTS.size(); i++) {
                final String instrument = INSTRUMENTS.get(i);
                printPrice(instrument, client.requestLastPrice(instrument));
            }

            // heat up the system
            for (int i = 0; i < 1_000_000; i++) {
                client.requestLastPrice(INSTRUMENTS.get(i % INSTRUMENTS.size()));
            }

            LAST_PRICE_HISTOGRAM.reset();
            for (int i = 0; i < 100_000; i++) {
                final long startNs = nanoClock.nanoTime();
                client.requestLastPrice(INSTRUMENTS.get(i % INSTRUMENTS.size()));
                LAST_PRICE_HISTOGRAM.recordValue(nanoClock.nanoTime() - startNs);
            }
            LAST_PRICE_HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
        }

    }

    private static void printPrice(String instrument, PriceRecord priceRecord) {
        if (priceRecord != null) {
            priceRecord.getPayload(PAYLOAD_BUFFER, 0);
            System.out.println("Instrument: " + priceRecord.instrument() +
                ", asOf: " + priceRecord.asOfTimestamp() +
                ", price: " + PAYLOAD_BUFFER.getInt(0));
        } else {
            System.out.println("Price was not found for: " + instrument);
        }
    }

    private static void prepareServiceData(LastPriceServiceClient client, PriceRecordsChunk priceRecordsChunk, EpochClock epochClock) throws ConnectionTimeoutException {
        priceRecordsChunk.reset();
        final long batchId = client.startBatchRun();
        for (String instrument : INSTRUMENTS) {
            final int price = RANDOM.nextInt(100);
            PAYLOAD_BUFFER.putInt(0, price);
            priceRecordsChunk.addRecord(instrument, epochClock.time(), PAYLOAD_BUFFER, 0, PAYLOAD_BUFFER.capacity());
        }
        client.uploadChunk(batchId, priceRecordsChunk);
        client.completeBatchRun(batchId);
    }
}
