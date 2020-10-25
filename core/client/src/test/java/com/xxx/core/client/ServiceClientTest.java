package com.xxx.core.client;

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
import org.mockito.Mockito;

import static com.xxx.core.protocol.Message.CORRELATION_ID_FIELD_OFFSET;
import static com.xxx.core.protocol.Message.MESSAGE_HEADER_LENGTH;
import static java.nio.ByteBuffer.allocateDirect;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ServiceClientTest {

    private static final int CLIENT_ID = 123;
    private static final long CORRELATION_ID = 111L;

    private final AbstractServiceGateway gateway = mock(AbstractServiceGateway.class);
    private final NanoClock nanoClock = mock(NanoClock.class);

    private final RingBuffer inboundBuffer = new OneToOneRingBuffer(new UnsafeBuffer(
        allocateDirectAligned(1024 + TRAILER_LENGTH, CACHE_LINE_LENGTH)
    ));
    private final DirectBuffer requestBuffer = new UnsafeBuffer(allocateDirect(MESSAGE_HEADER_LENGTH));
    private final MutableDirectBuffer responseBuffer = new UnsafeBuffer(allocateDirect(MESSAGE_HEADER_LENGTH));
    private final Message request = mock(Message.class);
    private final Message response = mock(Message.class);

    private final BaseServiceClient client = new BaseServiceClient(
        CLIENT_ID, nanoClock, gateway, BusySpinIdleStrategy.INSTANCE, 100L, inboundBuffer
    );

    @BeforeEach
    public void setUp() {
        reset(gateway, nanoClock, request, response);

        when(request.getReadBuffer()).thenReturn(requestBuffer);
        when(request.getOffset()).thenReturn(0);
        when(request.sizeInBytes()).thenReturn(MESSAGE_HEADER_LENGTH);

        when(response.getReadBuffer()).thenReturn(responseBuffer);
        when(response.getOffset()).thenReturn(0);
        when(response.sizeInBytes()).thenReturn(MESSAGE_HEADER_LENGTH);

        when(gateway.nextCorrelationId()).thenReturn(CORRELATION_ID);
        when(nanoClock.nanoTime()).thenReturn(0L, Long.MAX_VALUE);
    }

    @Test
    public void shouldSendRequestToGateway() throws ConnectionTimeoutException {
        putResponseToInboundBuffer(CORRELATION_ID);
        client.makeCall(request);
        verify(gateway).send(request);
    }

    @Test
    public void shouldReplyWithTimeoutIfThereWasNoAnswer() throws ConnectionTimeoutException {
        when(nanoClock.nanoTime()).thenReturn(0L, 50L, Long.MAX_VALUE);
        assertThrows(ConnectionTimeoutException.class, () -> {
            client.makeCall(request);
            verify(gateway).send(request);
        });
    }

    @Test
    public void shouldAskGatewayToCloseItself() {
        client.close();
        verify(gateway).closeClient(client);
    }

    @Test
    public void shouldWrapResponseBufferToReadResponseData() throws ConnectionTimeoutException {
        putResponseToInboundBuffer(CORRELATION_ID);
        client.makeCall(request);
        final Response response = new Response();
        client.wrapResponseBufferForRead(response);
        assertThat(response.correlationId(), is(CORRELATION_ID));
    }

    @Test
    public void shouldReturnAssignedClientId() {
        assertThat(client.getId(), is(CLIENT_ID));
    }

    private void putResponseToInboundBuffer(long correlationId) {
        responseBuffer.putLong(CORRELATION_ID_FIELD_OFFSET, correlationId);
        client.receiveMessage(1, response.getReadBuffer(), response.getOffset(), response.sizeInBytes());
    }

    private static final class Response extends Message {

        @Override
        public int uniqueType() {
            return 1;
        }

        @Override
        public int sizeInBytes() {
            return MESSAGE_HEADER_LENGTH;
        }
    }

}
