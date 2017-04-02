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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Server extends AbstractVerticle {

    private final int pSize = 100;

    private String ConsumerKey = "DX6ptkrAXL8iZv92IBNurhmm9";
    private String ConsumerSecret = "J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L";

    //tweet storage
    private final Queue<String> queue = new ConcurrentLinkedQueue<>();

    //for one by one requests
    private Semaphore semaphore = new Semaphore(1);

    private String btoken;

    //start query to search
    private String query = "#love";

    private AtomicInteger delay = new AtomicInteger(5000);

    private AtomicInteger Request15minReained = new AtomicInteger(480);

    private AtomicLong last15minWindowTime = new AtomicLong(0);

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
            System.out.println("message is: " + message.body());

            String[] split = message.body().toString().split(" ");

            //determine message type
            if (split[0].equals("query")) {
                query = split[1];
                queue.clear();
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




    private void configTwitterSource() throws InterruptedException {

        //15 min window
        vertx.setPeriodic(60 * 15 * 1000, s -> {
            Request15minReained.set(480);
            last15minWindowTime.set(System.currentTimeMillis());
        });

        WebClient wclient = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
                        .setTrustAll(true)
                        .setKeepAlive(false)
        );

        String BearerTokenCredentials = ConsumerKey + ":" + ConsumerSecret;
        String base64ebtc = base64encode(BearerTokenCredentials);

        oneMoreReqest();

        authtoken(wclient, base64ebtc);


        //start queue filling
        Thread thread = new Thread(() -> {
            try {
                while (true) {
                    if (btoken == null || btoken.equals("")) {
                        System.out.println("btoken");
                        authtoken(wclient, base64ebtc);
                        Thread.sleep(1000);
                        continue;
                    }
                    if (queue.size() > 6) {
                        System.out.println("size 6 sleep reason");
                        System.out.println("req remained" + Request15minReained.get());
                        Thread.sleep(1000);
                        continue;
                    }

                    if (!isRequestReamined()) {
                        System.out.println("no reqeus remained");
                        vertx.eventBus().publish("webpage", "error wait please " +
                                (System.currentTimeMillis() - last15minWindowTime.get()));
                        Thread.sleep(System.currentTimeMillis() - last15minWindowTime.get() + 1000);
                        continue;
                    }


                    if(!semaphore.tryAcquire()){
                        continue;
                    }

                    //query to search
                    System.out.println("req");
                    String eq = encodedQuery();

                    //decrement request counter
                    oneMoreReqest();
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

    private void authtoken(WebClient wclient, String base64ebtc) {
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
                    } else {
                        ar.cause().printStackTrace();
                    }
                });
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
        System.out.println("publish " + size + " messages");
        for (int i = 0; i < size; i++) {

            String name = "@" + statuses.getJsonObject(i).getJsonObject("user").getString("screen_name");
            String text = statuses.getJsonObject(i).getString("text");

            queue.add(name + " " + text);

        }
    }

    private String getTweet() {
        return queue.poll();
    }

    boolean isRequestReamined() {
        return Request15minReained.get() != 0;
    }

    void oneMoreReqest() {
        Request15minReained.decrementAndGet();
    }

    private String base64encode(String toencodeString) {
        return new String(Base64.getEncoder().encode(toencodeString.getBytes()));
    }

}
