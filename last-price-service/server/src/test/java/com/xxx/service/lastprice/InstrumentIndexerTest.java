package com.xxx.service.lastprice;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class InstrumentIndexerTest {

    @Test
    public void shouldDefineIndexCorrectly() {
        final InstrumentIndexer indexer = new InstrumentIndexer(List.of(
            "AAPL",
            "AMZN",
            "MSFT"
        ));
        assertThat(indexer.defineIndex("AAPL"), is(0));
        assertThat(indexer.defineIndex("AMZN"), is(1));
        assertThat(indexer.defineIndex("MSFT"), is(2));
        assertThat(indexer.defineIndex("DNKN"), is(lessThan(0)));
    }

}
