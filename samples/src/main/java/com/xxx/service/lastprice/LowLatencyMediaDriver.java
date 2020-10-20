package com.xxx.service.lastprice;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

import static org.agrona.SystemUtil.loadPropertiesFiles;


public class LowLatencyMediaDriver {
    public static void main(final String[] args) {
        loadPropertiesFiles(args);

        final MediaDriver.Context ctx = new MediaDriver.Context()
            .termBufferSparseFile(false)
            .useWindowsHighResTimer(true)
            .threadingMode(ThreadingMode.DEDICATED)
            .conductorIdleStrategy(BusySpinIdleStrategy.INSTANCE)
            .receiverIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE);

        try (MediaDriver ignored = MediaDriver.launch(ctx)) {
            new ShutdownSignalBarrier().await();
            System.out.println("Shutdown Driver...");
        }
    }
}
