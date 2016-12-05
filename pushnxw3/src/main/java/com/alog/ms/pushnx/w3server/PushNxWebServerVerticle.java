package com.alog.ms.pushnx.w3server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEventType;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

/**
 * PushNx Web Server
 *  - receives messages
 *  - caches the received messages
 *  - pushes them to the event bus when they arrive
 *  - receives replies from browsers via websocket or long-polling (see SockJS)
 *  - sends those replies to iCore
 *  - in the event iCore cannot receive replies, messages are stored in Redis.
 *  - another cron-like vert.x runs a job every few minutes that gets the stored replies
 *    and tries to send them to iCore until they are received.
 *
 * Created by Kevin Fleming on 12/5/2016.
 *
 */
public class PushNxWebServerVerticle extends AbstractVerticle {

    //TODO logging, get actual client ip
    //TODO implement circuit breakers
    //TODO implement service discovery

    private static final Logger logger = LoggerFactory.getLogger(PushNxWebServerVerticle.class);

    @Override
    public void start(Future<Void> fut) {

        logger.info("PushNxWebServerVerticle start-top.");

        // Create a router object.
        Router router = Router.router(vertx);

        // Bind "/" to the friendly welcome message.
        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response
                    .putHeader("content-type", "text/html;charset='UTF-8")
                    .end("<h1>Welcome to the Push Notifications Web Server!</h1>");
        });

        // Just keep routing. Just keep routing
        router.route("/assets/*").handler(StaticHandler.create("assets"));
        router.route("/eventbus/*").handler(eventBusHandler());

        // This is the money!
        router.mountSubRouter("/api/v1/pushnx/showpopup/:msg", popupRouter());
        router.route().failureHandler(errorHandler());

        //TODO needs work
        router.route(   "/api/v1/pushnx*").handler(BodyHandler.create());
        router.get(     "/api/v1/pushnx/diagnostics").handler(this::runDiagnostics);
        router.get(     "/api/v1/pushnx/replies/stored").handler(this::getStoredReplies);
        router.post(    "/api/v1/pushnx/replies/send/:reply").handler(this::receiveReply);
        router.get(     "/api/v1/pushnx/:id").handler(this::getNx);
        router.put(     "/api/v1/pushnx/receive/:reply").handler(this::receiveReply);
        router.delete(  "/api/v1/pushnx/:replyId").handler(this::deleteReply);

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
                .createHttpServer()
                .requestHandler(router::accept)
                .listen(
                        // Retrieve the port from the configuration,
                        // default to 8280.
                        config().getInteger("http.port", 8280),
                        result -> {
                            if (result.succeeded()) {
                                fut.complete();
                            } else {
                                fut.fail(result.cause());
                            }
                        }
                );
    }

    /**
     * Super important
     * @param routingContext
     */
    private void runDiagnostics(RoutingContext routingContext) {
        //TODO the actual diagnostics!

        String msg = "incomplete!";
        routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(msg));
    }

    /**
     * routes popup messages to the handler and then event bus
     * @return
     */
    private Router popupRouter() {

        PopupHandler handler = new PopupHandler();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.route().consumes("application/json");
        router.route().produces("application/json");

        router.patch("/nx/:nx").handler(handler::handlePopupMessage);

        return router;
    }

    /**
     * Handles putting messages onto the websocket
     * @return
     */
    private SockJSHandler eventBusHandler() {
        BridgeOptions options = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("livedealer.auction"));
        return SockJSHandler.create(vertx).bridge(options, event -> {
            if (event.type() == BridgeEventType.SOCKET_CREATED) {
                //TODO do better then this, please, Kevin!
                logger.info("A socket was created");
            }
            event.complete(true);
        });
    }

    /**
     * Receives a notification for popup in client's browser
     * @param routingContext
     */
    private void showPopup(RoutingContext routingContext) {
        final String msg = routingContext.request().getParam("msg");

        // iCore wants a 200 if OK
        // 200 No Content = happy!
        routingContext.response().setStatusCode(200).end();
    }

    /**
     * Receives a reply, sent either by browser or by cron job
     * @param routingContext
     */
    private void receiveReply(RoutingContext routingContext) {
        final String reply = routingContext.request().getParam("reply");
        JsonObject json = routingContext.getBodyAsJson();
        //TODO can it be empty string?
        if (reply == null || json == null) {
            //TODO better status code?
            routingContext.response().setStatusCode(400).end();
        } else {
            //TODO send reply to iCore

            // 204 No Content = happy!
            routingContext.response().setStatusCode(204).end();
        }
    }

