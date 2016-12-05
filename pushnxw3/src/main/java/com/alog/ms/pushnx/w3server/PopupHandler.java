package com.alog.ms.pushnx.w3server;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * Handles the processing of of incoming notification messages
 * Publishes them to the event bus.
 *
 * Created by Kevin Fleming on 12/5/2016.
 */
public class PopupHandler {

    /**
     * Handle the arrival of a popup message
     *
     * @param context
     */
    public void handlePopupMessage(RoutingContext context) {
        final String nx = context.request().getParam("notification");

        if (validate(nx)) {
            //TODO get the topic
            // publish the nx
            context.vertx().eventBus().publish("livedealer.popup."
                    , Json.encodePrettily(context.getBodyAsString()));

            context.response()
                    .setStatusCode(200)
                    .end();
        } else {
            context.response()
                    .setStatusCode(422)
                    .end();
        }
    }

    /**
     * Validates a notification message
     * @param nx
     * @return true if valid, otherwise false.
     */
    private boolean validate(String nx) {
        //TODO validate
        return false;
    }
}
