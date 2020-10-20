package com.xxx.core.client;

import com.xxx.core.client.pool.PoolInstance;
import com.xxx.core.protocol.Message;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

/**
 * This class is not thread safe. Use {@link AbstractServiceGateway#getClient()} to create client for thread.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class AbstractServiceClient implements PoolInstance, AutoCloseable {

    protected final int clientId;
    private final NanoClock nanoClock;
    private final AbstractServiceGateway serviceGateway;
    private final ResponsePoller responsePoller;
    private final long waitTimeoutNs;

    private final RingBuffer inboundMessagesBuffer;

    public AbstractServiceClient(
        int clientId,
        NanoClock nanoClock,
        AbstractServiceGateway serviceGateway,
        IdleStrategy idleStrategy,
        long waitTimeoutNs,
        RingBuffer inboundMessagesBuffer) {

        this.clientId = clientId;
        this.nanoClock = nanoClock;
        this.serviceGateway = serviceGateway;
        this.responsePoller = new ResponsePoller(nanoClock, idleStrategy, inboundMessagesBuffer);
        this.waitTimeoutNs = waitTimeoutNs;
        this.inboundMessagesBuffer = inboundMessagesBuffer;
    }

    @Override
    public int getId() {
        return clientId;
    }

    /**
     * Wraps poller buffer by presented decoder message structure.
     *
     * @param decoder decoder to wrap buffer
     */
    protected void wrapResponseBufferForRead(Message decoder) {
        decoder.wrapForRead(responsePoller.buffer, 0);
    }

    /**
     * Makes call of a service method. This operation consists of the following steps:
     *
     * 1. Generates correlation ID.
     * 2. Propagate generated correlation ID and client ID to the request message
     * 3. Sends request
     * 4. Poll for incoming request (main read cycle should deliver it to client's {@link #inboundMessagesBuffer})
     * 5. Validate call status. {@link #checkForErrors} is used for validation. Can be overridden.
     *
     * @param request request to send
     * @return call status
     * @throws ConnectionTimeoutException if there was no response received in time
     */
    protected CallStatus makeCall(final Message request) throws ConnectionTimeoutException {
        final long correlationId = startOperation(request);
        serviceGateway.send(request);
        final long nanoTime = nanoClock.nanoTime();
        final long deadline = nanoTime + waitTimeoutNs;
        final CallStatus callStatus = responsePoller.pollNextMessage(deadline, correlationId);
        checkForErrors(callStatus);
        return callStatus;
    }

    /**
     * Callback to deliver responses.
     *
     * @param messageType message type
     * @param buffer buffer to read the message from
     * @param offset offset to read message from
     * @param length message length
     */
    void receiveMessage(int messageType, DirectBuffer buffer, int offset, int length) {
        final boolean written = inboundMessagesBuffer.write(messageType, buffer, offset, length);
    }

    private long startOperation(Message request) {
        final long correlationId = serviceGateway.nextCorrelationId();
        request.clientId(clientId);
        request.correlationId(correlationId);
        return correlationId;
    }

    /**
     * Default strategy of call status validation.
     *
     * @param callStatus call status
     * @throws ConnectionTimeoutException if timeout has occurred
     */
    protected void checkForErrors(CallStatus callStatus) throws ConnectionTimeoutException {
        if (callStatus == CallStatus.TIMEOUT) {
            throw new ConnectionTimeoutException("Client did not receive server acknowledge in time");
        }
    }

    /**
     * Just return the client to the clients pool of gateway.
     */
    @Override
    public void close() {
        serviceGateway.closeClient(this);
    }

    /**
     * Poller used by client to poll result with expecting correlationId in caller thread from the inbound ring bound.
     * When a message is received it's written to internal buffer to be read by the client.
     *
     * @see #pollNextMessage
     */
    protected class ResponsePoller implements MessageHandler {

        private static final int MAX_RESPONSE_SIZE = 512;

        private final NanoClock nanoClock;
        private final IdleStrategy idleStrategy;
        private final RingBuffer ringBuffer;

        private final MutableDirectBuffer buffer;
        private int messageType;
        private int length;

        private ResponsePoller(NanoClock nanoClock, IdleStrategy idleStrategy, RingBuffer ringBuffer) {
            this.buffer = new UnsafeBuffer(allocateDirectAligned(MAX_RESPONSE_SIZE, CACHE_LINE_LENGTH));
            this.nanoClock = nanoClock;
            this.idleStrategy = idleStrategy;
            this.ringBuffer = ringBuffer;
        }

        /**
         * Actively waits response polling ring buffer for next message with the given correlationId.
         *
         * @param deadlineNs deadline until which to poll for the message
         * @param correlationId expected correlation id
         * @return call status
         */
        public CallStatus pollNextMessage(final long deadlineNs, final long correlationId) {
            idleStrategy.reset();
            while (true) {
                final boolean messageReceived = ringBuffer.read(this, 1) == 1;
                if (messageReceived && (readCorrelationId() == correlationId)) {
                    return CallStatus.RESPONSE_RECEIVED;
                }
                final long nanoTime = nanoClock.nanoTime();
                if (deadlineNs - nanoTime < 0) {
                    return CallStatus.TIMEOUT;
                }
                idleStrategy.idle();
            }
        }

        @Override
        public void onMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int index, final int length) {
            this.messageType = msgTypeId;
            this.buffer.putBytes(0, buffer, index, length);
            this.length = length;
        }

        private long readCorrelationId() {
            return buffer.getLong(Message.CORRELATION_ID_FIELD_OFFSET);
        }
    }

    /**
     * Result that is propagated from the {@link ResponsePoller}
     */
    protected enum CallStatus {
        RESPONSE_RECEIVED,
        TIMEOUT
    }

}
