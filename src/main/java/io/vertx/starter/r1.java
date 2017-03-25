package io.vertx.starter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;

public class r1 extends AbstractVerticle {
    @Override
    public void start() throws Exception {
        EventBus eb = vertx.eventBus();

        eb.consumer("twitter dota", message -> {
            System.out.println("Received on consumer : " + message.body());
        });
    }
}
