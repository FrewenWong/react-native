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

  private final MessageQueueThreadImpl mUIQueueThread;
  private final MessageQueueThreadImpl mNativeModulesQueueThread;
  private final MessageQueueThreadImpl mJSQueueThread;

  /**
   * 我们可以看一下他的构造函数
   * 上面的ReactQueueConfigurationSpec.createDefault()方法生成了默认的ReactQueueConfigurationSpec，告诉后续流程需要创建Native线程与JS线程，
   */
  private ReactQueueConfigurationImpl(
      MessageQueueThreadImpl uiQueueThread,
      MessageQueueThreadImpl nativeModulesQueueThread,
      MessageQueueThreadImpl jsQueueThread) {
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
   */
  public void destroy() {
    if (mNativeModulesQueueThread.getLooper() != Looper.getMainLooper()) {
      mNativeModulesQueueThread.quitSynchronous();
    }
    if (mJSQueueThread.getLooper() != Looper.getMainLooper()) {
      mJSQueueThread.quitSynchronous();
    }
  }

  /**
   * 上面的ReactQueueConfigurationSpec.createDefault()方法生成了默认的ReactQueueConfigurationSpec，
   * 告诉后续流程需要创建Native线程与JS线程，
   * 
   * 可以看出，该方法生成UI线程、Native线程与JS线程各自的MessageQueueThreadImpl，
   * 创建各自对应的线程，并设置相应的ExceptionHandler
   */
  public static ReactQueueConfigurationImpl create(
      ReactQueueConfigurationSpec spec, QueueThreadExceptionHandler exceptionHandler) {
    Map<MessageQueueThreadSpec, MessageQueueThreadImpl> specsToThreads = MapBuilder.newHashMap();
    // 创建关于UI线程的MessageQueueThreadSpec，并设置ExceptionHandler
    MessageQueueThreadSpec uiThreadSpec = MessageQueueThreadSpec.mainThreadSpec();
    // 创建UI线程
    MessageQueueThreadImpl uiThread = MessageQueueThreadImpl.create(uiThreadSpec, exceptionHandler);
    specsToThreads.put(uiThreadSpec, uiThread);
    //给JS线程设置ExceptionHandler
    MessageQueueThreadImpl jsThread = specsToThreads.get(spec.getJSQueueThreadSpec());
    if (jsThread == null) {
      //创建JS线程
      jsThread = MessageQueueThreadImpl.create(spec.getJSQueueThreadSpec(), exceptionHandler);
    }
    //给Native线程设置ExceptionHandler
    MessageQueueThreadImpl nativeModulesThread =
        specsToThreads.get(spec.getNativeModulesQueueThreadSpec());
    if (nativeModulesThread == null) {
      //创建Native线程
      nativeModulesThread =
          MessageQueueThreadImpl.create(spec.getNativeModulesQueueThreadSpec(), exceptionHandler);
    }

    return new ReactQueueConfigurationImpl(uiThread, nativeModulesThread, jsThread);
  }
}
