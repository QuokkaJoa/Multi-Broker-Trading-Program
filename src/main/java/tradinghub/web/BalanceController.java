package tradinghub.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tradinghub.service.BalanceService;
import tradinghub.service.ConsolidatedBalance;

@RestController
class BalanceController {

    private final BalanceService balances;

    BalanceController(BalanceService balances) {
        this.balances = balances;
    }

    @GetMapping("/api/balance")
    ConsolidatedBalance balance() {
        return balances.consolidated();
    }
}
