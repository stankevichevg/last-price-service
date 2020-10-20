package com.xxx.service.lastprice;

/**
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
@FunctionalInterface
public interface IntIndexedEntityConsumer<T> {

    void accept(int index, T entity);

}
