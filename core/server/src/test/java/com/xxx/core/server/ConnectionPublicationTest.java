package com.xxx.core.server;

import com.xxx.core.protocol.Message;
import io.aeron.Publication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionPublicationTest {

    private static final TestMessage TEST_MESSAGE = new TestMessage();

    private final Publication publication = Mockito.mock(Publication.class);

    @BeforeAll
    public static void init() {
        allocateMemoryForMessage(TEST_MESSAGE);
    }

    @BeforeEach
    public void resetMocks() {
        reset(publication);
    }

    @Test
    public void whenPublicationClosedAndSendThenIllegalStateException() {
        when(publication.isClosed()).thenReturn(true);
        final ConnectionPublication connectionPublication = new ConnectionPublication(100L, publication);
        assertThrows(IllegalStateException.class, () -> {
            connectionPublication.sendResponse(TEST_MESSAGE, 1, 100500L);
        });
    }

    private static final class TestMessage extends Message {

        @Override
        protected int uniqueType() {
            return -1;
        }

        @Override
        public int sizeInBytes() {
            return MESSAGE_HEADER_LENGTH;
        }
    }

}
