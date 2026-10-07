package tradinghub.service;

import tradinghub.brokers.BrokerException.Reason;

/** 조회에 실패한 증권사와 이유 종류. */
public record FailedBroker(String broker, Reason reason) {
}
