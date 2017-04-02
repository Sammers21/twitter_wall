package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends AbstractVerticle {

    private final int pSize = 100;

    private String ConsumerKey = "DX6ptkrAXL8iZv92IBNurhmm9";
    private String ConsumerSecret = "J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L";

    //tweet storage
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(pSize);

    //for one by one requests
    private Semaphore semaphore = new Semaphore(1);

    private String btoken;

    //start query to search
    private String query = "#love";

    private AtomicInteger delay = new AtomicInteger(5000);

    @Override
    public void start() throws Exception {

        EventBus eb = vertx.eventBus();

        //SockJS bridge
        Router router = Router.router(vertx);
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("webpage"));

        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());


        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);

        configTwitterSource();

        //dynamic periodic event
        Runnable r = () -> {
            while (true) {
                try {
                    Thread.sleep(delay.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String tweet = getTweet();
                //sending into event bus tweets
                eb.publish("webpage", tweet);
                System.out.println("sended: " + tweet);
            }
        };
        new Thread(r).start();


        eb.consumer("server").handler(message -> {
            // Send the message back out to all clients with the timestamp prepended.
            System.out.println("message is: "+message.body());

            String[] split = message.body().toString().split(" ");

            //determine message type
            if (split[0].equals("query")) {
                query = split[1];
                changeQuery();
            }

            if (split[0].equals("delay")) {
                int i = Integer.parseInt(split[1]);
                if (i > 0) {
                    delay.set(i * 1000);
                } else {
                    //alert wrong delay input
                    eb.publish("webpage", "error delay should be positive integer greater then 0");
                }
            }

        });

    }

    private void changeQuery() {
        try {
            semaphore.acquire();
            //clear old queries
            queue.clear();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();
        }
    }


    private void configTwitterSource() throws InterruptedException {


        WebClient wclient = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
                        .setTrustAll(true)
                        .setKeepAlive(false)
        );

        String BearerTokenCredentials = ConsumerKey + ":" + ConsumerSecret;
        String base64ebtc = base64encode(BearerTokenCredentials);

        semaphore.acquire();
        Buffer buffer = Buffer.buffer("grant_type=client_credentials");
        wclient
                .post(443, "api.twitter.com", "/oauth2/token")
                .putHeader("Authorization", "Basic " + base64ebtc)
                .putHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8.")
                .sendBuffer(buffer, (AsyncResult<HttpResponse<Buffer>> ar) -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        System.out.println("Got HTTP response with status " + response.statusCode());
                        System.out.println(response.bodyAsString());
                        System.out.println(response.bodyAsJsonObject().getString("access_token"));
                        btoken = response.bodyAsJsonObject().getString("access_token");
                        semaphore.release();
                    } else {
                        ar.cause().printStackTrace();
                    }
                });


        //start queue filling
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    if (queue.size() > 6) {
                        Thread.sleep(delay.get());
                        continue;
                    }
                    semaphore.acquire();
                    String eq = encodedQuery();
                    wclient
                            .get(443, "api.twitter.com", "/1.1/search/tweets.json" + eq)
                            .putHeader("Authorization", "Bearer " + btoken)
                            .send(ar -> {
                                if (ar.succeeded()) {
                                    HttpResponse<Buffer> response = ar.result();
                                    System.out.println("Got HTTP response with status " + response.statusCode());
                                    publishMessagesIntoQueue(response.bodyAsJsonObject());
                                } else {
                                    ar.cause().printStackTrace();
                                }
                                semaphore.release();
                            });

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        thread.start();
    }

    private String encodedQuery() {
        String q = null;
        try {
            q = "?q=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.out.println("encode error");
            e.printStackTrace();
        }
        return q;
    }

    private void publishMessagesIntoQueue(JsonObject entries) {
        JsonArray statuses = entries.getJsonArray("statuses");

        int size = statuses.size();
        for (int i = 0; i < size; i++) {

            String name = "@" + statuses.getJsonObject(i).getJsonObject("user").getString("screen_name");
            String text = statuses.getJsonObject(i).getString("text");

            try {
                queue.put(name + " " + text);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    private String getTweet() {
        return queue.poll();
    }

    private String base64encode(String toencodeString) {
        return new String(Base64.getEncoder().encode(toencodeString.getBytes()));
    }

}
