package com.googlecode.concurrentlinkedhashmap.caches;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A forwarding {@link ConcurrentMap} that wraps each call with a mutex.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class SynchronizedForwardingConcurrentMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<K, V> delegate;
  private final Object lock;

  public SynchronizedForwardingConcurrentMap(ConcurrentMap<K, V> delegate) {
    this.delegate = delegate;
    this.lock = new Object();
  }

  public boolean isEmpty() {
    synchronized (lock) {
      return delegate.isEmpty();
    }
  }

  public int size() {
    synchronized (lock) {
      return delegate.size();
    }
  }

  public void clear() {
    synchronized (lock) {
      delegate.clear();
    }
  }

  public boolean containsKey(Object key) {
    synchronized (lock) {
      return delegate.containsKey(key);
    }
  }

  public boolean containsValue(Object value) {
    synchronized (lock) {
      return delegate.containsValue(value);
    }
  }

  public V get(Object key) {
    synchronized (lock) {
      return delegate.get(key);
    }
  }

  public V put(K key, V value) {
    synchronized (lock) {
      return delegate.put(key, value);
    }
  }

  public V putIfAbsent(K key, V value) {
    synchronized (lock) {
      return delegate.putIfAbsent(key, value);
    }
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    synchronized (lock) {
      delegate.putAll(map);
    }
  }

  public V remove(Object key) {
    synchronized (lock) {
      return delegate.remove(key);
    }
  }

  public boolean remove(Object key, Object value) {
    synchronized (lock) {
      return delegate.remove(key, value);
    }
  }

  public boolean replace(K key, V oldValue, V newValue) {
    synchronized (lock) {
      return delegate.replace(key, oldValue, newValue);
    }
  }

  public V replace(K key, V value) {
    synchronized (lock) {
      return delegate.replace(key, value);
    }
  }

  public Set<K> keySet() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.keySet());
    }
  }

  public Collection<V> values() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.values());
    }
  }

  public Set<Entry<K, V>> entrySet() {
    synchronized (lock) {
      return newLinkedHashSet(delegate.entrySet());
    }
  }

  @Override
  public boolean equals(Object object) {
    synchronized (lock) {
      return delegate.equals(object);
    }
  }

  @Override
  public int hashCode() {
    synchronized (lock) {
      return delegate.hashCode();
    }
  }
}