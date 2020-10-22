package com.xxx.core.protocol;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static com.xxx.core.protocol.SystemMessageTypes.CONNECTION_ACK_MESSAGE_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionAskResponseTest {

    private static final ConnectionAckResponse CONNECTION_ACK_RESPONSE = new ConnectionAckResponse();

    @BeforeAll
    public static void init() {
        allocateMemoryForMessage(CONNECTION_ACK_RESPONSE);
    }

    @Test
    public void uniqueTypeTest() {
        assertThat(CONNECTION_ACK_RESPONSE.uniqueType(), is(CONNECTION_ACK_MESSAGE_TYPE));
    }

    @Test
    public void sizeInBytesTest() {
        assertThat(CONNECTION_ACK_RESPONSE.sizeInBytes(), is(24));
    }

}
