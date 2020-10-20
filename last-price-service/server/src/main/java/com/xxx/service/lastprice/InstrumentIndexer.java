package com.xxx.service.lastprice;

import java.util.Collection;

import static java.util.Arrays.binarySearch;
import static java.util.Arrays.sort;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public class InstrumentIndexer {

    private final String[] sortedInstruments;

    public InstrumentIndexer(Collection<String> instruments) {
        this.sortedInstruments = new String[instruments.size()];
        instruments.toArray(this.sortedInstruments);
        sort(this.sortedInstruments, CharSequence::compare);
    }

    public int defineIndex(CharSequence instrument) {
        return binarySearch(sortedInstruments, instrument, CharSequence::compare);
    }

}
