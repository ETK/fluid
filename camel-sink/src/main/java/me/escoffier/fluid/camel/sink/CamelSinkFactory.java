package me.escoffier.fluid.camel.sink;

import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.fluid.config.Config;
import me.escoffier.fluid.models.Sink;
import me.escoffier.fluid.spi.SinkFactory;

public class CamelSinkFactory implements SinkFactory {

    @Override public String name() {
        return "camel";
    }

    @Override public <T> Single<Sink<T>> create(Vertx vertx, String name, Config config) {
        return Single.just(new CamelSink<>(name, config));
    }

}
