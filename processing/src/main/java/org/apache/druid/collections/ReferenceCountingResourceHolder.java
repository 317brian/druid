/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.collections;

import org.apache.druid.java.util.common.Cleaners;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.logger.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ReferenceCountingResourceHolder<T> implements ResourceHolder<T>
{
  private static final Logger log = new Logger(ReferenceCountingResourceHolder.class);

  private static final AtomicLong LEAKED_RESOURCES = new AtomicLong();

  public static long leakedResources()
  {
    return LEAKED_RESOURCES.get();
  }

  private final T object;
  private final Closeable closer;
  private final AtomicInteger refCount = new AtomicInteger(1);
  private final AtomicBoolean closed = new AtomicBoolean(false);
  /**
   * The point of Cleaner is to be referenced. Action is performed when it becomes unreachable, so it doesn't need
   * to be used directly.
   */
  @SuppressWarnings("unused")
  private final Object cleanable;

  public ReferenceCountingResourceHolder(final T object, final Closeable closer)
  {
    this.object = object;
    this.closer = closer;
    this.cleanable = Cleaners.register(this, new CloserRunnable(object, closer, refCount));
  }

  public static <T extends Closeable> ReferenceCountingResourceHolder<T> fromCloseable(final T object)
  {
    return new ReferenceCountingResourceHolder<>(object, object);
  }

  /**
   * Returns the resource with an initial reference count of 1. More references can be added by
   * calling {@link #increment()}.
   */
  @Override
  public T get()
  {
    if (refCount.get() <= 0) {
      throw new ISE("Already closed!");
    }
    return object;
  }

  /**
   * Increments the reference count by 1 and returns a {@link ResourceHolder} representing the new references.
   * The returned {@link ResourceHolder} "close" method decrements the reference count when the caller no longer
   * needs the resource.
   *
   * Returned {@link ResourceHolder} are not thread-safe. If multiple threads need references to the same resource, they
   * should each call this method on the original object.
   */
  public ResourceHolder<T> increment()
  {
    while (true) {
      int count = this.refCount.get();
      if (count <= 0) {
        throw new ISE("Already closed!");
      }
      if (refCount.compareAndSet(count, count + 1)) {
        break;
      }
    }

    // This ResourceHolder is supposed to be used from a single thread, so no synchronization/atomicity
    return new ResourceHolder<>()
    {
      boolean released = false;

      @Override
      public T get()
      {
        return object;
      }

      @Override
      public void close()
      {
        if (!released) {
          decrement();
          released = true;
        } else {
          log.warn(new ISE("Already closed"), "Already closed");
        }
      }
    };
  }

  /**
   * Decrements the reference count by 1. If it reaches to 0, then closes {@link #closer}.
   */
  @Override
  public void close()
  {
    if (closed.compareAndSet(false, true)) {
      decrement();
    } else {
      log.warn(new ISE("Already closed"), "Already closed");
    }
  }

  private void decrement()
  {
    // Checking that the count is exactly equal to 0, rather than less or equal, helps to avoid calling closer.close()
    // twice if there is a race with CloserRunnable. Such a race is possible and could be avoided only with
    // reachabilityFence() (Java 9+) in ReferenceCountingResourceHolder's and Releaser's close() methods.
    if (refCount.decrementAndGet() == 0) {
      try {
        closer.close();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class CloserRunnable implements Runnable
  {
    private final Object object;
    private final Closeable closer;
    private final AtomicInteger refCount;

    private CloserRunnable(Object object, Closeable closer, AtomicInteger refCount)
    {
      this.object = object;
      this.closer = closer;
      this.refCount = refCount;
    }

    @Override
    public void run()
    {
      while (true) {
        int count = refCount.get();
        if (count <= 0) {
          return;
        }
        if (refCount.compareAndSet(count, 0)) {
          try {
            LEAKED_RESOURCES.incrementAndGet();
            closer.close();
            return;
          }
          catch (Exception e) {
            try {
              log.error(e, "Exception in closer");
            }
            catch (Exception ignore) {
              // ignore
            }
          }
          finally {
            try {
              log.warn("Not closed! Object was[%s]", object);
            }
            catch (Exception ignore) {
              // ignore
            }
          }
        }
      }
    }
  }
}
