package com.xxx.service.lastprice;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class SampleConfiguration {

    public static final String SERVER_CHANNEL_PROP = "server.channel";
    public static final String SERVER_CHANNEL = System.getProperty(SERVER_CHANNEL_PROP, "aeron:udp?endpoint=localhost:40124");

    public static final String SERVER_STREAM_ID_PROP = "server.streamId";
    public static final int SERVER_STREAM_ID = Integer.getInteger(SERVER_STREAM_ID_PROP, 2001);

    public static final String CLIENT_CHANNEL_PROP = "client.channel";
    public static final String CLIENT_CHANNEL = System.getProperty(CLIENT_CHANNEL_PROP, "aeron:udp?endpoint=localhost:40123");

    public static final String CLIENT_STREAM_ID_PROP = "client.streamId";
    public static final int CLIENT_STREAM_ID = Integer.getInteger(CLIENT_STREAM_ID_PROP, 2002);

    public static final String MAX_LOCAL_CLIENTS_NUMBER_PROP = "client.max_clients_number";
    public static final int MAX_LOCAL_CLIENTS_NUMBER = Integer.getInteger(MAX_LOCAL_CLIENTS_NUMBER_PROP, 10);

}
