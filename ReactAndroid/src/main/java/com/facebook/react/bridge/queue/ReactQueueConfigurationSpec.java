/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge.queue;

import android.os.Build;
import androidx.annotation.Nullable;
import com.facebook.infer.annotation.Assertions;

/**
 * Spec for creating a ReactQueueConfiguration. This exists so that CatalystInstance is able to set
 * Exception handlers on the MessageQueueThreads it uses and it would not be super clean if the
 * threads were configured, then passed to CatalystInstance where they are configured more. These
 * specs allows the Threads to be created fully configured.
 * ReactQueueConfigurationSpec：创建ReactQueueConfiguration的说明书，描述创建ReactQueueConfiguration的具体细节，
 */
public class ReactQueueConfigurationSpec {

  private static final long LEGACY_STACK_SIZE_BYTES = 2000000;

  private final MessageQueueThreadSpec mNativeModulesQueueThreadSpec;
  private final MessageQueueThreadSpec mJSQueueThreadSpec;

  private ReactQueueConfigurationSpec(
      MessageQueueThreadSpec nativeModulesQueueThreadSpec,
      MessageQueueThreadSpec jsQueueThreadSpec) {
    mNativeModulesQueueThreadSpec = nativeModulesQueueThreadSpec;
    mJSQueueThreadSpec = jsQueueThreadSpec;
  }

  public MessageQueueThreadSpec getNativeModulesQueueThreadSpec() {
    return mNativeModulesQueueThreadSpec;
  }

  public MessageQueueThreadSpec getJSQueueThreadSpec() {
    return mJSQueueThreadSpec;
  }

  public static Builder builder() {
    return new Builder();
  }
  /**
   * CatalystInstanceImpl的ReactQueueConfigurationSpec
   * 是由ReactQueueConfigurationSpec.createDefault()来创建的
   * MessageQueueThreadSpec并咩有做什么实际的创建工作，
   * 它相当于创建MessageQueue的说明书，告诉后续流程应该创建什么样的线程。
   */
  public static ReactQueueConfigurationSpec createDefault() {

    // 在该方法中，创建出的ReactQueueConfigurationSpec告诉后续流程需要创建Native线程与JS线程，
    // 我们进一步来看看ReactQueueConfigurationImpl.create()的实现。
    MessageQueueThreadSpec spec =
        Build.VERSION.SDK_INT < 21
            ? MessageQueueThreadSpec.newBackgroundThreadSpec(
                "native_modules", LEGACY_STACK_SIZE_BYTES)
            : MessageQueueThreadSpec.newBackgroundThreadSpec("native_modules");
    return builder()
        .setJSQueueThreadSpec(MessageQueueThreadSpec.newBackgroundThreadSpec("js"))
        .setNativeModulesQueueThreadSpec(spec)
        .build();
  }

  public static class Builder {

    private @Nullable MessageQueueThreadSpec mNativeModulesQueueSpec;
    private @Nullable MessageQueueThreadSpec mJSQueueSpec;

    public Builder setNativeModulesQueueThreadSpec(MessageQueueThreadSpec spec) {
      Assertions.assertCondition(
          mNativeModulesQueueSpec == null, "Setting native modules queue spec multiple times!");
      mNativeModulesQueueSpec = spec;
      return this;
    }

    public Builder setJSQueueThreadSpec(MessageQueueThreadSpec spec) {
      Assertions.assertCondition(mJSQueueSpec == null, "Setting JS queue multiple times!");
      mJSQueueSpec = spec;
      return this;
    }

    public ReactQueueConfigurationSpec build() {
      return new ReactQueueConfigurationSpec(
          Assertions.assertNotNull(mNativeModulesQueueSpec),
          Assertions.assertNotNull(mJSQueueSpec));
    }
  }
}
