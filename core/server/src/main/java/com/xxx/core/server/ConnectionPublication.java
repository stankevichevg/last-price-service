package com.xxx.core.server;

import com.xxx.core.protocol.Message;
import io.aeron.Publication;

import static io.aeron.Publication.CLOSED;
import static io.aeron.Publication.MAX_POSITION_EXCEEDED;
import static io.aeron.Publication.NOT_CONNECTED;

/**
 * Publication for connection. Allows to send a message back to the clients.
 * Propagates connection and client IDs.
 * This object is created as a result of connection process.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionPublication implements AutoCloseable {

    private final long connectionId;
    private final Publication publication;

    ConnectionPublication(long connectionId, Publication publication) {
        this.connectionId = connectionId;
        this.publication = publication;
    }

    public boolean isConnected() {
        return publication.isConnected();
    }

    public long getConnectionId() {
        return connectionId;
    }

    /**
     * Sends response to the client with the given ID.
     *
     * @param message message to send
     * @param clientId client ID
     * @param correlationId request correlation ID
     */
    public void sendResponse(Message message, int clientId, long correlationId) {
        if (publication.isClosed()) {
            throw new IllegalStateException();
        }
        long result;
        message.connectionId(connectionId);
        message.clientId(clientId);
        message.correlationId(correlationId);
        while ((result = publication.offer(message.getReadBuffer(), message.getOffset(), message.sizeInBytes())) < 0) {
            checkResult(result);
            Thread.yield();
        }
    }

    private void checkResult(final long result) {
        if (result == CLOSED || result == MAX_POSITION_EXCEEDED || result == NOT_CONNECTED) {
            close();
            throw new IllegalStateException("Unexpected publication state: " + result);
        }
    }

    @Override
    public void close() {
        publication.close();
    }

}
