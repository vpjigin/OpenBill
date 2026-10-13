package app.billing.channel;

import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import app.billing.auth.AuthService;
import app.billing.common.ApiException;
import app.billing.config.AppProperties;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

/**
 * Starts/stops the two independent web-access channels at runtime:
 *  - Wi-Fi/LAN: binds billingPort on each LAN address of this computer.
 *  - Public:    binds publicPort on 127.0.0.1 and runs the tunnel to it.
 * "This computer" (127.0.0.1:billingPort) is always on and is not managed here.
 */
@org.springframework.stereotype.Service
public class AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessService.class);

    private final ApplicationContext ctx;
    private final AppProperties props;
    private final SettingsService settings;
    private final TunnelService tunnel;
    private final AuthService auth;

    private final List<Connector> lanConnectors = new ArrayList<>();
    private Connector publicConnector;

    public AccessService(ApplicationContext ctx, AppProperties props,
                         SettingsService settings, TunnelService tunnel, AuthService auth) {
        this.ctx = ctx;
        this.props = props;
        this.settings = settings;
        this.tunnel = tunnel;
        this.auth = auth;
    }

    /** Restore what the admin had switched on before the last restart. */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        settings.find().ifPresent(s -> {
            try {
                if (s.isLanEnabled()) {
                    startLan();
                }
                if (s.isPublicEnabled()) {
                    startPublic();
                }
            } catch (RuntimeException e) {
                log.warn("Could not restore access channels: {}", e.getMessage());
            }
        });
    }

    // ---- Wi-Fi / LAN ------------------------------------------------------------------------

    public synchronized List<String> startLan() {
        if (!lanConnectors.isEmpty()) {
            return lanAddresses();
        }
        List<String> ips = NetworkInfo.lanAddresses();
        if (ips.isEmpty()) {
            throw ApiException.conflict("No Wi-Fi or LAN network found on this computer.");
        }
        for (String ip : ips) {
            Connector c = LocalBillingConnector.connector(ip, props.billingPort());
            try {
                tomcatService().addConnector(c);
                lanConnectors.add(c);
            } catch (RuntimeException e) {
                log.warn("Could not listen on {}:{} - {}", ip, props.billingPort(), e.getMessage());
                removeConnector(c); // Tomcat keeps a failed connector registered
            }
        }
        if (lanConnectors.isEmpty()) {
            throw ApiException.conflict("Port " + props.billingPort() + " is busy. Close the other program using it.");
        }
        settings.update(s -> s.setLanEnabled(true));
        return lanAddresses();
    }

    public synchronized void stopLan() {
        for (Connector c : lanConnectors) {
            removeConnector(c);
        }
        lanConnectors.clear();
        settings.update(s -> s.setLanEnabled(false));
        auth.revokeChannel(Channel.LAN);
    }

    public synchronized boolean lanRunning() {
        return !lanConnectors.isEmpty();
    }

    public synchronized List<String> lanAddresses() {
        return lanConnectors.stream().map(c -> String.valueOf(c.getProperty("address"))).toList();
    }

    // ---- Public -----------------------------------------------------------------------------

    public synchronized void startPublic() {
        if (publicConnector == null) {
            Connector c = LocalBillingConnector.connector("127.0.0.1", props.publicPort());
            try {
                tomcatService().addConnector(c);
            } catch (RuntimeException e) {
                removeConnector(c);
                throw ApiException.conflict("Port " + props.publicPort() + " is busy. Close the other program using it.");
            }
            publicConnector = c;
        }
        ShopSettings s = settings.get();
        tunnel.start(s.getTunnelToken(), s.getPublicUrl());
        settings.update(x -> x.setPublicEnabled(true));
    }

    public synchronized void stopPublic() {
        tunnel.stop();
        if (publicConnector != null) {
            removeConnector(publicConnector);
            publicConnector = null;
        }
        settings.update(s -> s.setPublicEnabled(false));
        auth.revokeChannel(Channel.PUBLIC);
    }

    public synchronized boolean publicRunning() {
        return publicConnector != null;
    }

    // ---- helpers ----------------------------------------------------------------------------

    private Service tomcatService() {
        if (ctx instanceof ServletWebServerApplicationContext web && web.getWebServer() instanceof TomcatWebServer tomcat) {
            return tomcat.getTomcat().getService();
        }
        throw new IllegalStateException("Embedded Tomcat is not running");
    }

    private void removeConnector(Connector c) {
        try {
            tomcatService().removeConnector(c);
            c.destroy();
        } catch (Exception e) {
            log.warn("Error stopping connector: {}", e.getMessage());
        }
    }
}
