package me.escoffier.fluid.inject;

import io.reactivex.Flowable;
import me.escoffier.fluid.annotations.Inbound;
import me.escoffier.fluid.annotations.Outbound;
import me.escoffier.fluid.annotations.Transformation;
import me.escoffier.fluid.models.Message;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MediatorProvidingFlowableData {

  @Inbound("my-source")
  private Flowable<String> source;

  @Transformation
  @Outbound("my-sink")
  public Flowable<Message<String>> transform() {
    return source.map(String::toUpperCase).flatMap(s -> Flowable.fromArray(s, s)).map(Message::new);
  }
}
