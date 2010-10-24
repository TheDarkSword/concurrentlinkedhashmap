package com.googlecode.concurrentlinkedhashmap;

import static com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.RECENCY_THRESHOLD;
import static com.googlecode.concurrentlinkedhashmap.IsValidState.valid;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableMap;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Builder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap.Node;

import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A unit-test for the page replacement algorithm and its public methods.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(groups = "development")
public final class EvictionTest extends BaseTest {

  @Override
  protected int capacity() {
    return 100;
  }

  @Test(dataProvider = "warmedMap")
  public void capacity_increase(ConcurrentLinkedHashMap<Integer, Integer> map) {
    Map<Integer, Integer> expected = ImmutableMap.copyOf(newWarmedMap());
    int newMaxCapacity = 2 * capacity();

    map.setCapacity(newMaxCapacity);
    assertThat(map, is(equalTo(expected)));
    assertThat(map.capacity(), is(equalTo(newMaxCapacity)));
  }

  @Test
  public void capacity_decrease() {
    checkDecreasedCapacity(capacity() / 2);
  }

  @Test
  public void capacity_decreaseToMinimum() {
    checkDecreasedCapacity(0);
  }

  private void checkDecreasedCapacity(int newMaxCapacity) {
    CollectingListener<Integer, Integer> listener = new CollectingListener<Integer, Integer>();
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .listener(listener)
        .build();
    warmUp(map, 0, capacity());
    map.setCapacity(newMaxCapacity);

    assertThat(map, is(valid()));
    assertThat(map.size(), is(equalTo(newMaxCapacity)));
    assertThat(map.capacity(), is(equalTo(newMaxCapacity)));
    assertThat(listener.evicted, hasSize(capacity() - newMaxCapacity));
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalArgumentException.class)
  public void capacity_decreaseBelowMinimum(ConcurrentLinkedHashMap<Integer, Integer> map) {
    try {
      map.setCapacity(-1);
    } finally {
      assertThat(map.capacity(), is(equalTo(capacity())));
    }
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void evictionListener_fails() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .listener(new EvictionListener<Integer, Integer>() {
          @Override public void onEviction(Integer key, Integer value) {
            throw new IllegalStateException();
          }
        })
        .maximumWeightedCapacity(0)
        .build();
    try {
      warmUp(map, 0, capacity());
    } finally {
      assertThat(map, is(valid()));
    }
  }

  @Test
  public void evictUntil_neverDiscard() {
    checkEvictUntil(new SizeLimiter() {
      @Override public boolean hasExceededLimit(ConcurrentLinkedHashMap<?, ?> map) {
        return false;
      }
    }, capacity());
  }

  @Test
  public void evictUntil_alwaysDiscard() {
    checkEvictUntil(new SizeLimiter() {
      @Override public boolean hasExceededLimit(ConcurrentLinkedHashMap<?, ?> map) {
        return true;
      }
    }, 0);
  }

  @Test
  public void evictUntil_decrease() {
    final int maxSize = capacity() / 2;
    checkEvictUntil(new SizeLimiter() {
      @Override public boolean hasExceededLimit(ConcurrentLinkedHashMap<?, ?> map) {
        return map.size() > maxSize;
      }
    }, maxSize);
  }

  @Test(dataProvider = "warmedMap", expectedExceptions = IllegalStateException.class)
  public void evictUntil_fails(ConcurrentLinkedHashMap<Integer, Integer> map) {
    map.evictUntil(new SizeLimiter() {
      @Override public boolean hasExceededLimit(ConcurrentLinkedHashMap<?, ?> map) {
        throw new IllegalStateException();
      }
    });
  }

  private void checkEvictUntil(SizeLimiter sizeLimiter, int expectedSize) {
    CollectingListener<Integer, Integer> listener = new CollectingListener<Integer, Integer>();
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(capacity())
        .listener(listener)
        .build();
    warmUp(map, 0, capacity());
    map.evictUntil(sizeLimiter);

    assertThat(map, is(valid()));
    assertThat(map.size(), is(expectedSize));
    assertThat(listener.evicted, hasSize(capacity() - expectedSize));
  }

  @Test(dataProvider = "collectingListener")
  public void evict_alwaysDiscard(CollectingListener<Integer, Integer> listener) {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(0)
        .listener(listener)
        .build();
    warmUp(map, 0, 100);

    assertThat(map, is(valid()));
    assertThat(listener.evicted, hasSize(100));
  }

