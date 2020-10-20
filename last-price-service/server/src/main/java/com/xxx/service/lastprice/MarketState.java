package com.xxx.service.lastprice;


/**
 * Represents current markIt state for all instruments ;)
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class MarketState extends BaseIndexedPriceRecordsBlock {

    protected MarketState(int recordsNumber) {
        super(recordsNumber, 0);
    }

}
