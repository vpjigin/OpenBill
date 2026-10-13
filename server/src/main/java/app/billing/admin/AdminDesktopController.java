package app.billing.admin;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

/** Desktop shell entry: validates the launch secret and sets the admin_desktop cookie. */
@Controller
public class AdminDesktopController {

    private final AdminDesktopAuth auth;

    public AdminDesktopController(AdminDesktopAuth auth) {
        this.auth = auth;
    }

    @GetMapping("/__desktop")
    public void desktop(@RequestParam(name = "secret", required = false) String secret, HttpServletResponse res)
            throws java.io.IOException {
        if (!auth.isLocked()) {
            res.sendRedirect("/");
            return;
        }
        if (!auth.matchesSecret(secret)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType(MediaType.TEXT_HTML_VALUE);
            res.getWriter().write("Open the Billing app to manage the shop.");
            return;
        }
        res.addHeader("Set-Cookie",
                AdminDesktopAuth.COOKIE_NAME + "=" + auth.secretHex()
                        + "; Path=/; HttpOnly; SameSite=Strict");
        res.sendRedirect("/");
    }
}