//    private Future<Void> forwardReply() {
//        Future<Void> future = Future.future();
//        HttpEndpoint.getClient(discovery, new JsonObject().put("name", "audit"), client -> {
//            this.client = client.result();
//            if (client.succeeded()) {
//                future.complete();
//            } else {
//                future.fail(client.cause());
//            }
//        });
//        return future;
//    }

    /**
     * Geta notification by its message id
     * @param routingContext
     */
    private void getNx(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            //TODO get a notification from Redis
            String nx = null;
            // write
            routingContext.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(nx));
        }
    }

    /**
     * After successfully sending a reply to iCore, it needs to be deleted.
     * @param routingContext
     */
    private void deleteReply(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("replyId");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            try {
                //TODO delete a reply from Redis

                // if successful
                // send 204 no content
                routingContext.response().setStatusCode(204).end();
            }
            catch(Exception ex) {
                //TODO Hmmm
            }
        }
    }

    /**
     * Deletes a reply from the Redis store
     * @param replyId
     * @throws Exception
     */
    private void deleteReply(String replyId) throws Exception {
        //TODO throw if delete fails
    }

    /**
     * Gets all stored replies
     * @param routingContext
     */
    private void getStoredReplies(RoutingContext routingContext) {
        
    }

    private ErrorHandler errorHandler() {
        return ErrorHandler.create(true);
    }

    @Override
    public void stop() throws Exception {
        //TODO Close ?
    }
}


/**

 protected ServiceDiscovery discovery;
 protected Set<Record> registeredRecords = new ConcurrentHashSet<>();

 @Override
 public void start() {
 discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions().setBackendConfiguration(config()));
 }

 public void publishHttpEndpoint(String name, String host, int port, Handler<AsyncResult<Void>>
 completionHandler) {
 Record record = HttpEndpoint.createRecord(name, host, port, "/");
 publish(record, completionHandler);
 }

 public void publishMessageSource(String name, String address, Class contentClass, Handler<AsyncResult<Void>>
 completionHandler) {
 Record record = MessageSource.createRecord(name, address, contentClass);
 publish(record, completionHandler);
 }

 public void publishMessageSource(String name, String address, Handler<AsyncResult<Void>>
 completionHandler) {
 Record record = MessageSource.createRecord(name, address);
 publish(record, completionHandler);
 }

 public void publishEventBusService(String name, String address, Class serviceClass, Handler<AsyncResult<Void>>
 completionHandler) {
 Record record = EventBusService.createRecord(name, address, serviceClass);
 publish(record, completionHandler);
 }

 private void publish(Record record, Handler<AsyncResult<Void>> completionHandler) {
 if (discovery == null) {
 try {
 start();
 } catch (Exception e) {
 throw new RuntimeException("Cannot create discovery service");
 }
 }

 discovery.publish(record, ar -> {
 if (ar.succeeded()) {
 registeredRecords.add(record);
 completionHandler.handle(Future.succeededFuture());
 } else {
 completionHandler.handle(Future.failedFuture(ar.cause()));
 }
 });
 }

 @Override
 public void stop(Future<Void> future) throws Exception {
 List<Future> futures = new ArrayList<>();
 for (Record record : registeredRecords) {
 Future<Void> unregistrationFuture = Future.future();
 futures.add(unregistrationFuture);
 discovery.unpublish(record.getRegistration(), unregistrationFuture.completer());
 }

 if (futures.isEmpty()) {
 discovery.close();
 future.complete();
 } else {
 CompositeFuture composite = CompositeFuture.all(futures);
 composite.setHandler(ar -> {
 discovery.close();
 if (ar.failed()) {
 future.fail(ar.cause());
 } else {
 future.complete();
 }
 });
 }
 }
 }
 */