package tradinghub.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tradinghub.service.LiveBalance;
import tradinghub.service.LiveBalanceService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 첫 화면. 체결이 오면 /events(SSE)가 "changed"를 알리고, htmx가 같은 주소를 다시 불러 잔고 부분만 바꾼다.
 * 체결이 없을 때(장 마감)도 30초마다 다시 부른다.
 */
@Controller
class PageController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final LiveBalanceService live;

    PageController(LiveBalanceService live) {
        this.live = live;
    }

    @GetMapping("/")
    String index(Model model) {
        LiveBalance now = live.current();
        model.addAttribute("b", now.balance());
        model.addAttribute("snapshotAt", TIME.format(now.snapshotAt()));
        model.addAttribute("tradeAt", now.tradeAt() == null ? null : TIME.format(now.tradeAt()));
        return "index";
    }

    /** 체결이 왔다는 신호만 보낸다. 브라우저 EventSource는 끊기면 스스로 다시 붙는다. */
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events() {
        var emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        Runnable changed = new Runnable() {
            @Override
            public void run() {
                try {
                    // 데이터가 비면 브라우저가 이벤트를 버린다 (SSE 명세)
                    emitter.send(SseEmitter.event().name("changed").data(Instant.now().toEpochMilli()));
                } catch (IOException | IllegalStateException e) {
                    live.unsubscribe(this); // 브라우저가 닫힘
                }
            }
        };
        live.subscribe(changed);
        emitter.onCompletion(() -> live.unsubscribe(changed));
        try {
            emitter.send(SseEmitter.event().comment("connected")); // 첫 글자를 써야 응답 머리가 나간다
        } catch (IOException e) {
            live.unsubscribe(changed);
        }
        return emitter;
    }
}
