package tradinghub.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tradinghub.service.AccountBalance;
import tradinghub.service.AccountNames;
import tradinghub.service.LiveBalance;
import tradinghub.service.LiveBalanceService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 첫 화면. 체결이 오면 /events(SSE)가 "changed"를 알리고, htmx가 같은 주소를 다시 불러 잔고 부분만 바꾼다.
 * 체결이 없을 때(장 마감)도 30초마다 다시 부른다.
 */
@Controller
class PageController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final LiveBalanceService live;
    private final AccountNames names;

    PageController(LiveBalanceService live, AccountNames names) {
        this.live = live;
        this.names = names;
    }

    @GetMapping("/")
    String index(Model model) {
        LiveBalance now = live.current();
        model.addAttribute("b", now.balance());
        model.addAttribute("snapshotAt", TIME.format(now.snapshotAt()));
        model.addAttribute("tradeAt", now.tradeAt() == null ? null : TIME.format(now.tradeAt()));
        model.addAttribute("names", names);
        return "index";
    }

    /** 폼 칸 이름이 계좌(예: "toss-1234"). 지금 화면에 있는 계좌만 바꾸고, 다 바꾸면 첫 화면으로 돌려보낸다. */
    @PostMapping("/account-names")
    ResponseEntity<String> rename(@RequestParam Map<String, String> form) {
        try {
            for (AccountBalance a : live.current().balance().accounts()) {
                String name = form.get(a.account().value());
                if (name != null) names.rename(a.account(), name);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage()); // 메시지에 계좌번호가 없다 (이름 규칙만)
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header("Location", "/").build();
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
