package io.vertx.starter;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TweetConsumer extends AbstractVerticle {

    //storage of tweets
    private Queue<String> q = new ConcurrentLinkedQueue<>();

    //delay between two tweets
    private AtomicInteger delay = new AtomicInteger(5000);

    private AtomicLong timerId = new AtomicLong(0);

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        EventBus eventBus = vertx.eventBus();

        eventBus.consumer("to.consumer.JSON", h -> {
            JsonObject jsonObject = (JsonObject) h.body();
            publishMessagesIntoQueue(jsonObject);
        });

        eventBus.consumer("to.consumer.delay", h -> {
            int i = Integer.parseInt(h.body().toString());
            if (i > 0) {
                delay.set(i * 1000);

                //update timer's delay
                vertx.cancelTimer(timerId.get());
                setTimerWithDelay(eventBus);
            } else {
                //alert wrong delay input
                eventBus.publish("webpage", "error delay should be positive integer greater then 0");
            }
        });


        setTimerWithDelay(eventBus);
    }

    private void setTimerWithDelay(EventBus eventBus) {
        timerId.set(vertx.setPeriodic(delay.get(), h -> {
            if (q.size() < 5) {
                eventBus.publish("to.twitter.client", "provide tweets");
            } else {
                eventBus.publish("webpage", q.poll());
            }
        }));
    }

    private void publishMessagesIntoQueue(JsonObject entries) {
        JsonArray statuses = entries.getJsonArray("statuses");

        int size = statuses.size();
        System.out.println("publish " + size + " messages");
        for (int i = 0; i < size; i++) {

            String name = "@" + statuses.getJsonObject(i).getJsonObject("user").getString("screen_name");
            String text = statuses.getJsonObject(i).getString("text");

            q.add(name + " " + text);

        }
    }
}
