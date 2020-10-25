package com.xxx.core.protocol;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.xxx.core.protocol.Configuration.MAX_CHANNEL_LENGTH;
import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static com.xxx.core.protocol.SystemMessageTypes.CONNECTION_ACK_MESSAGE_TYPE;
import static com.xxx.core.protocol.SystemMessageTypes.CREATE_CONNECTION_MESSAGE_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class CreateConnectionCommandTest {

    private static final String TEST_CLIENT_CHANNEL = "aeron:udp?endpoint=localhost:40124";
    private static final CreateConnectionCommand CREATE_CONNECTION_COMMAND = new CreateConnectionCommand();

    @BeforeAll
    public static void init() {
        allocateMemoryForMessage(CREATE_CONNECTION_COMMAND);
    }

    @Test
    public void uniqueTypeTest() {
        assertThat(CREATE_CONNECTION_COMMAND.uniqueType(), is(CREATE_CONNECTION_MESSAGE_TYPE));
    }

    @Test
    public void sizeInBytesTest() {
        assertThat(CREATE_CONNECTION_COMMAND.sizeInBytes(), is(28 + MAX_CHANNEL_LENGTH));
    }

    @Test
    public void whenWriteFieldsThenReadThemCorrectly() {
        CREATE_CONNECTION_COMMAND.clientChannel(TEST_CLIENT_CHANNEL);
        CREATE_CONNECTION_COMMAND.clientStreamId(100500);
        assertThat(CREATE_CONNECTION_COMMAND.clientChannel(), is(TEST_CLIENT_CHANNEL));
        assertThat(CREATE_CONNECTION_COMMAND.clientStreamId(), is(100500));
    }

}
