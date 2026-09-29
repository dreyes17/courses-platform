package com.example.courses.web;

import com.example.courses.TestcontainersConfiguration;
import org.apache.tomcat.util.threads.VirtualThreadExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spring.threads.virtual.enabled switches three thread sources to virtual threads: Tomcat's request executor,
 * the RabbitMQ listener containers and the scheduler that drives the outbox relay. Checks each one by running
 * work on it, not by inspecting configuration.
 * <p>
 * Same annotations as {@link ManagementPortTest} on purpose, so both reuse one application context with real
 * servers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "app.jwt.secret=test-only-secret-at-least-32-bytes-long!"
})
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class VirtualThreadsTest {

    @Autowired
    private WebServerApplicationContext context;
    @Autowired
    private SimpleRabbitListenerContainerFactory listenerContainerFactory;
    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    void httpRequestsAreServedOnVirtualThreads() {
        var tomcat = ((TomcatWebServer) context.getWebServer()).getTomcat();

        assertThat(tomcat.getConnector().getProtocolHandler().getExecutor()).isInstanceOf(VirtualThreadExecutor.class);
    }

    @Test
    void rabbitListenersConsumeOnVirtualThreads() throws Exception {
        // The application's @RabbitListener containers all come from this factory.
        assertThat(listenerRegistry.getListenerContainers()).isNotEmpty();
        Queue probe = amqpAdmin.declareQueue();
        CompletableFuture<Thread> consumerThread = new CompletableFuture<>();
        var endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("virtual-threads-probe");
        endpoint.setQueueNames(probe.getName());
        endpoint.setMessageListener(message -> consumerThread.complete(Thread.currentThread()));
        SimpleMessageListenerContainer container = listenerContainerFactory.createListenerContainer(endpoint);
        container.start();
        try {
            rabbitTemplate.convertAndSend("", probe.getName(), "probe");

            assertThat(consumerThread.get(10, TimeUnit.SECONDS).isVirtual()).isTrue();
        } finally {
            container.stop();
            amqpAdmin.deleteQueue(probe.getName());
        }
    }

    @Test
    void scheduledTasksLikeTheOutboxRelayRunOnVirtualThreads() throws Exception {
        CompletableFuture<Thread> taskThread = new CompletableFuture<>();

        taskScheduler.schedule(() -> taskThread.complete(Thread.currentThread()), Instant.now());

        assertThat(taskThread.get(5, TimeUnit.SECONDS).isVirtual()).isTrue();
    }
}
