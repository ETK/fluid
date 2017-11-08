package me.escoffier.fluid.constructs.impl;

import io.reactivex.Flowable;
import io.reactivex.flowables.ConnectableFlowable;
import me.escoffier.fluid.constructs.*;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DataStreamImpl<I, T> implements DataStream<T> {
    private Flowable<T> flow;
    private final boolean connectable;
    private final DataStream<I> previous;
    private StreamConnector<T> connector;

    public DataStreamImpl(DataStream<I> previous, Publisher<T> flow) {
        Objects.requireNonNull(flow, "The flow passed to the stream cannot be `null`");
        this.flow = Flowable.fromPublisher(flow);
        this.previous = previous;
        this.connectable = false;
    }

    public DataStreamImpl(Class<T> clazz) {
        connector = new StreamConnector<>();
        this.flow = Flowable.fromPublisher(connector);
        this.previous = null;
        this.connectable = true;
    }

    @Override
    public DataStream<T> catchAndReturn(Function<Throwable, T> errorHandler) {
        return new DataStreamImpl<>(this, flow.onErrorReturn(errorHandler::apply));
    }


    @Override
    @SafeVarargs
    public final DataStream<T> mergeWith(DataStream<T>... streams) {
        Objects.requireNonNull(streams, "The given streams cannot be `null`");
        Flowable<T> merged = flow;
        for (DataStream<T> s : streams) {
            merged = merged.mergeWith(s.flow());
        }
        return new DataStreamImpl<>(this, merged);
    }

    @Override
    @SafeVarargs
    public final DataStream<T> concatWith(DataStream<T>... streams) {
        Objects.requireNonNull(streams, "The given streams cannot be `null`");
        Flowable<T> newFlow = flow;
        for (DataStream<T> s : streams) {
            newFlow = newFlow.concatWith(s.flow());
        }
        return new DataStreamImpl<>(this, newFlow);
    }

    @Override
    public <O> DataStream<Pair<T, O>> zipWith(DataStream<O> stream) {
        Objects.requireNonNull(stream, "The given stream cannot be `null`");
        Flowable<Pair<T, O>> flowable = flow.zipWith(stream.flow(), Pair::pair);
        return new DataStreamImpl<>(this, flowable);
    }

    @Override
    public <O1, O2> DataStream<Tuple> zipWith(DataStream<O1> stream1, DataStream<O2> stream2) {
        Objects.requireNonNull(stream1, "The given stream cannot be `null`");
        Objects.requireNonNull(stream2, "The given stream cannot be `null`");
        Flowable<Tuple> flowable = Flowable.zip(flow, stream1.flow(), stream2.flow(), (a, b, c)
            -> Tuple.tuple(a, b, c));
        return new DataStreamImpl<>(this, flowable);
    }

    // TODO Zip up to 7 streams.


    @Override
    public <OUT> DataStream<OUT> transformWith(@NotNull Transformer<T, OUT> transformer) {
        Objects.requireNonNull(transformer, "The given transformer must not be `null`");
        return new DataStreamImpl<>(this, transformer.transform(flow));
    }

    @Override
    public <OUT> DataStream<OUT> transform(Function<T, OUT> mapper) {
        Objects.requireNonNull(mapper, "The mapper mapper cannot be `null'");
        return new DataStreamImpl<>(this, flow.map(mapper::apply));
    }

    @Override
    public <OUT> DataStream<OUT> transformFlow(Function<Flowable<T>, Flowable<OUT>> mapper) {
        Objects.requireNonNull(mapper, "The mapper mapper cannot be `null'");
        return new DataStreamImpl<>(this, flow.compose(mapper::apply));
    }

    @Override
    public DataStream<T> broadcastTo(@NotNull DataStream... streams) {
        ConnectableFlowable<T> publish = flow.replay();
        DataStreamImpl<T, T> stream = new DataStreamImpl<>(this, publish);

        for (DataStream s : streams) {
            DataStream first = s;
            while (first.previous() != null) {
                first = first.previous();
            }
            if (first.isConnectable()) {
                first.connect(stream);
            } else {
                throw new IllegalArgumentException("The stream head is not connectable");
            }
        }
        publish.connect();
        return stream;
    }

    public void connect(DataStream<T> source) {
        this.connector.connectDownstream(source);

    }

    @Override
    public DataStream<T> onData(Consumer<? super T> consumer) {
        return new DataStreamImpl<>(this, flow.doOnNext(consumer::accept));
    }

    public DataStream<I> previous() {
        return previous;
    }

    public boolean isConnectable() {
        return connectable;
    }

    @Override
    public void broadcastTo(@NotNull Sink... sinks) {
        ConnectableFlowable<T> publish = flow.publish();
        for (Sink s : sinks) {
            publish
                .flatMapCompletable(s::dispatch)
                .subscribe();
        }
        publish.connect();
    }

    @Override
    public Flowable<T> flow() {
        return flow;
    }

    // TODO What should be the return type of the "to" operation
    // Sink can produce one result, so maybe a Future. Not a single as it would delay the subscription.

    @Override
    public Sink<T> to(Sink<T> sink) {
        // TODO Error management
        flow
            .flatMapCompletable(sink::dispatch)
            .subscribe(
                () -> {

                },
                Throwable::printStackTrace // TODO Error management
            );

        return sink;
    }
}