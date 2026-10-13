package app.billing.channel;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import app.billing.config.AppProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs before Spring Security.
 *  1. Tags each request with its Channel (by local port / address).
 *  2. Keeps the admin surface on the admin port only, and billing off the admin port.
 *  3. Serves the right single-page app for browser navigation (admin.html vs index.html).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ChannelFilter extends OncePerRequestFilter {

    private final AppProperties props;

    public ChannelFilter(AppProperties props) {
        this.props = props;
    }

    public static Channel channelOf(HttpServletRequest request) {
        Object c = request.getAttribute(Channel.REQUEST_ATTR);
        return c instanceof Channel ch ? ch : Channel.LOCAL;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Channel channel = resolve(req);
        req.setAttribute(Channel.REQUEST_ATTR, channel);

        if (req.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        boolean adminChannel = channel == Channel.ADMIN;

        boolean adminOnlyPath = path.startsWith("/api/admin/") || path.startsWith("/api/setup")
                || path.equals("/admin.html");
        if (adminOnlyPath && !adminChannel) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean billingOnlyPath = path.startsWith("/api/invoices") || path.startsWith("/api/credit-notes")
                || path.startsWith("/api/held-bills") || path.startsWith("/api/reports")
                || path.startsWith("/api/einvoice")
                || path.startsWith("/api/customers") || path.startsWith("/api/suppliers")
                || path.startsWith("/api/purchases") || path.startsWith("/api/print")
                || path.startsWith("/api/shop") || path.equals("/index.html");
        if (billingOnlyPath && adminChannel) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // SPA fallback: browser navigation to /, /items, /users ... gets the right app shell.
        if ("/__desktop".equals(path)) {
            chain.doFilter(req, res);
            return;
        }

        if ("GET".equals(req.getMethod()) && !path.startsWith("/api/") && !path.contains(".")) {
            req.getRequestDispatcher(adminChannel ? "/admin.html" : "/index.html").forward(req, res);
            return;
        }

        chain.doFilter(req, res);
    }

    private Channel resolve(HttpServletRequest req) {
        int port = req.getLocalPort();
        if (port == props.adminPort()) {
            return Channel.ADMIN;
        }
        if (port == props.publicPort()) {
            return Channel.PUBLIC;
        }
        String local = req.getLocalAddr();
        boolean loopback = local != null && (local.startsWith("127.") || local.equals("::1")
                || local.equals("0:0:0:0:0:0:0:1"));
        return loopback ? Channel.LOCAL : Channel.LAN;
    }
}
