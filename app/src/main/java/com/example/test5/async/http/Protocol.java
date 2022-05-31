package com.example.test5.async.http;

import java.util.Hashtable;

public enum Protocol {
    HTTP_1_0("http/1.0"),
    HTTP_1_1("http/1.1"),
    SPDY_3("spdy/3.1") {
    };

    private final String protocol;
    private static final Hashtable<String, Protocol> protocols = new Hashtable<String, Protocol>();

    static {
        protocols.put(SPDY_3.toString(), SPDY_3);
    }


    Protocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Returns the protocol identified by {@code protocol}.
     */
    public static Protocol get(String protocol) {
        if (protocol == null)
            return null;
        return protocols.get(protocol.toLowerCase());
    }

    /**
     * Returns the string used to identify this protocol for ALPN and NPN, like
     * "http/1.1", "spdy/3.1" or "h2-13".
     */
    @Override
    public String toString() {
        return protocol;
    }

}
