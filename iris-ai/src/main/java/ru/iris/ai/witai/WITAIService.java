package ru.iris.ai.witai;

/**
 * IRISv2 Project
 * Author: Nikolay A. Viguro
 * WWW: iris.ph-systems.ru
 * E-Mail: nv@ph-systems.ru
 * Date: 26.10.13
 * Time: 20:41
 * License: GPL v3
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.iris.ai.Service;
import ru.iris.common.Config;
import ru.iris.common.SQL;
import ru.iris.common.messaging.JsonEnvelope;
import ru.iris.common.messaging.JsonMessaging;
import ru.iris.common.messaging.model.ServiceStatus;
import ru.iris.common.messaging.model.SpeakRecognizedAdvertisement;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.UUID;

public class WitAiService implements Runnable {

    private Thread t = null;
    private Logger log = LogManager.getLogger(WitAiService.class);
    private boolean shutdown = false;
    private SQL sql = Service.getSQL();

    public WitAiService() {
        this.t = new Thread(this);
        this.t.start();
    }

    public Thread getThread() {
        return this.t;
    }

    public synchronized void run() {

        Service.serviceChecker.setAdvertisment(
                Service.advertisement.set("AI", Service.serviceId, ServiceStatus.AVAILABLE));

        Config cfg = new Config();
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

        try {
            // Make sure we exit the wait loop if we receive shutdown signal.
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    shutdown = true;
                }
            }));

            JsonMessaging jsonMessaging = new JsonMessaging(UUID.randomUUID());
            jsonMessaging.subscribe("event.speak.recognized");
            jsonMessaging.start();

            while (!shutdown) {

                // Lets wait for 100 ms on json messages and if nothing comes then proceed to carry out other tasks.
                final JsonEnvelope envelope = jsonMessaging.receive(100);
                if (envelope != null) {
                    if (envelope.getObject() instanceof SpeakRecognizedAdvertisement) {

                        SpeakRecognizedAdvertisement advertisement = envelope.getObject();

                        /////////////////////////////////////////////////////////////////

                        String url = "https://api.wit.ai/message?q=" + URLEncoder.encode(advertisement.getText(), "UTF-8");

                        log.debug("URL is: " + url);

                        CloseableHttpClient httpclient = HttpClients.createDefault();
                        HttpGet httpget = new HttpGet(url);

                        // auth on wit.ai
                        httpget.addHeader("Authorization", "Bearer "+cfg.getConfig().get("witaiKey"));

                        CloseableHttpResponse response = httpclient.execute(httpget);
                        try {
                            HttpEntity entity = response.getEntity();
                            if (entity != null) {

                                InputStream instream = entity.getContent();

                                try {
                                    String content = IOUtils.toString(instream, "UTF-8");

                                    //TODO testing

                                    log.info("Content: " + content);

                                    WitAiResponse json = gson.fromJson(content, WitAiResponse.class);

                                    log.info("Action value: "+json.getOutcome().getEntities().get("action").getValue());

                                } finally {
                                    instream.close();
                                }
                            }
                        } finally {
                            response.close();
                        }

                        /////////////////////////////////////////////////////////////////

                    } else {
                        // We received unknown request message. Lets make generic log entry.
                        log.info("Received request "
                                + " from " + envelope.getSenderInstance()
                                + " to " + envelope.getReceiverInstance()
                                + " at '" + envelope.getSubject()
                                + ": " + envelope.getObject());
                    }
                }
            }

            Service.serviceChecker.setAdvertisment(
                    Service.advertisement.set("AI", Service.serviceId, ServiceStatus.SHUTDOWN));

            // Close JSON messaging.
            jsonMessaging.close();

        } catch (final Throwable t) {

            log.error("Unexpected exception in AI", t);

            Service.serviceChecker.setAdvertisment(
                    Service.advertisement.set("AI", Service.serviceId, ServiceStatus.SHUTDOWN));
            t.printStackTrace();
        }

    }
}