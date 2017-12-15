package com.example.chatbot;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.ArrayList;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bytedeco.javacpp.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.opencv_core;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import org.bytedeco.javacpp.opencv_objdetect;

import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import org.bytedeco.javacpp.opencv_objdetect.*;

public class FacebookBotVerticle extends AbstractVerticle {

    private String VERIFY_TOKEN;
    private String ACCESS_TOKEN;
    
    String classifier = null;
    File file = null;
    URL url = new URL("https://raw.github.com/Itseez/opencv/2.4.0/data/haarcascades/haarcascade_frontalface_alt.xml");
    File file = Loader.extractResource(url, null, "classifier", ".xml");
    classifier = file.getAbsolutePath();
    Loader.load(opencv_objdetect.class);
    CvHaarClassifierCascade classifier = new CvHaarClassifierCascade(cvLoad(classifier));

    @Override
    public void start() throws Exception {

        updateProperties();

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/webhook").handler(this::verify);
        router.post("/webhook").handler(this::message);

        vertx.createHttpServer().requestHandler(router::accept)
                .listen(
                        Integer.getInteger("http.port", 8080), System.getProperty("http.address", "0.0.0.0"));
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new FacebookBotVerticle());
    }


    private void verify(RoutingContext routingContext) {
        String challenge = routingContext.request().getParam("hub.challenge");
        String token = routingContext.request().getParam("hub.verify_token");
        if (!VERIFY_TOKEN.equals(token)) {
            challenge = "fake";
        }

        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(challenge);
    }

    private void message(RoutingContext routingContext) {

        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end("done");

        final JsonObject hook = routingContext.getBodyAsJson();

        JsonArray entries = hook.getJsonArray("entry");

        entries.getList().forEach(new Consumer() {
            @Override
            public void accept(Object e) {
                System.out.println(e.getClass());
                Map entry = (Map) e ;
                ArrayList messagingList = (ArrayList) entry.get("messaging");
                System.out.println(messagingList.getClass());
                messagingList.forEach(new Consumer() {
                    () {
                        FacebookBotVerticle.super();
                    }

                    @Override
                    public void accept(Object m) {
                        Map messaging = (Map) m ;
                        Map sender = (Map) messaging.get("sender");
                        messaging.put("recipient", sender);
                        messaging.remove("sender");
                        
                        Map message = (Map) messaging.get("message");
                        
                        final ArrayList attachments = (ArrayList) message.get("attachments");
                        if (attachments !=null)
                        {
                            final Map attachment = (Map) attachments.get(0);
                            final Map payload = (Map) attachment.get("payload");
                            final String image = ((String) payload.get("url"));
                            
                            URL url = null;
                            try {
                                url = new URL(image);
                            } catch (MalformedURLException ex) {
                                Logger.getLogger(FacebookBotVerticle.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            final File file = null;
                            try {
                                file = File.createTempFile("attachment-", FilenameUtils.getName(url.getPath())); //Create a temp file to save attachment
                            } catch (IOException ex) {
                                Logger.getLogger(FacebookBotVerticle.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            try {
                                FileUtils.copyURLToFile(url, file);
                            } catch (IOException ex) {
                                Logger.getLogger(FacebookBotVerticle.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            //читаем изображение из файла
                            opencv_core.Mat mat = imread(file.getAbsolutePath());
                            //сюда складываем найденное
                            opencv_core.RectVector rectVector = new opencv_core.RectVector();
                            classifier.detectMultiScale(mat, rectVector); //поиск фрагментов
                            boolean hasFound = rectVector.size() > 0;
                            if (hasFound) {
                                message.put("text", "In the photo there is a face! Total number of persons: " + rectVector.size());
                            }
                        }
                        
                        message.remove("mid");
                        message.remove("seq");
                        message.remove("attachments");
                        messaging.put("message", message);
                        
                        System.out.println(JsonObject.mapFrom(messaging));
                        
                        WebClientOptions options = new WebClientOptions();
                        options.setSsl(true).setLogActivity(true);
                        WebClient client = WebClient.create(vertx, options);
                        
                        
                        client
                                .post(443, "graph.facebook.com", "/v2.6/me/messages/")
                                .addQueryParam("access_token", ACCESS_TOKEN)
                                .sendJsonObject(JsonObject.mapFrom(messaging), ar -> {
                                    if (ar.succeeded()) {
                                        // Obtain response
                                        HttpResponse<Buffer> res = ar.result();
                                        
                                        System.out.println("Received response with status code" + res.bodyAsString());
                                    } else {
                                        System.out.println("Something went wrong " + ar.cause().getMessage());
                                    }
                                });
                    }
                });
            }
        });
    }

    private void updateProperties() {
        
            VERIFY_TOKEN = System.getProperty("facebook.verify.token", "verify-token-default");
            ACCESS_TOKEN = System.getProperty("facebook.access.token", "access-token-default");
        
    }
}

