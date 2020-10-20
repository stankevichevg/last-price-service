package com.xxx.core.client;

import java.io.IOException;

public class ConnectionTimeoutException extends IOException {
    public ConnectionTimeoutException(String message) {
        super(message);
    }
}
