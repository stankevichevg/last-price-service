package com.xxx.service.lastprice;


import com.xxx.core.client.ConnectionException;
import com.xxx.core.client.ConnectionTimeoutException;
import io.aeron.Aeron;
import org.HdrHistogram.Histogram;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.SystemNanoClock;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.xxx.service.lastprice.SampleConfiguration.CLIENT_CHANNEL;
import static com.xxx.service.lastprice.SampleConfiguration.CLIENT_STREAM_ID;
import static com.xxx.service.lastprice.SampleConfiguration.MAX_LOCAL_CLIENTS_NUMBER;
import static com.xxx.service.lastprice.SampleConfiguration.SERVER_CHANNEL;
import static com.xxx.service.lastprice.SampleConfiguration.SERVER_STREAM_ID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class StartCancelBatchRunPerformanceTest {

    private static final Histogram HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

    public static void main(String[] args) throws ConnectionException, InterruptedException {
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
            // heating up the client
            for (int i = 0; i < 200000; i++) {
                try {
                    final long batchId = client.startBatchRun();
                    client.cancelBatchRun(batchId);
                } catch (ConnectionTimeoutException e) {
                    // ignore
                }
            }
            // do performance test
            HISTOGRAM.reset();
            for (int i = 0; i < 100000; i++) {
                final long startNs = nanoClock.nanoTime();
                try {
                    final long batchId = client.startBatchRun();
                    client.cancelBatchRun(batchId);
                } catch (ConnectionTimeoutException e) {
                    // ignore
                } finally {
                    HISTOGRAM.recordValue(nanoClock.nanoTime() - startNs);
                }
            }
            HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
        }

    }

}
