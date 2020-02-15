/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge.queue;

import android.os.Looper;
import com.facebook.react.common.MapBuilder;
import java.util.Map;

public class ReactQueueConfigurationImpl implements ReactQueueConfiguration {
  /**
   * 消息队列的实现来，这里面我们有三个线程类：
   * 1、负责UI渲染的main_ui线程
   * 2、与NativeModule相关的mqt_native_modules线程
   * 3、负责执行JS代码的mqt_js线程
   */
  private final MessageQueueThreadImpl mUIQueueThread;
  private final MessageQueueThreadImpl mNativeModulesQueueThread;
  private final MessageQueueThreadImpl mJSQueueThread;

  /**
   * 这个获取ReactQueuede传入的是这个
   */
  private ReactQueueConfigurationImpl(
      MessageQueueThreadImpl uiQueueThread,
      MessageQueueThreadImpl nativeModulesQueueThread,
      MessageQueueThreadImpl jsQueueThread) {
    // 传入这三个线程类    
    mUIQueueThread = uiQueueThread;
    mNativeModulesQueueThread = nativeModulesQueueThread;
    mJSQueueThread = jsQueueThread;
  }

  @Override
  public MessageQueueThread getUIQueueThread() {
    return mUIQueueThread;
  }

  @Override
  public MessageQueueThread getNativeModulesQueueThread() {
    return mNativeModulesQueueThread;
  }

  @Override
  public MessageQueueThread getJSQueueThread() {
    return mJSQueueThread;
  }

  /**
   * Should be called when the corresponding {@link com.facebook.react.bridge.CatalystInstance} is
   * destroyed so that we shut down the proper queue threads.
   * 
   * 
   */
  public void destroy() {
    // 进行destroy的时候
    if (mNativeModulesQueueThread.getLooper() != Looper.getMainLooper()) {
      mNativeModulesQueueThread.quitSynchronous();
    }
    if (mJSQueueThread.getLooper() != Looper.getMainLooper()) {
      mJSQueueThread.quitSynchronous();
    }
  }

  public static ReactQueueConfigurationImpl create(
      ReactQueueConfigurationSpec spec, QueueThreadExceptionHandler exceptionHandler) {
    Map<MessageQueueThreadSpec, MessageQueueThreadImpl> specsToThreads = MapBuilder.newHashMap();

    MessageQueueThreadSpec uiThreadSpec = MessageQueueThreadSpec.mainThreadSpec();
    MessageQueueThreadImpl uiThread = MessageQueueThreadImpl.create(uiThreadSpec, exceptionHandler);
    specsToThreads.put(uiThreadSpec, uiThread);

    MessageQueueThreadImpl jsThread = specsToThreads.get(spec.getJSQueueThreadSpec());
    if (jsThread == null) {
      jsThread = MessageQueueThreadImpl.create(spec.getJSQueueThreadSpec(), exceptionHandler);
    }

    MessageQueueThreadImpl nativeModulesThread =
        specsToThreads.get(spec.getNativeModulesQueueThreadSpec());
    if (nativeModulesThread == null) {
      nativeModulesThread =
          MessageQueueThreadImpl.create(spec.getNativeModulesQueueThreadSpec(), exceptionHandler);
    }

    return new ReactQueueConfigurationImpl(uiThread, nativeModulesThread, jsThread);
  }
}
