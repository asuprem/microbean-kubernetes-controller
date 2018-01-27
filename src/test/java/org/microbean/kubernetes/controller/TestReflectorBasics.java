/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2017-2018 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.kubernetes.controller;

import java.io.Closeable;

import java.time.Duration;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;

import io.fabric8.kubernetes.client.dsl.Listable;
import io.fabric8.kubernetes.client.dsl.VersionWatchable;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import io.fabric8.kubernetes.client.Watcher;

@Deprecated
public class TestReflectorBasics {

  public TestReflectorBasics() {
    super();
  }

  // @Ignore
  @Test
  public void testBasics() throws Exception {
    assumeFalse(Boolean.getBoolean("skipClusterTests"));

    final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
    assertNotNull(executorService);

    final DefaultKubernetesClient client = new DefaultKubernetesClient();

    final Map<Object, ConfigMap> configMaps = new HashMap<>();
    final EventQueueCollection<ConfigMap> eventQueues = new EventQueueCollection<>(configMaps, 16, 0.75f);
    
    final Consumer<? super EventQueue<? extends ConfigMap>> siphon = (q) -> {
      assertNotNull(q);
      assertFalse(q.isEmpty());
      for (final Event<? extends ConfigMap> event : q) {
        assertNotNull(event);
        System.out.println("*** received event: " + event);
        final Event.Type type = event.getType();
        assertNotNull(type);
        switch (type) {
        case DELETION:
          configMaps.remove(event.getKey());
          break;
        default:
          configMaps.put(event.getKey(), event.getResource());
          break;
        }
      }
    };

    // Begin sucking EventQueue instances out of the cache.  Obviously
    // there aren't any yet.  This creates a new (daemon) Thread and
    // starts it.
    eventQueues.start(siphon);

    final Reflector<ConfigMap> reflector =
      new Reflector<ConfigMap>(client.configMaps(),
                               eventQueues,
                               executorService,
                               Duration.ofSeconds(10));

    // Begin effectively putting EventQueue instances into the cache.
    // This creates a new (daemon) Thread and starts it.
    reflector.start();
    
    Thread.sleep(1L * 60L * 1000L);

    // Shut down production of events.
    reflector.close();
    client.close();

    // Shut down reception of events.
    eventQueues.close();
  }

  public static final void main(final String[] args) throws Exception {
    new TestReflectorBasics().testBasics();
  }

}
