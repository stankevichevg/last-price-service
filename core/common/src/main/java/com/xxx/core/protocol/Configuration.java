package com.xxx.core.protocol;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Configuration {

    private static final String MAX_CHANNEL_LENGTH_PROP = "connection.channel.max_length";
    public static final int MAX_CHANNEL_LENGTH = Integer.getInteger(MAX_CHANNEL_LENGTH_PROP, 256);

}
