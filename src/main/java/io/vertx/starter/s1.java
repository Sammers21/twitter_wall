package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.vertx.ext.web.Router;

public class s1 extends AbstractVerticle {

  private final int pSize = 100;

  private BlockingQueue<String> queue = new LinkedBlockingQueue<String>(pSize);

  @Override
  public void start() throws Exception {

    EventBus eb = vertx.eventBus();

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


    vertx.setPeriodic(1000, v -> {
      String tweet = getTweet();
      eb.publish("twitter dota", tweet);
      eb.publish("webpage", tweet);
      System.out.println("sended: " + tweet);

    });

  }


  private void configTwitterSource(String queryToSearch) {
    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey("DX6ptkrAXL8iZv92IBNurhmm9")
      .setOAuthConsumerSecret("J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L")
      .setOAuthAccessToken("519198946-TQUfCrIUfQKz8R6FBUYE2IFheb4Ht7qkyTv8uz0h")
      .setOAuthAccessTokenSecret("15TjyJsBNMmHdQaxCUfyfa3JxdFMVp6ui2klgxOUtLJQP");


    Thread thread = new Thread(() -> {
      Twitter twitter = new TwitterFactory(cb.build()).getInstance();
      try {
        while (true) {
          Query query = new Query(queryToSearch);
          QueryResult result;
          do {
            result = twitter.search(query);
            List<Status> tweets = result.getTweets();
            for (Status tweet : tweets) {
              //  System.out.println("@" + tweet.getUser().getScreenName() + " - " + tweet.getText());
              queue.put("@" + tweet.getUser().getScreenName() + " " + tweet.getText());
            }
          } while ((query = result.nextQuery()) != null);
        }
      } catch (TwitterException te) {
        te.printStackTrace();
        System.out.println("Failed to search tweets: " + te.getMessage());
        System.exit(-1);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    thread.start();
  }

  private String getTweet() {
    return queue.poll();
  }

}
