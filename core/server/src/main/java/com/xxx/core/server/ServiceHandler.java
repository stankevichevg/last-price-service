package com.xxx.core.server;

import com.xxx.core.protocol.Message;
import org.agrona.DirectBuffer;

import java.util.function.LongSupplier;

/**
 * Handler for incoming requests. Implementation should contain service business logic.
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public interface ServiceHandler {

    /**
     * Handles incoming service request, do business logic and responds to the client.
     *
     * @param messageType message type
     * @param idGenerator generates long IDs
     * @param buffer buffer to read request data from
     * @param offset start of the read buffer
     * @param length length of the request message
     */
    Message handleRequest(
        int messageType,
        LongSupplier idGenerator,
        DirectBuffer buffer,
        int offset,
        int length);

}
