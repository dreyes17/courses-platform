package com.example.courses.shared.security;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * The port the actuator server actually bound to. Taken from the running server rather than from
 * management.server.port, which can be 0 (random port) and would then never match a request.
 */
@Component
public class ManagementPort implements ApplicationListener<WebServerInitializedEvent> {

    private static final String MANAGEMENT_NAMESPACE = "management";

    private volatile int port = -1;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (MANAGEMENT_NAMESPACE.equals(event.getApplicationContext().getServerNamespace())) {
            port = event.getWebServer().getPort();
        }
    }

    public boolean matches(int localPort) {
        return port > 0 && port == localPort;
    }
}
