package com.xxx.core.protocol;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.xxx.core.protocol.Message.allocateMemoryForMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class MessageTest {

    private static final TestMessage TEST_MESSAGE = new TestMessage();

    @BeforeAll
    public static void init() {
        allocateMemoryForMessage(TEST_MESSAGE);
    }

    @Test
    public void whenWriteMessageHeaderFieldsThenReadThemCorrectly() {
        TEST_MESSAGE.type(111);
        TEST_MESSAGE.clientId(100500);
        TEST_MESSAGE.connectionId(100501L);
        TEST_MESSAGE.correlationId(100502L);
        assertThat(TEST_MESSAGE.type(), is(111));
        assertThat(TEST_MESSAGE.clientId(), is(100500));
        assertThat(TEST_MESSAGE.connectionId(), is(100501L));
        assertThat(TEST_MESSAGE.correlationId(), is(100502L));
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
