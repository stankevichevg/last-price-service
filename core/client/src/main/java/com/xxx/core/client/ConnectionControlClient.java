package com.xxx.core.client;

import com.xxx.core.protocol.Message;
import com.xxx.core.protocol.ConnectionAckResponse;
import com.xxx.core.protocol.CreateConnectionCommand;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.ringbuffer.RingBuffer;

/**
 * Client to send administration commands to server. This class for internal use only.
 * At the moment it's used to send connection request to server and receive assigned connection identifier.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionControlClient extends AbstractServiceClient {

    protected static final int CONNECTION_CONTROL_CLIENT_ID = -1;

    private final CreateConnectionCommand createConnectionCommand = new CreateConnectionCommand();
    private final ConnectionAckResponse connectionAckResponse = new ConnectionAckResponse();

    ConnectionControlClient(
        String clientChannel,
        int clientStreamId,
        NanoClock nanoClock,
        AbstractServiceGateway serviceGateway,
        IdleStrategy idleStrategy,
        long waitTimeoutNs,
        RingBuffer inboundMessagesBuffer) {

        super(CONNECTION_CONTROL_CLIENT_ID, nanoClock, serviceGateway, idleStrategy, waitTimeoutNs, inboundMessagesBuffer);
        wrapResponseBufferForRead(connectionAckResponse);
        Message.allocateMemoryForMessage(createConnectionCommand);
        createConnectionCommand.clientChannel(clientChannel);
        createConnectionCommand.clientStreamId(clientStreamId);
    }

    /**
     * Sends connection request.
     *
     * @return assigned by the server connection ID, this value should be sent in each subsequent requests.
     * @throws ConnectionTimeoutException if response was not received in time
     */
    long connect() throws ConnectionTimeoutException {
        makeCall(createConnectionCommand);
        return connectionAckResponse.connectionId();
    }

}
