package com.xxx.service.lastprice;

import com.xxx.core.server.Server;
import com.xxx.core.server.ServiceHandler;
import io.aeron.Aeron;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SystemEpochClock;

import java.util.HashSet;
import java.util.Set;

import static com.xxx.service.lastprice.SampleConfiguration.SERVER_CHANNEL;
import static com.xxx.service.lastprice.SampleConfiguration.SERVER_STREAM_ID;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class LastPriceServiceRunner {

    public static void main(String[] args) {
        final EpochClock epochClock = SystemEpochClock.INSTANCE;
        final ServiceHandler handler = createServiceHandler(epochClock);
        final IdleStrategy serverIdleStrategy = BusySpinIdleStrategy.INSTANCE;
        try (Aeron aeron = Aeron.connect();
             Server server = new Server(epochClock, aeron, SERVER_CHANNEL, SERVER_STREAM_ID, serverIdleStrategy, handler)) {
            server.start();
        }
    }

    private static ServiceHandler createServiceHandler(EpochClock epochClock) {
        final Set<String> instruments = new HashSet<>();
        instruments.add("AIR");
        instruments.add("TEAM");
        instruments.add("NEE");
        instruments.add("SAF");
        instruments.add("TKWY");
        instruments.add("VOW");
        instruments.add("RDSA");
        return new LastPriceServiceHandler(epochClock, instruments);
    }

}
