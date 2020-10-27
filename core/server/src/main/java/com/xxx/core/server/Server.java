package com.xxx.core.server;

import com.xxx.core.protocol.ConnectionAckResponse;
import com.xxx.core.protocol.CreateConnectionCommand;
import com.xxx.core.protocol.Message;
import com.xxx.core.protocol.SystemMessageTypes;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Server implements AutoCloseable {

    private static final long CLIENT_CONNECTION_TIMEOUT = 1000;

    private static final int FRAGMENT_LIMIT = 10;
    private static final IdleStrategy WAIT_PUB_SUB_IDLE_STRATEGY = new SleepingMillisIdleStrategy(10);

    private final CreateConnectionCommand createConnectionCommand = new CreateConnectionCommand();
    private final ConnectionAckResponse connectionAckResponse = new ConnectionAckResponse();

    private final Aeron aeron;
    private final EpochClock epochClock;
    private final IdleStrategy serverIdleStrategy;

    private final String serverChannel;
    private final int serverStreamId;

    private final Long2ObjectHashMap<ConnectionPublication> connections = new Long2ObjectHashMap<>();
    private final FragmentAssembler assembler = new FragmentAssembler(this::onMessage);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ServiceHandler serviceHandler;

    public Server(EpochClock epochClock, Aeron aeron, String serverChannel, int serverStreamId,
                  IdleStrategy serverIdleStrategy, ServiceHandler serviceHandler) {
        this.epochClock = epochClock;
        this.aeron = aeron;
        this.serverChannel = serverChannel;
        this.serverStreamId = serverStreamId;
        this.serverIdleStrategy = serverIdleStrategy;
        this.serviceHandler = serviceHandler;
        Message.allocateMemoryForMessage(connectionAckResponse);
    }

    public void start() {
        SigInt.register(this::close);
        serverIdleStrategy.reset();
        try (final Subscription subscription = aeron.addSubscription(serverChannel, serverStreamId)) {
            while (running.get()) {
                serverIdleStrategy.idle(subscription.poll(assembler, FRAGMENT_LIMIT));
            }
        }
    }

    private void onMessage(final DirectBuffer buffer, final int offset, final int length, final Header header) {
        final int messageType = buffer.getInt(offset + Message.TYPE_FIELD_OFFSET);
        if (messageType == SystemMessageTypes.CREATE_CONNECTION_MESSAGE_TYPE) {
            createConnectionCommand.wrapForRead(buffer, offset);
            onConnect(createConnectionCommand);
        } else {
            final long connectionId = buffer.getLong(offset + Message.CONNECTION_ID_FIELD_OFFSET);
            final int clientId = buffer.getInt(offset + Message.CLIENT_ID_FIELD_OFFSET);
            final long correlationId = buffer.getLong(offset + Message.CORRELATION_ID_FIELD_OFFSET);
            final ConnectionPublication publication = connections.get(connectionId);
            if (publication != null) {
                try {
                    final Message response = serviceHandler.handleRequest(
                        messageType,
                        aeron::nextCorrelationId,
                        buffer, offset, length
                    );
                    publication.sendResponse(response, clientId, correlationId);
                } catch (Throwable e) {
                    // at the moment do not try to understand the problem, just close client publication.
                    // not very graceful solution, but fast =)
                    e.printStackTrace();
                    publication.close();
                    connections.remove(connectionId);
                }
            }
        }
    }

    private void onConnect(CreateConnectionCommand createConnectionCommand) {
        final ConnectionPublication connectionPublication = createConnection(
            createConnectionCommand.clientChannel(), createConnectionCommand.clientStreamId()
        );
        if (connectionPublication != null) {
            try {
                connectionPublication.sendResponse(
                    connectionAckResponse,
                    createConnectionCommand.clientId(),
                    createConnectionCommand.correlationId()
                );
                connections.put(connectionPublication.getConnectionId(), connectionPublication);
            } catch (Throwable e) {
                e.printStackTrace();
                connectionPublication.close();
            }
        }
    }

    private ConnectionPublication createConnection(String clientChannel, int clientStreamId) {
        final long connectionId = aeron.nextCorrelationId();
        final Publication publication = createClientPublication(clientChannel, clientStreamId, CLIENT_CONNECTION_TIMEOUT);
        return publication != null ? new ConnectionPublication(connectionId, publication) : null;
    }

    private Publication createClientPublication(String clientChannel, int streamId, long timeout) {
        final Publication publication = aeron.addPublication(clientChannel, streamId);
        final long startTime = epochClock.time();
        if (!publication.isConnected()) {
            WAIT_PUB_SUB_IDLE_STRATEGY.reset();
            while (!publication.isConnected()) {
                WAIT_PUB_SUB_IDLE_STRATEGY.idle();
                if (epochClock.time() - startTime > timeout) {
                    publication.close();
                    return null;
                }
            }
        }
        return publication;
    }

    @Override
    public void close() {
        running.set(false);
        for (ConnectionPublication publication : connections.values()) {
            publication.close();
        }
    }
}
