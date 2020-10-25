package com.xxx.core.client;

import com.xxx.core.protocol.ConnectionAckResponse;
import com.xxx.core.protocol.Message;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.xxx.core.protocol.Message.MESSAGE_HEADER_LENGTH;
import static java.nio.ByteBuffer.allocateDirect;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionControlClientTest {

    private static final long CONNECTION_ID = 100500L;
    private static final long CORRELATION_ID = 111L;
    private static final String CLIENT_CHANNEL = "aeron:udp?endpoint=localhost:40123";
    private static final int CLIENT_STREAM_ID = 1;

    private final AbstractServiceGateway gateway = mock(AbstractServiceGateway.class);
    private final NanoClock nanoClock = mock(NanoClock.class);

    private final RingBuffer inboundBuffer = new OneToOneRingBuffer(new UnsafeBuffer(
        allocateDirectAligned(1024 + TRAILER_LENGTH, CACHE_LINE_LENGTH)
    ));
    private final MutableDirectBuffer responseBuffer = new UnsafeBuffer(allocateDirect(ConnectionAckResponse.MESSAGE_SIZE));
    private final ConnectionAckResponse connectionAckResponse = new ConnectionAckResponse();

    private final ConnectionControlClient connectionControlClient = new ConnectionControlClient(
        CLIENT_CHANNEL, CLIENT_STREAM_ID, nanoClock, gateway, BusySpinIdleStrategy.INSTANCE, 100L, inboundBuffer
    );

    @BeforeEach
    public void setUp() {
        reset(gateway, nanoClock);
        when(gateway.nextCorrelationId()).thenReturn(CORRELATION_ID);
        when(nanoClock.nanoTime()).thenReturn(0L, Long.MAX_VALUE);
        connectionAckResponse.wrapForWrite(responseBuffer, 0);
    }

    @Test
    public void shouldReturnReceivedConnectionId() throws ConnectionTimeoutException {
        connectionAckResponse.clientId(-1);
        connectionAckResponse.connectionId(CONNECTION_ID);
        connectionAckResponse.correlationId(CORRELATION_ID);
        inboundBuffer.write(
            connectionAckResponse.uniqueType(),
            connectionAckResponse.getReadBuffer(),
            connectionAckResponse.getOffset(),
            connectionAckResponse.sizeInBytes()
        );
        final long connectionId = connectionControlClient.connect();
        assertThat(connectionId, is(CONNECTION_ID));
    }

}
