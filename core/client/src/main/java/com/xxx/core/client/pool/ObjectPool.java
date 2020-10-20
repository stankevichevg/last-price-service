package com.xxx.core.client.pool;

public interface ObjectPool<T extends PoolInstance> {

    T getInstance(int id);
    T acquireInstance();
    void freeInstance(T instance);

}
