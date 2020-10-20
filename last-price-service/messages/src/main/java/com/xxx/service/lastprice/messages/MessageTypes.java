package com.xxx.service.lastprice.messages;

public final class MessageTypes {

    public static final int START_BATCH_REQUEST_MESSAGE_TYPE = 1001;
    public static final int START_BATCH_RESPONSE_MESSAGE_TYPE = 1002;

    public static final int UPLOAD_CHUNK_REQUEST_MESSAGE_TYPE = 1003;
    public static final int UPLOAD_CHUNK_RESPONSE_MESSAGE_TYPE = 1004;

    public static final int CANCEL_BATCH_REQUEST_MESSAGE_TYPE = 1005;
    public static final int CANCEL_BATCH_RESPONSE_MESSAGE_TYPE = 1006;

    public static final int COMPLETE_BATCH_REQUEST_MESSAGE_TYPE = 1007;
    public static final int COMPLETE_BATCH_RESPONSE_MESSAGE_TYPE = 1008;

    public static final int LAST_PRICE_REQUEST_MESSAGE_TYPE = 1009;
    public static final int LAST_PRICE_RESPONSE_MESSAGE_TYPE = 1010;

    private MessageTypes() {
    }
}
