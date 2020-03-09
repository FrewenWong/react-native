/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge.queue;

/**
 * Specifies which {@link MessageQueueThread}s must be used to run the various contexts of execution
 * within catalyst (Main UI thread, native modules, and JS). Some of these queues *may* be the same
 * but should be coded against as if they are different.
 *
 * <p>UI Queue Thread: The standard Android main UI thread and Looper. Not configurable. Native
 * Modules Queue Thread: The thread and Looper that native modules are invoked on. JS Queue Thread:
 * The thread and Looper that JS is executed on.
 * ReactQueueConfiguration：接口，描述了RN应用中的3个线程，UI线程、Native线程与JS线程。
 */
public interface ReactQueueConfiguration {
  MessageQueueThread getUIQueueThread();

  MessageQueueThread getNativeModulesQueueThread();

  MessageQueueThread getJSQueueThread();

  void destroy();
}
