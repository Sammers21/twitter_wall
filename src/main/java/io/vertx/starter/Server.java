package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class Server extends AbstractVerticle {


    @Override
    public void start() throws Exception {

        vertx.setTimer(1000, h -> {
            deploy(TwitterClient.class.getCanonicalName());
        });
        vertx.setTimer(2000, h -> {
            deploy(TweetConsumer.class.getCanonicalName());
        });

        EventBus eb = vertx.eventBus();

        //SockJS bridge
        Router router = Router.router(vertx);

        PermittedOptions optclient = new PermittedOptions().setAddress("to.twitter.client");
        optclient.setRequiredAuthority(null);
        PermittedOptions optdelay = new PermittedOptions().setAddress("to.consumer.delay");
        optdelay.setRequiredAuthority(null);
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(optdelay)
                .addInboundPermitted(optclient)
                .addOutboundPermitted(new PermittedOptions().setAddress("webpage"));

        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());

        eb.consumer("to.twitter.client", h -> {
            System.out.println("to.twitter.client body is " + h.body());
        });
        eb.consumer("to.consumer.delay", h -> {
            System.out.println("to.twitter.client body " + h.body());
        });


        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);

    }

    private void deploy(String canonicalName) {
        vertx.deployVerticle(canonicalName, res -> {
            if (res.succeeded()) {
                System.out.println("Deployment of " + canonicalName + ". Id is: " + res.result());
            } else {
                System.out.println("Deployment of " + canonicalName + " failed!");
            }
        });
    }


}
