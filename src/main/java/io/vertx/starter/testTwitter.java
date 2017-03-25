package io.vertx.starter;

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.util.List;


public class testTwitter {
    public static void run(String consumerKey, String consumerSecret, String token, String secret) throws InterruptedException {

        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey(consumerKey)
                .setOAuthConsumerSecret(consumerSecret)
                .setOAuthAccessToken(token)
                .setOAuthAccessTokenSecret(secret);

        Twitter twitter = new TwitterFactory(cb.build()).getInstance();
        try {
            Query query = new Query("#Dota2");
            QueryResult result;
            do {
                result = twitter.search(query);
                List<Status> tweets = result.getTweets();
                for (Status tweet : tweets) {
                    System.out.println("@" + tweet.getUser().getScreenName()+" "+tweet.getUser().getName() + " - " + tweet.getText());
                }
                Thread.sleep(10000);
            } while ((query = result.nextQuery()) != null);
            System.exit(0);
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to search tweets: " + te.getMessage());
            System.exit(-1);
        }
    }

    public static void main(String[] args) throws InterruptedException {

        run("DX6ptkrAXL8iZv92IBNurhmm9"
                , "J1o1oTK5muKoDLYOw26Awcd0krZhjGaVFaC0ioKIpSoaoxyI2L"
                , "519198946-TQUfCrIUfQKz8R6FBUYE2IFheb4Ht7qkyTv8uz0h"
                , "15TjyJsBNMmHdQaxCUfyfa3JxdFMVp6ui2klgxOUtLJQP");
    }
}
