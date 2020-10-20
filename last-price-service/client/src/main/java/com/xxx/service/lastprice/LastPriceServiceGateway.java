package com.xxx.service.lastprice;

import com.xxx.core.client.AbstractServiceGateway;
import io.aeron.Aeron;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;

public class LastPriceServiceGateway extends AbstractServiceGateway<LastPriceServiceClient> {

    public LastPriceServiceGateway(
        Lock lock,
        Aeron aeron,
        NanoClock nanoClock,
        IdleStrategy readCycleIdleStrategy,
        String serverChannel,
        int serverStreamId,
        String clientChannel,
        int clientStreamId,
        int maxClients,
        Supplier<IdleStrategy> clientIdleStrategyFactory,
        ExecutorService executorService) {

        super(lock, aeron, nanoClock, readCycleIdleStrategy, serverChannel,
            serverStreamId, clientChannel, clientStreamId, maxClients, clientIdleStrategyFactory, executorService);
    }

    @Override
    protected LastPriceServiceClient createClient(int clientId, NanoClock nanoClock, IdleStrategy idleStrategy) {
        return new LastPriceServiceClient(
            clientId,
            nanoClock,
            this,
            idleStrategy,
            Configuration.CLIENT_WAIT_TIMEOUT,
            // TODO move it dipper to hide
            new OneToOneRingBuffer(new UnsafeBuffer(
                allocateDirectAligned(Configuration.CLIENT_INBOUND_BUFFER_SIZE + TRAILER_LENGTH, CACHE_LINE_LENGTH)
            ))
        );
    }
}
