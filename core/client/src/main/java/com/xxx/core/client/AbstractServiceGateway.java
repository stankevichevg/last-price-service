package com.xxx.core.client;

import com.xxx.core.client.pool.FixSizeObjectPool;
import com.xxx.core.client.pool.ObjectPool;
import com.xxx.core.protocol.Message;
import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import static com.xxx.core.client.ConnectionControlClient.CONNECTION_CONTROL_CLIENT_ID;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.TRAILER_LENGTH;

/**
 * Base class for main entry point to create service clients.
 * Main responsibilities of this class are the following:
 * 1. Holds pool of clients to call remote service, clients factoring (consider to redesign)
 * 2. Initiate and set up connection to server (setup of Aeron publication and subscription)
 * 3. (NOT FULLY IMPLEMENTED) Handle broken connection (reconnect)
 *
 * @author Evgeny Stankevich {@literal <stankevich.evg@gmail.com>}.
 */
public abstract class AbstractServiceGateway<C extends BaseServiceClient> implements AutoCloseable {

    private static final IdleStrategy WAIT_PUB_SUB_IDLE_STRATEGY = new SleepingMillisIdleStrategy(10);

    private final Lock lock;

    private final Aeron aeron;
    private final IdleStrategy readCycleIdleStrategy;

    private final String serverChannel;
    private final int serverStreamId;
    private final String clientChannel;
    private final int clientStreamId;

    private final ConnectionControlClient serverConnectionClient;
    private final ObjectPool<C> clients;

    // should be volatile since it can be updated by one thread during connect and
    // read by multiple clients threads
    private volatile ServerPublication registeredPublication;

    private ReadCycleTask readCycleTask;
    private final ExecutorService executorService;

    protected AbstractServiceGateway(
        Lock lock,
        Aeron aeron,
        NanoClock nanoClock,
        IdleStrategy readCycleIdleStrategy,
        String serverChannel,
        int serverStreamId,
        String clientChannel,
        int clientStreamId,
        int maxClients,
        Supplier<IdleStrategy> clientIdleStrategyFactory,
        ExecutorService executorService) {

        this.lock = lock;
        this.aeron = aeron;
        this.readCycleIdleStrategy = readCycleIdleStrategy;
        this.serverChannel = serverChannel;
        this.serverStreamId = serverStreamId;
        this.clientChannel = clientChannel;
        this.clientStreamId = clientStreamId;
        this.serverConnectionClient = new ConnectionControlClient(
            clientChannel,
            clientStreamId,
            nanoClock,
            this,
            WAIT_PUB_SUB_IDLE_STRATEGY,
            TimeUnit.SECONDS.toNanos(1),
            new OneToOneRingBuffer(new UnsafeBuffer(
                allocateDirectAligned(1024 + TRAILER_LENGTH, CACHE_LINE_LENGTH)
            ))
        );
        this.clients = createClientsPool(maxClients, nanoClock, clientIdleStrategyFactory);
        this.executorService = executorService;
    }

    /**
     * Connect gateway to server. This operation assumes the following steps:
     *
     * 1. stop receiving any messages from previous subscription
     * 2. close previous registeredPublication
     * 3. create new subscription and publication
     * 4. setup and run new read cycle with new subscription in separate thread
     * 5. make request to server to open a new registeredPublication,
     *    server should assign registeredPublication id we will use for all outgoing requests
     * 6. atomically update registeredPublication, so all outgoing messages will be sent using new publication
     * 7. if something goes wrong do not forget close publication and subscription
     *
     * @throws ConnectionException if can not connect
     */
    public void connect() throws ConnectionException {
        lock.lock();
        Publication publication = null;
        Subscription subscription = null;
        try {
            this.close();
            publication = createServerPublication();
            subscription = createClientSubscription();
            readCycleTask = new ReadCycleTask(subscription, readCycleIdleStrategy);
            executorService.execute(readCycleTask);
            registeredPublication = new ServerPublication(0L, publication);
            final long serverConnectionId = serverConnectionClient.connect();
            registeredPublication = new ServerPublication(serverConnectionId, publication);
        } catch (Throwable e) {
            if (publication != null) {
                publication.close();
            }
            if (subscription != null) {
                subscription.close();
            }
            throw new ConnectionException("Gateway can not connect", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquire client instance from the internal pool.
     *
     * @return client instance or {@code null} if there is no more available clients
     */
    public C getClient() {
        lock.lock();
        try {
            return clients.acquireInstance();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        if (readCycleTask != null && readCycleTask.isRunning()) {
            readCycleTask.stop();
        }
        if (registeredPublication != null) {
            registeredPublication.close();
        }
    }

    /**
     * Produces client. Method is used to initialise pool of service clients.
     *
     * @param clientId client identifier
     * @param nanoClock Nano clock service
     * @return created client
     */
    protected abstract C createClient(int clientId, NanoClock nanoClock, IdleStrategy idleStrategy);

    /**
     * Generates new correlation ID. This value guaranteed to be unique.
     *
     * @return created ID
     */
    protected long nextCorrelationId() {
        return aeron.nextCorrelationId();
    }

    void send(Message message) {
        registeredPublication.sendRequest(message);
    }

    @SuppressWarnings("unchecked")
    void closeClient(BaseServiceClient serviceClient) {
        lock.lock();
        try {
            clients.freeInstance((C) serviceClient);
        } finally {
            lock.unlock();
        }
    }

    private ObjectPool<C> createClientsPool(int maxClients, final NanoClock nanoClock, Supplier<IdleStrategy> clientIdleStrategyFactory) {
        return new FixSizeObjectPool<>(maxClients, id -> this.createClient(id, nanoClock, clientIdleStrategyFactory.get()));
    }

    private Subscription createClientSubscription() {
        return aeron.addSubscription(clientChannel, clientStreamId);
    }

    private Publication createServerPublication() {
        final Publication publication = aeron.addPublication(serverChannel, serverStreamId);
        if (!publication.isConnected()) {
            WAIT_PUB_SUB_IDLE_STRATEGY.reset();
            while (!publication.isConnected()) {
                WAIT_PUB_SUB_IDLE_STRATEGY.idle();
            }
        }
        return publication;
    }

    /**
     * Routine for the only thread to read inbound messages from the registeredPublication.
     */
    private final class ReadCycleTask implements Runnable {

        private static final int FRAGMENT_LIMIT = 10;

        private final Subscription subscription;
        private final IdleStrategy idleStrategy;
        private final FragmentAssembler assembler = new FragmentAssembler(this::onFragment);

        final AtomicBoolean running = new AtomicBoolean(false);

        private ReadCycleTask(Subscription subscription, IdleStrategy idleStrategy) {
            this.subscription = subscription;
            this.idleStrategy = idleStrategy;
        }

        @Override
        public void run() {
            running.set(true);
            while (running.get()) {
                idleStrategy.idle(pollNewMessages());
            }
        }

        public int pollNewMessages() {
            return subscription.poll(assembler, FRAGMENT_LIMIT);
        }

        private void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header) {
            final int messageType = buffer.getInt(offset + Message.TYPE_FIELD_OFFSET);
            final int clientId = buffer.getInt(offset + Message.CLIENT_ID_FIELD_OFFSET);
            final BaseServiceClient client = clientId == CONNECTION_CONTROL_CLIENT_ID ?
                serverConnectionClient : clients.getInstance(clientId);
            client.receiveMessage(messageType, buffer, offset, length);
        }

        public boolean isRunning() {
            return running.get();
        }

        public void stop() {
            running.set(false);
            subscription.close();
        }

    }

}
