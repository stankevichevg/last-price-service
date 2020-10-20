package com.xxx.service.lastprice;

import java.util.concurrent.TimeUnit;

/**
 *
 * TODO split it ti client and server configuration and move to appropriate module
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class Configuration {

    private static final String MAX_CHUNK_SIZE_PROP = "properties.chunk.max_size";
    public static final int MAX_CHUNK_SIZE = Integer.getInteger(MAX_CHUNK_SIZE_PROP, 1000);

    private static final String MAX_ACTIVE_BATCHES_NUMBER_PROP = "properties.batch.max_active_number";
    public static final int MAX_ACTIVE_BATCHES_NUMBER = Integer.getInteger(MAX_ACTIVE_BATCHES_NUMBER_PROP, 100);

    private static final String PRICE_MAX_PAYLOAD_SIZE_PROP = "properties.price.max_payload_size";
    public static final int PRICE_MAX_PAYLOAD_SIZE = Integer.getInteger(PRICE_MAX_PAYLOAD_SIZE_PROP, 16);

    private static final String INSTRUMENT_MAX_TICKER_LENGTH_PROP = "properties.instrument.max_ticker_length";
    public static final int INSTRUMENT_MAX_TICKER_LENGTH = Integer.getInteger(INSTRUMENT_MAX_TICKER_LENGTH_PROP, 10);

    private static final String BATCH_EVICTION_TIMEOUT_PROP = "properties.batch.eviction_timeout";
    public static final long BATCH_EVICTION_TIMEOUT = Integer.getInteger(BATCH_EVICTION_TIMEOUT_PROP, 5000);

    private static final String CLIENT_WAIT_TIMEOUT_PROP = "properties.client.wait_timeout";
    public static final long CLIENT_WAIT_TIMEOUT = Integer.getInteger(CLIENT_WAIT_TIMEOUT_PROP, (int) TimeUnit.MILLISECONDS.toNanos(300));

    private static final String CLIENT_INBOUND_BUFFER_SIZE_PROP = "properties.client.inbound_buffer_size";
    public static final int CLIENT_INBOUND_BUFFER_SIZE = Integer.getInteger(CLIENT_INBOUND_BUFFER_SIZE_PROP, 2048);

}
