package app.billing.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import app.billing.channel.Channel;
import app.billing.channel.ChannelFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * When {@link AdminDesktopAuth} is locked, the admin port only serves requests that carry the
 * desktop cookie (set via {@code /__desktop?secret=}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class AdminDesktopFilter extends OncePerRequestFilter {

    private static final byte[] FORBIDDEN_HTML = """
            <!DOCTYPE html>
            <html lang="en"><head><meta charset="utf-8"/><title>OpenBill Admin</title></head>
            <body style="font-family:system-ui,sans-serif;padding:2rem;color:#1b1f1d">
            <p>Open the OpenBill app to manage the shop.</p>
            </body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final AdminDesktopAuth auth;

    public AdminDesktopFilter(AdminDesktopAuth auth) {
        this.auth = auth;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!auth.isLocked()) {
            chain.doFilter(req, res);
            return;
        }

        if (req.getDispatcherType() != DispatcherType.REQUEST) {
            chain.doFilter(req, res);
            return;
        }

        if (ChannelFilter.channelOf(req) != Channel.ADMIN) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        if ("/__desktop".equals(path)) {
            chain.doFilter(req, res);
            return;
        }

        if (auth.matchesCookie(readCookie(req))) {
            chain.doFilter(req, res);
            return;
        }

        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType(MediaType.TEXT_HTML_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentLength(FORBIDDEN_HTML.length);
        res.getOutputStream().write(FORBIDDEN_HTML);
    }

    private static String readCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (AdminDesktopAuth.COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
