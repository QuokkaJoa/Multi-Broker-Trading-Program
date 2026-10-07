package tradinghub.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHostOnlyFilterTest {

    private final LocalHostOnlyFilter filter = new LocalHostOnlyFilter();

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "localhost", "127.0.0.1:8080", "LOCALHOST:8080"})
    void 내_맥_주소는_통과(String host) throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        filter.doFilter(request(host), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evil.example", "192.168.0.10", "localhost.evil.example", "evil.example:8080"})
    void 다른_주소는_403(String host) throws Exception {
        assertForbidden(request(host));
    }

    @Test
    void Host_헤더가_없으면_403() throws Exception {
        assertForbidden(new MockHttpServletRequest("GET", "/api/balance")); // serverName은 기본 localhost
    }

    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static MockHttpServletRequest request(String host) {
        var request = new MockHttpServletRequest("GET", "/api/balance");
        request.addHeader("Host", host);
        return request;
    }
}
