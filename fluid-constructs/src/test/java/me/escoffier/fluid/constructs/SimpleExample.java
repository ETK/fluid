package me.escoffier.fluid.constructs;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.reactivex.core.Vertx;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class SimpleExample {


    private Vertx vertx;

    @BeforeEach
    public void setup() {
        vertx = Vertx.vertx();
    }

    @AfterEach
    public void teardown() {
        vertx.close();
    }

    @Test
    void testWithRange() {
        CacheSink<Integer> sink = new CacheSink<>();
        Source.from(Flowable.range(0, 10))
            .transform(i -> {
                System.out.println("Item: " + i);
                return i;
            })
            .to(sink);

        await().until(() -> sink.buffer.size() == 10);
    }

    @Test
    void testWithRange2() {
        Source.from(Flowable.range(0, 10))
            .to(Sink.forEach(System.out::println));
    }

    @Test
    void testWithFactorial() throws IOException {
        FileSink sink = new FileSink(vertx, "factorial.txt");
        getFactorialFlow()
            .transform(i -> i.toString() + "\n")
            .to(sink);

        await().until(() -> FileUtils.readLines(new File("factorial.txt"), "UTF-8").size() >= 10);
        assertThat(FileUtils.readLines(new File("factorial.txt"), "UTF-8")).contains("3628800");
    }

    @Test
    void testQuotes() {
        CacheSink<String> cache = new CacheSink<>();
        List<Quote> quotes = new ArrayList<>();
        quotes.add(new Quote("Attitude is everything", "Diane Von Furstenberg"));
        quotes.add(new Quote("Life is short, heels shouldn't be", "Brian Atwood"));
        quotes.add(new Quote("Red is the color for fall", "Piera Gelardi"));
        quotes.add(new Quote("Rhinestones make everything better", "Piera Gelardi"));
        quotes.add(new Quote("Design is so simple, that's why it's so complicated", "Paul Rand"));

        //TODO What is this generic issue?
        Source.from(quotes.stream())
            .transform(q -> q.author)
            .transformFlow(Flowable::distinct)
            .transform(String::toUpperCase)
            .broadcastTo(Sink.forEach(System.out::println), cache);

        await().until(() -> cache.cache().size() == 4);
        assertThat(cache.cache()).contains("PAUL RAND", "PIERA GELARDI");


    }

    private class Quote {
        public final String quote;
        public final String author;

        public Quote(String quote, String author) {
            this.author = author;
            this.quote = quote;
        }
    }

    Sink<BigInteger> toLineInFile() {
        FileSink sink = new FileSink(vertx, "factorial-2.txt");
        return data ->
            Single.just(data)
                .map(d -> d.toString() + "\n")
                .flatMapCompletable(sink::dispatch);
    }

    @Test
    void testWithFactorialUsingBuiltSink() throws IOException {
        getFactorialFlow()
            .to(toLineInFile());

        await().until(() -> FileUtils.readLines(new File("factorial-2.txt"), "UTF-8").size() >= 10);
        assertThat(FileUtils.readLines(new File("factorial-2.txt"), "UTF-8")).contains("3628800");
    }

    private DataStream<BigInteger> getFactorialFlow() {
        return Source.from(Flowable.range(1, 10))
            .transformFlow(flow -> flow.scan(BigInteger.ONE, (acc, next) -> acc.multiply(BigInteger.valueOf(next))));
    }

    @Test
    void testTimeBasedManipulation() {
        CacheSink<String> cache = new CacheSink<>();
        getFactorialFlow()
            .transformFlow(flow ->
                flow.zipWith(Flowable.range(0, 99), (num, idx) -> String.format("%d! = %s", idx, num))
                    .delay(1, TimeUnit.SECONDS))
            .broadcastTo(Sink.forEach(System.out::println), cache);

        await().until(() -> cache.buffer.size() >= 10);
    }

    @Test
    void testComplexShaping() {
        /*
        final UniformFanOutShape<Tweet, Tweet> bcast = b.add(Broadcast.create(2));
  final FlowShape<Tweet, Author> toAuthor =
	  b.add(Flow.of(Tweet.class).map(t -> t.author));
  final FlowShape<Tweet, Hashtag> toTags =
      b.add(Flow.of(Tweet.class).mapConcat(t -> new ArrayList<Hashtag>(t.hashtags())));
  final SinkShape<Author> authors = b.add(writeAuthors);
  final SinkShape<Hashtag> hashtags = b.add(writeHashtags);

  b.from(b.add(tweets)).viaFanOut(bcast).via(toAuthor).to(authors);
                             b.from(bcast).via(toTags).to(hashtags);
  return ClosedShape.getInstance();
         */

        Transformer<Quote, String> toAuthor = flow -> flow.map(q -> q.author);
        Transformer<Quote, String> toWords = flow -> flow
            .concatMap(q -> Flowable.fromArray(q.quote.split(" ")));
        CacheSink<String> authors = new CacheSink<>();
        CacheSink<String> words = new CacheSink<>();

        List<Quote> quotes = new ArrayList<>();
        quotes.add(new Quote("Attitude is everything", "Diane Von Furstenberg"));
        quotes.add(new Quote("Life is short, heels shouldn't be", "Brian Atwood"));
        quotes.add(new Quote("Red is the color for fall", "Piera Gelardi"));
        quotes.add(new Quote("Rhinestones make everything better", "Piera Gelardi"));
        quotes.add(new Quote("Design is so simple, that's why it's so complicated", "Paul Rand"));

        DataStream<String> s1 = DataStream.of(Quote.class).transformWith(toAuthor)
            .transformFlow(Flowable::distinct);
        DataStream<String> s2 = DataStream.of(Quote.class).transformWith(toWords)
            .transformFlow(Flowable::distinct);

        Source.from(quotes.stream())
            .broadcastTo(s1, s2);

        s1.to(authors);
        s2.to(words);

        await().until(() -> authors.cache().size() == 4);
        System.out.println(authors.cache());
        System.out.println(words.cache());
    }

    @Test
    void testMerge() {
        Flowable<String> f1 = Flowable.fromArray("a", "b", "c")
            .delay(10, TimeUnit.MILLISECONDS);

        DataStream<String> s1 = DataStream.of(String.class)
            .transform(String::toUpperCase);
        DataStream<String> s2 = DataStream.of(String.class)
            .transform(s -> "FOO");
        Source.from(f1).broadcastTo(s1, s2);

        CacheSink<String> cache = new CacheSink<>();
        s1.mergeWith(s2).to(cache);

        await().until(() -> cache.cache().size() == 6);
        assertThat(cache.cache()).contains("A", "B", "C").contains("FOO");
    }

    @Test
    void testConcat() {
        Flowable<String> f1 = Flowable.fromArray("a", "b", "c")
            .delay(10, TimeUnit.MILLISECONDS);

        DataStream<String> s1 = DataStream.of(String.class)
            .transform(String::toUpperCase);
        DataStream<String> s2 = DataStream.of(String.class)
            .transform(s -> "FOO");
        Source.from(f1).broadcastTo(s1, s2);

        CacheSink<String> cache = new CacheSink<>();
        s1.concatWith(s2).to(cache);

        await().until(() -> cache.cache().size() == 6);
        assertThat(cache.cache()).containsExactly("A", "B", "C", "FOO", "FOO", "FOO");
    }

    @Test
    void testZip() {
        Flowable<String> f1 = Flowable.fromArray("a", "b", "c");

        Flowable<String> f2 = Flowable.fromArray("1", "2", "3");

        CacheSink<String> cache = new CacheSink<>();
        Source.from(f1).transform(String::toUpperCase).zipWith(Source.from(f2))
            .transform(pair -> pair.left() + ":" + pair.right() + "\n")
            .to(cache);

        await().until(() -> cache.cache().size() == 3);
        assertThat(cache.cache()).containsExactly("A:1\n", "B:2\n", "C:3\n");
    }

    @Test
    void testFold() {
        Sink.ScanSink<Integer, Integer> sink = Sink.fold(0, (i, v) -> i + v);
        Source.from(Flowable.range(0, 3))
            .transform(i -> ++i)
            .to(sink);

        await().until(() -> sink.value() == 6);
    }

    @Test
    void testHead() {
        Sink.HeadSink<Integer> sink = Sink.head();
        Source.from(Flowable.range(0, 3))
            .transform(i -> ++i)
            .to(sink);

        await().until(() -> sink.value() == 1);
    }


}