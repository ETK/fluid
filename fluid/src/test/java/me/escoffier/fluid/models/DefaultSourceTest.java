package me.escoffier.fluid.models;

import io.reactivex.Flowable;
import io.reactivex.Single;
import me.escoffier.fluid.impl.ListSink;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Checks the behavior of {@link DefaultSource}.
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DefaultSourceTest {

  @Test
  public void testNamedSource() {
    Source<String> source = Source.from("a", "b", "c")
      .named("my source");

    assertThat(source.name()).isEqualTo("my source");

    assertThat(source.named("foo").name()).isEqualTo("foo");

    assertThat(source.unnamed().name()).isNull();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullName() {
    Source.from("a", "b", "c").named(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBlankName() {
    Source.from("a", "b", "c").named(" ");
  }

  @Test
  public void testAttributes() {
    Source<String> source = Source.from("a", "b", "c")
      .named("my source");

    assertThat(source.attr("foo")).isEmpty();

    source = source.withAttribute("foo", "bar").withAttribute("k", "v");
    assertThat(source.attr("foo")).get().isEqualTo("bar");
    assertThat(source.attr("k")).get().isEqualTo("v");

    source = source.withoutAttribute("foo").withoutAttribute("boo");
    assertThat(source.attr("foo")).isEmpty();
    assertThat(source.attr("k")).get().isEqualTo("v");
  }

  @Test
  public void testOrElse() {
    Source<String> source = Source.from("a", "b", "c")
      .named("my source");

    Source<String> empty = Source.<String>empty().named("empty");

    Source<String> another = Source.from("d", "e", "f");

    ListSink<String> sink = Sink.list();
    source.orElse(another).to(sink);
    assertThat(sink.values()).containsExactly("a", "b", "c");

    sink = Sink.list();
    empty.orElse(source).to(sink);
    assertThat(sink.values()).containsExactly("a", "b", "c");

    sink = Sink.list();
    empty.orElse(another).to(sink);
    assertThat(sink.values()).containsExactly("d", "e", "f");

    sink = Sink.list();
    empty.orElse(empty).to(sink);
    assertThat(sink.values()).isEmpty();
  }


  @Test
  public void testMapOnItem() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .mapPayload(i -> i + 1)
      .to(list);

    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testFilterOnMessage() {
    ListSink<Integer> sink = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .filter(msg -> msg.payload() >= 3)
      .to(sink);
    assertThat(sink.values()).containsExactly(3, 4, 5);
  }

  @Test
  public void testFilterNotOnMessage() {
    ListSink<Integer> sink = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .filterNot(msg -> msg.payload() >= 3)
      .to(sink);
    assertThat(sink.values()).containsExactly(1, 2);
  }

  @Test
  public void testFilterOnPayloads() {
    ListSink<Integer> sink = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .filterPayload(i -> i >= 3)
      .to(sink);
    assertThat(sink.values()).containsExactly(3, 4, 5);
  }

  @Test
  public void testFilterNotOnPayloads() {
    ListSink<Integer> sink = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .filterNotPayload(i -> i >= 3)
      .to(sink);
    assertThat(sink.values()).containsExactly(1, 2);
  }

  @Test
  public void testCompose() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .composeFlowable(flow -> flow.map(Message::payload).map(i -> i + 1).map(Message::new))
      .to(list);

    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testTransformer() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .composePayloadFlowable(flow -> flow.map(i -> i + 1))
      .to(list);
    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testThatWeCanRetrieveTheFlowable() {
    List<Integer> list = Source.from(1, 2, 3, 4, 5)
      .asFlowable()
      .map(Message::payload)
      .toList()
      .blockingGet();
    assertThat(list).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  public void testMergeWith() {
    Source<String> s1 = Source.from("a", "b", "c");
    Source<String> s2 = Source.from("d", "e", "f");
    Source<String> s3 = Source.from("g", "h");

    ListSink<String> list = Sink.list();
    s1.mergeWith(s2).to(list);

    assertThat(list.values()).containsExactly("a", "b", "c", "d", "e", "f");

    Random random = new Random();
    ListSink<String> list2 = Sink.list();
    s1.composeFlowable(flow ->
      flow.delay(s ->
        Single.just(s)
          .delay(random.nextInt(10), TimeUnit.MILLISECONDS)
          .toFlowable()))
      .mergeWith(s2, s3)
      .to(list2);

    await().atMost(1, TimeUnit.MINUTES).until(() -> list2.values().size() == 8);
    assertThat(list2.values()).contains("a", "b", "c", "d", "e", "f", "g", "h");
  }

  @Test
  public void testZipWithAnotherStream() {
    Source<String> s1 = Source.from("a", "b", "c");
    Source<String> s2 = Source.from("d", "e", "f");
    Source<String> s3 = Source.from("g", "h", "i");

    ListSink<String> list = Sink.list();
    s1.composePayloadFlowable(s -> s.map(String::toUpperCase))
      .zipWith(s2)
      .mapPayload(pair -> pair.left() + "x" + pair.right())
      .to(list);

    assertThat(list.values()).containsExactly("Axd", "Bxe", "Cxf");

    list = Sink.list();
    s1.composePayloadFlowable(s -> s.map(String::toUpperCase))
      .zipWith(s2, s3)
      .mapPayload(tuple -> tuple.nth(0) + "x" + tuple.nth(1) + "x" + tuple.nth(2))
      .to(list);

    assertThat(list.values()).containsExactly("Axdxg", "Bxexh", "Cxfxi");
  }

  @Test
  public void testDataTransformation() {
    ListSink<Integer> sink = new ListSink<>();
    Source.from(Flowable.range(0, 10).map(i -> random()))
      .map(data -> data.with((int) (data.payload() * 100)))
      .to(sink);
    assertThat(sink.values()).hasSize(10);
    assertThat(sink.data()).hasSize(10);

    for (Message<Integer> d : sink.data()) {
      assertThat(d.<Long>get("X-Timestamp")).isNotNull().isGreaterThanOrEqualTo(0);
      assertThat(d.<Boolean>get("Random")).isTrue();
      assertThat(d.payload()).isGreaterThanOrEqualTo(0).isLessThan(100);
    }
  }



  private static Message<Double> random() {
    return new Message<>(Math.random()).with("X-Timestamp", System.currentTimeMillis()).with("Random", true);
  }


}
