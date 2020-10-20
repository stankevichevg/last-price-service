package com.xxx.core.client;

import com.xxx.core.protocol.Message;
import io.aeron.Publication;

import static io.aeron.Publication.CLOSED;
import static io.aeron.Publication.MAX_POSITION_EXCEEDED;

/**
 * Represents server publication identified by connection ID.
 * This publication propagates connection ID to all sent messages, see {@link #sendRequest(Message)}.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ServerPublication implements AutoCloseable {

    private final long connectionId;
    private final Publication publication;

    public ServerPublication(long connectionId, Publication publication) {
        this.connectionId = connectionId;
        this.publication = publication;
    }

    /**
     * Sends given message with the propagated {@link #connectionId}.
     *
     * @param message message to send
     */
    public void sendRequest(Message message) {
        if (publication.isClosed()) {
            throw new IllegalStateException();
        }
        long result;
        message.connectionId(connectionId);
        while ((result = publication.offer(message.getReadBuffer(), message.getOffset(), message.sizeInBytes())) < 0) {
            checkResult(result);
            Thread.yield();
        }
    }

    private void checkResult(final long result) {
        if (result == CLOSED || result == MAX_POSITION_EXCEEDED) {
            close();
            throw new IllegalStateException("Unexpected publication state: " + result);
        }
    }

    @Override
    public void close() {
        publication.close();
    }
}
