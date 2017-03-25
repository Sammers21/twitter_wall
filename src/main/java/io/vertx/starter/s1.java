package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.eventbus.EventBus;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class s1 extends AbstractVerticle {

  private final int pSize = 100;

  private BlockingQueue<String> queue = new LinkedBlockingQueue<String>(pSize);

  @Override
  public void start() throws Exception {

    EventBus eb = vertx.eventBus();
    DeploymentOptions options = new DeploymentOptions().setWorker(true);

    vertx.deployVerticle(r1.class.getCanonicalName(), options, res -> {
      if (res.succeeded()) {
        System.out.println("receiver deployed successful");
      } else if (res.failed()) {
        System.out.println("receiver deployment failed");
      }
    });

    configTwitterSource();


    vertx.setPeriodic(5000, v -> {
      String tweet = getTweet();
      eb.publish("twitter dota", tweet);
      System.out.println("sended: " + tweet);

    });
  }

  private void configTwitterSource() {
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
          Query query = new Query("#Dota2");
          QueryResult result;
          do {
            result = twitter.search(query);
            List<Status> tweets = result.getTweets();
            for (Status tweet : tweets) {
              //  System.out.println("@" + tweet.getUser().getScreenName() + " - " + tweet.getText());
              queue.put("@" + tweet.getUser().getScreenName() + " - " + tweet.getText());
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
