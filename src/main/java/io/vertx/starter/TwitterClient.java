package io.vertx.starter;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TwitterClient extends AbstractVerticle {

    //#TODO: replace this keys with yours
    private String ConsumerKey = "DX6ptkrAXL8iZv92IBNurhmm9";
    private String ConsumerSecret = "J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L";

    private String btoken = "";

    //start query to search
    private String query = "#love";

    //to prevent big amount of unexpected requests to API
    private Semaphore semaphore = new Semaphore(10);


    private AtomicInteger reqCount = new AtomicInteger(450);

    //last tie of reqCount refreshment
    private AtomicLong rateLimitReset = new AtomicLong(System.currentTimeMillis() / 1000 + 15 * 60);

    @Override
    public void start(Future<Void> startFuture) throws Exception {


        WebClient wclient = WebClient.create(vertx,
                new WebClientOptions()
                        .setSsl(true)
                        .setTrustAll(true)
                        .setKeepAlive(false)
        );

        //for token refreshment
        vertx.setPeriodic(5000, h -> {
            if (btoken == null || btoken.equals("")) {
                make_auth(wclient);
            }
        });

        EventBus eventBus = vertx.eventBus();

        eventBus.consumer("to.twitter.client", h -> {
            String[] split = h.body().toString().split(" ");

            //if consumer ask for some tweets
            if (split[0].equals("provide")
                    && semaphore.tryAcquire()
                    && ableToRequest()) {
                provideToConsumer(wclient, eventBus);
                //or if message is about search query update
            } else if (split[0].equals("query")) {
                query = split[1];
                System.out.println("new query is " + query);
                eventBus.publish("consumer.force.clean.queue", "clean queue please");
            }
        });

    }

    private void provideToConsumer(WebClient wclient, EventBus eventBus) {
        String eq = encodedQuery();
        wclient
                .get(443, "api.twitter.com", "/1.1/search/tweets.json" + eq)
                .putHeader("Authorization", "Bearer " + btoken)
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        System.out.println("Got HTTP response with status " + response.statusCode());
                        //send to consumer tweets
                        if (response.statusCode() == 200) {
                            MultiMap headers = response.headers();
                            rateLimitReset.set(Integer.parseInt(headers.get("x-rate-limit-reset")));
                            reqCount.set(Integer.parseInt(headers.get("x-rate-limit-remaining")));
                            eventBus.publish("to.consumer.JSON", response.bodyAsJsonObject());
                        } else {
                            //force token to refresh
                            btoken = null;
                        }
                    } else {
                        ar.cause().printStackTrace();
                    }
                    reqInfo();
                    semaphore.release();
                });
    }


    private void make_auth(WebClient wclient) {
        String base64ebtc = base64encode();
        Buffer buffer = Buffer.buffer("grant_type=client_credentials");
        wclient
                .post(443, "api.twitter.com", "/oauth2/token")
                .putHeader("Authorization", "Basic " + base64ebtc)
                .putHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8.")
                .sendBuffer(buffer, (AsyncResult<HttpResponse<Buffer>> ar) -> {
                    if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        System.out.println("Got HTTP response with status " + response.statusCode());
                        if (response.statusCode() == 200) {
                            System.out.println(response.bodyAsString());
                            System.out.println(response.bodyAsJsonObject().getString("access_token"));
                            btoken = response.bodyAsJsonObject().getString("access_token");
                        } else {
                            System.out.println("can't auth");
                        }
                    } else {
                        ar.cause().printStackTrace();
                    }
                    reqInfo();
                });
    }


    private String base64encode() {
        String BearerTokenCredentials = ConsumerKey + ":" + ConsumerSecret;
        return new String(Base64.getEncoder().encode(BearerTokenCredentials.getBytes()));
    }

    private void reqInfo() {
        reqCount.decrementAndGet();
        System.out.println("requests remained " + reqCount.get());
        System.out.println("Seconds to wait before refresh " +
                ((rateLimitReset.get() - System.currentTimeMillis()) / 1000));
    }

    private boolean ableToRequest() {
        boolean b = reqCount.get() > 0;
        if (!b) {
            vertx.eventBus()
                    .publish("webpage",
                            "notice " +
                                    "Seconds to wait before refresh " +
                                    ((rateLimitReset.get() * 1000 - System.currentTimeMillis()) / 1000));

        }
        return b;
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
}
