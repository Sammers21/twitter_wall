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

public class Server extends AbstractVerticle {

    private final int pSize = 100;

    private String ConsumerKey = "DX6ptkrAXL8iZv92IBNurhmm9";
    private String ConsumerSecret = "J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L";

    //tweet storage
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(pSize);

    private String btoken;

    //start query to search
    private String query = "#love";

    private int delay = 1000;

    @Override
    public void start() throws Exception {

        EventBus eb = vertx.eventBus();

        //SockJS bridge
        Router router = Router.router(vertx);
        BridgeOptions opts = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddress("webpage"));

        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());


        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);


        configTwitterSource("#Dota2");


        //sending into event bus tweets
        vertx.setPeriodic(delay, v -> {
            String tweet = getTweet();
            eb.publish("webpage", tweet);
            System.out.println("sended: " + tweet);

        });

    }


    private void configTwitterSource(String queryToSearch) throws InterruptedException {


        WebClient wclient = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
                        .setTrustAll(true)
                        .setKeepAlive(false)
        );

        String BearerTokenCredentials = ConsumerKey + ":" + ConsumerSecret;
        String base64ebtc = base64encode(BearerTokenCredentials);
        Semaphore semaphore = new Semaphore(1);

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
                        Thread.sleep(delay);
                        continue;
                    }
                    semaphore.acquire();
                    String queryy = queryToSerch();
                    wclient
                            .get(443, "api.twitter.com", "/1.1/search/tweets.json" + queryy)
                            .putHeader("Authorization", "Bearer " + btoken)
                            .send(ar -> {
                                if (ar.succeeded()) {
                                    HttpResponse<Buffer> response = ar.result();
                                    System.out.println("Got HTTP response with status " + response.statusCode());
                                    print_in_nice(response.bodyAsJsonObject());
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

    private String queryToSerch() {
        String q = null;
        try {
            q = "?q=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return q;
    }

    private void print_in_nice(JsonObject entries) {
        JsonArray statuses = entries.getJsonArray("statuses");
        int size = statuses.size();
        for (int i = 0; i < size; i++) {
            String name = "@" + statuses.getJsonObject(i).getJsonObject("user").getString("screen_name");
            String text = statuses.getJsonObject(i).getString("text");
            System.out.println("name " + name);
            System.out.println("body " + text);
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
