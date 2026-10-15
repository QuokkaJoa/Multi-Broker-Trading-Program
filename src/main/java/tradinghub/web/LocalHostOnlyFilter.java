package tradinghub.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Host 헤더가 127.0.0.1·localhost가 아니면 거부한다 (DNS rebinding 방지). 바꾸는 요청은 Origin도 본다. */
@Component
public class LocalHostOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("127.0.0.1", "localhost");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getServerName()은 Host가 없거나 프록시 헤더가 있으면 다른 값을 줄 수 있어서 헤더를 직접 본다
        String host = request.getHeader("Host");
        if (!allowed(host)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        // 다른 사이트의 폼도 127.0.0.1로 보낼 수 있어서(Host는 통과), 바꾸는 요청은 내 화면에서 온 것만 받는다 (CSRF 방지)
        boolean changes = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (changes && !allowedOrigin(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean allowed(String host) {
        return host != null && ALLOWED.contains(host.replaceFirst(":\\d+$", "").toLowerCase(Locale.ROOT));
    }

    private static boolean allowedOrigin(String origin) {
        return origin != null && origin.startsWith("http://") && allowed(origin.substring("http://".length()));
    }
}
