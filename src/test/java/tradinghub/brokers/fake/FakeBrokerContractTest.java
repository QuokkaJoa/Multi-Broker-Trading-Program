package tradinghub.brokers.fake;

import org.junit.jupiter.api.Nested;
import tradinghub.brokers.Broker;
import tradinghub.brokers.BrokerContractTest;

class FakeBrokerContractTest {

    @Nested
    class 원화만 extends BrokerContractTest {
        @Override
        protected Broker broker() {
            return FakeBroker.krwOnly();
        }
    }

    @Nested
    class 원화와_달러 extends BrokerContractTest {
        @Override
        protected Broker broker() {
            return FakeBroker.krwAndUsd();
        }
    }
}
