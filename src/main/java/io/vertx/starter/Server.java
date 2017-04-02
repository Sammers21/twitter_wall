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

        deploy(TwitterClient.class.getCanonicalName());
        deploy(MessageConsumer.class.getCanonicalName());

        EventBus eb = vertx.eventBus();

        //SockJS bridge
        Router router = Router.router(vertx);
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("to.consumer.delay"))
                .addInboundPermitted(new PermittedOptions().setAddress("to.twitter.client"))
                .addOutboundPermitted(new PermittedOptions().setAddress("webpage"));

        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());


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