  @Test(dataProvider = "collectingListener")
  public void evict(CollectingListener<Integer, Integer> listener) {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(10)
        .listener(listener)
        .build();
    warmUp(map, 0, 20);

    assertThat(map, is(valid()));
    assertThat(map.size(), is(10));
    assertThat(map.weightedSize(), is(10));
    assertThat(listener.evicted, hasSize(10));
  }

  @Test
  public void evict_weighted() {
    ConcurrentLinkedHashMap<Integer, Collection<Integer>> map =
      new Builder<Integer, Collection<Integer>>()
        .weigher(Weighers.<Integer>collection())
        .maximumWeightedCapacity(10)
        .build();

    map.put(1, asList(1, 2));
    map.put(2, asList(3, 4, 5, 6, 7));
    map.put(3, asList(8, 9, 10));
    assertThat(map.weightedSize(), is(10));

    // evict (1)
    map.put(4, asList(11));
    assertThat(map.containsKey(1), is(false));
    assertThat(map.weightedSize(), is(9));

    // evict (2, 3)
    map.put(5, asList(12, 13, 14, 15, 16, 17, 18, 19, 20));
    assertThat(map.weightedSize(), is(10));

    assertThat(map, is(valid()));
  }

  @Test
  public void evict_lru() {
    ConcurrentLinkedHashMap<Integer, Integer> map = new Builder<Integer, Integer>()
        .maximumWeightedCapacity(10)
        .build();
    warmUp(map, 0, 10);
    assertThat(map.keySet(), containsInAnyOrder(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

    // re-order
    checkReorder(map, asList(0, 1, 2), 3, 4, 5, 6, 7, 8, 9, 0, 1, 2);

    // evict 3, 4, 5
    checkEvict(map, asList(10, 11, 12), 6, 7, 8, 9, 0, 1, 2, 10, 11, 12);

    // re-order
    checkReorder(map, asList(6, 7, 8), 9, 0, 1, 2, 10, 11, 12, 6, 7, 8);

    // evict 9, 0, 1
    checkEvict(map, asList(13, 14, 15), 2, 10, 11, 12, 6, 7, 8, 13, 14, 15);

    assertThat(map, is(valid()));
  }

  private void checkReorder(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.get(i);
      map.tryToDrainEvictionQueues(false);
    }
    assertThat(map.keySet(), containsInAnyOrder(expect));
  }

  private void checkEvict(ConcurrentLinkedHashMap<Integer, Integer> map,
      List<Integer> keys, Integer... expect) {
    for (int i : keys) {
      map.put(i, i);
      map.tryToDrainEvictionQueues(false);
    }
    assertThat(map.keySet(), containsInAnyOrder(expect));
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onGet(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.get(originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPutIfAbsent(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.putIfAbsent(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onPut(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.put(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplace(final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(originalHead.key, originalHead.key);
      }
    });
  }

  @Test(dataProvider = "warmedMap")
  public void updateRecency_onReplaceConditionally(
      final ConcurrentLinkedHashMap<Integer, Integer> map) {
    final ConcurrentLinkedHashMap<Integer, Integer>.Node originalHead = map.sentinel.next;
    updateRecency(map, new Runnable() {
      @Override public void run() {
        map.replace(originalHead.key, originalHead.key, originalHead.key);
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void updateRecency(ConcurrentLinkedHashMap<?, ?> map, Runnable operation) {
    Node originalHead = map.sentinel.next;

    operation.run();
    map.drainRecencyQueues();

    assertThat(map.sentinel.next, is(not(originalHead)));
    assertThat(map.sentinel.prev, is(originalHead));
    assertThat(map, is(valid()));
  }

  @Test(dataProvider = "warmedMap")
  public void drainRecencyQueue(ConcurrentLinkedHashMap<Integer, Integer> map) {
    int segment = map.segmentFor(1);
    for (int i = 0; i < RECENCY_THRESHOLD; i++) {
      map.get(1);
    }
    assertThat(map.recencyQueueLength.get(segment), is(equalTo(RECENCY_THRESHOLD)));
    map.get(1);
    assertThat(map.recencyQueueLength.get(segment), is(equalTo(0)));
  }
}