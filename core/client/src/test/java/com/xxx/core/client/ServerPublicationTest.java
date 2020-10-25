package com.xxx.core.client;

import com.xxx.core.protocol.Message;
import io.aeron.Publication;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static com.xxx.core.protocol.Message.MESSAGE_HEADER_LENGTH;
import static java.nio.ByteBuffer.allocateDirect;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class ServerPublicationTest {

    private final DirectBuffer buffer = new UnsafeBuffer(allocateDirect(MESSAGE_HEADER_LENGTH));

    private final Message message = mock(Message.class);
    private final Publication publication = mock(Publication.class);

    private final ServerPublication serverPublication = new ServerPublication(100500L, publication);

    @BeforeEach
    public void setUp() {
        reset(message, publication);
        when(message.getReadBuffer()).thenReturn(buffer);
        when(message.getOffset()).thenReturn(0);
        when(message.sizeInBytes()).thenReturn(MESSAGE_HEADER_LENGTH);
    }

    @Test
    public void shouldPropagateConnectionId() {
        when(publication.offer(buffer, 0, MESSAGE_HEADER_LENGTH)).thenReturn((long) MESSAGE_HEADER_LENGTH);
        serverPublication.sendRequest(message);
        verify(message).connectionId(100500L);
    }

    @Test
    public void shouldThrowIllegalArgExceptionIfClosed() {
        when(publication.offer(buffer, 0, MESSAGE_HEADER_LENGTH)).thenReturn(Publication.CLOSED);
        assertThrows(IllegalStateException.class, () -> {
            serverPublication.sendRequest(message);
        });
    }

    @Test
    public void shouldThrowIllegalArgExceptionIfMaxPosition() {
        when(publication.offer(buffer, 0, MESSAGE_HEADER_LENGTH)).thenReturn(Publication.MAX_POSITION_EXCEEDED);
        assertThrows(IllegalStateException.class, () -> {
            serverPublication.sendRequest(message);
        });
    }

    @Test
    public void shouldSendRightData() {
        serverPublication.sendRequest(message);
        verify(publication).offer(buffer, 0, MESSAGE_HEADER_LENGTH);
    }

}
