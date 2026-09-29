package org.opensagetv.webplayer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Security and isolation headers for the Web Player.
 *
 * COOP/COEP unlock SharedArrayBuffer/Wasm threads on browsers that also treat
 * the origin as secure (normally HTTPS or localhost). On plain LAN HTTP the
 * browser may still report crossOriginIsolated=false; the player will then use
 * its worker/single-memory fallback automatically.
 */
public class SecurityHeadersFilter implements Filter {
    @Override public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("Referrer-Policy", "same-origin");
            http.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            http.setHeader("X-Frame-Options", "SAMEORIGIN");
            http.setHeader("Cross-Origin-Opener-Policy", "same-origin");
            http.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
            http.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            http.setHeader("Origin-Agent-Cluster", "?1");
        }
        chain.doFilter(request, response);
    }

    @Override public void destroy() {}
}
