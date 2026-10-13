package app.billing.channel;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import app.billing.config.AppProperties;

/**
 * The main connector (server.port) is the admin port on 127.0.0.1.
 * This adds the billing website for THIS computer: 127.0.0.1:billingPort — always on.
 * Wi-Fi and public connectors are added/removed at runtime by AccessService.
 */
@Component
public class LocalBillingConnector implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final AppProperties props;

    public LocalBillingConnector(AppProperties props) {
        this.props = props;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addAdditionalTomcatConnectors(connector("127.0.0.1", props.billingPort()));
    }

    static Connector connector(String address, int port) {
        Connector c = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        c.setPort(port);
        c.setProperty("address", address);
        return c;
    }
}
