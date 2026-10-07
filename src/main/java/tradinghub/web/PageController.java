package tradinghub.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tradinghub.service.BalanceService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 첫 화면. htmx가 30초마다 같은 주소를 다시 불러 잔고 부분만 바꾼다. */
@Controller
class PageController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BalanceService balances;

    PageController(BalanceService balances) {
        this.balances = balances;
    }

    @GetMapping("/")
    String index(Model model) {
        model.addAttribute("b", balances.consolidated());
        model.addAttribute("fetchedAt", LocalTime.now().format(TIME));
        return "index";
    }
}
