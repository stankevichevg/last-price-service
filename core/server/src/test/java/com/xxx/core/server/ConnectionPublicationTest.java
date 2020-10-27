package com.xxx.core.server;

import com.xxx.core.protocol.Message;
import io.aeron.Publication;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static com.xxx.core.protocol.Message.MESSAGE_HEADER_LENGTH;
import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static io.aeron.Publication.CLOSED;
import static io.aeron.Publication.MAX_POSITION_EXCEEDED;
import static io.aeron.Publication.NOT_CONNECTED;
import static java.nio.ByteBuffer.allocateDirect;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ConnectionPublicationTest {

    private final DirectBuffer buffer = new UnsafeBuffer(allocateDirect(MESSAGE_HEADER_LENGTH));
    private final Message message = mock(Message.class);
    private static final TestMessage TEST_MESSAGE = new TestMessage();

    private final Publication publication = Mockito.mock(Publication.class);

    @BeforeAll
    public static void init() {
        allocateMemoryForMessage(TEST_MESSAGE);
    }

    @BeforeEach
    public void resetMocks() {
        reset(publication, message);
        when(message.getReadBuffer()).thenReturn(buffer);
        when(message.getOffset()).thenReturn(0);
        when(message.sizeInBytes()).thenReturn(MESSAGE_HEADER_LENGTH);
    }

    @Test
    public void whenPublicationClosedAndSendThenIllegalStateException() {
        when(publication.isClosed()).thenReturn(true);
        final ConnectionPublication connectionPublication = new ConnectionPublication(100L, publication);
        assertThrows(IllegalStateException.class, () -> {
            connectionPublication.sendResponse(TEST_MESSAGE, 1, 100500L);
        });
    }

    @Test
    public void shouldReturnAssignedConnectionId() {
        final ConnectionPublication connectionPublication = new ConnectionPublication(100500L, publication);
        assertThat(connectionPublication.getConnectionId(), is(100500L));
    }

    @Test
    public void shouldBeConnectedIfAeronPublicationConnected() {
        when(publication.isConnected()).thenReturn(true, false);
        final ConnectionPublication connectionPublication = new ConnectionPublication(100500L, publication);
        assertThat(connectionPublication.isConnected(), is(true));
        assertThat(connectionPublication.isConnected(), is(false));
    }

    @Test
    public void shouldCloseAeronPublication() {
        final ConnectionPublication connectionPublication = new ConnectionPublication(100500L, publication);
        connectionPublication.close();
        verify(publication).close();
    }

    @Test
    public void shouldPopulateSystemFields() {
        when(publication.offer(buffer, 0, MESSAGE_HEADER_LENGTH)).thenReturn(-2L, 10L);
        final ConnectionPublication connectionPublication = new ConnectionPublication(100500L, publication);
        connectionPublication.sendResponse(message, 123, 321L);
        verify(message).connectionId(100500L);
        verify(message).clientId(123);
        verify(message).correlationId(321L);
    }

    @Test
    public void shouldThrowIllegalStateExceptionIfPublicationIsClosed() {
        shouldThrowIllegalStateExceptionIfPublicationHasStatus(CLOSED);
    }

    @Test
    public void shouldThrowIllegalStateExceptionIfPublicationIsExceededMaxPosition() {
        shouldThrowIllegalStateExceptionIfPublicationHasStatus(MAX_POSITION_EXCEEDED);
    }

    @Test
    public void shouldThrowIllegalStateExceptionIfPublicationIsNotConnected() {
        shouldThrowIllegalStateExceptionIfPublicationHasStatus(NOT_CONNECTED);
    }

    private void shouldThrowIllegalStateExceptionIfPublicationHasStatus(long status) {
        when(publication.offer(buffer, 0, MESSAGE_HEADER_LENGTH)).thenReturn(status);
        final ConnectionPublication connectionPublication = new ConnectionPublication(100500L, publication);
        assertThrows(IllegalStateException.class, () -> {
            connectionPublication.sendResponse(message, 123, 321L);
        });
    }


    private static final class TestMessage extends Message {

        @Override
        public int uniqueType() {
            return -1;
        }

        @Override
        public int sizeInBytes() {
            return MESSAGE_HEADER_LENGTH;
        }
    }

}
