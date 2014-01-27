/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.support.atomic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.data.redis.ConnectionFactoryTracker;
import org.springframework.data.redis.connection.ConnectionUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Integration test of {@link RedisAtomicInteger}
 * 
 * @author Costin Leau
 * @author Jennifer Hickey
 */
@RunWith(Parameterized.class)
public class RedisAtomicIntegerTests {

	private RedisAtomicInteger intCounter;
	private RedisConnectionFactory factory;

	public RedisAtomicIntegerTests(RedisConnectionFactory factory) {
		intCounter = new RedisAtomicInteger(getClass().getSimpleName() + ":int", factory);
		this.factory = factory;
		ConnectionFactoryTracker.add(factory);
	}

	@After
	public void stop() {
		RedisConnection connection = factory.getConnection();
		connection.flushDb();
		connection.close();
	}

	@AfterClass
	public static void cleanUp() {
		ConnectionFactoryTracker.cleanUp();
	}

	@Parameters
	public static Collection<Object[]> testParams() {
		return AtomicCountersParam.testParams();
	}

	@Test
	public void testCheckAndSet() throws Exception {
		// Txs not supported in Jredis
		assumeTrue(!ConnectionUtils.isJredis(factory));
		intCounter.set(0);
		assertFalse(intCounter.compareAndSet(1, 10));
		assertTrue(intCounter.compareAndSet(0, 10));
		assertTrue(intCounter.compareAndSet(10, 0));
	}

	@Test
	public void testIncrementAndGet() throws Exception {
		intCounter.set(0);
		assertEquals(1, intCounter.incrementAndGet());
	}

	@Test
	public void testAddAndGet() throws Exception {
		intCounter.set(0);
		int delta = 5;
		assertEquals(delta, intCounter.addAndGet(delta));
	}

	@Test
	public void testDecrementAndGet() throws Exception {
		intCounter.set(1);
		assertEquals(0, intCounter.decrementAndGet());
	}

	@Test
	@Ignore("DATAREDIS-108 Test is intermittently failing")
	public void testCompareSet() throws Exception {
		// Txs not supported in Jredis
		assumeTrue(!ConnectionUtils.isJredis(factory));
		final AtomicBoolean alreadySet = new AtomicBoolean(false);
		final int NUM = 50;
		final String KEY = getClass().getSimpleName() + ":atomic:counter";
		final CountDownLatch latch = new CountDownLatch(NUM);

		final AtomicBoolean failed = new AtomicBoolean(false);
		for (int i = 0; i < NUM; i++) {
			new Thread(new Runnable() {
				public void run() {
					RedisAtomicInteger atomicInteger = new RedisAtomicInteger(KEY, factory);
					try {
						if (atomicInteger.compareAndSet(0, 1)) {
							System.out.println(atomicInteger.get());
							if (alreadySet.get()) {
								failed.set(true);
							}
							alreadySet.set(true);
						}
					} finally {
						latch.countDown();
					}
				}
			}).start();
		}
		latch.await();

		assertFalse("counter already modified", failed.get());
	}
}
