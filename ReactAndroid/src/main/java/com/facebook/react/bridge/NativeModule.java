/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge;

import androidx.annotation.NonNull;
import com.facebook.proguard.annotations.DoNotStrip;

/**
 * A native module whose API can be provided to JS catalyst instances. {@link NativeModule}s whose
 * implementation is written in Java should extend {@link BaseJavaModule} or {@link
 * ReactContextBaseJavaModule}. {@link NativeModule}s whose implementation is written in C++ must
 * not provide any Java code (so they can be reused on other platforms), and instead should register
 * themselves using {@link CxxModuleWrapper}.
 * NativeModule是ava Module，负责Java到Js的映射调用格式声明，由CatalystInstance统一管理。
 * 
 * NativeModule是Java暴露给JS调用的APU集合，例如：ToastModule、DialogModule等，
 * UIManagerModule也是供JS调用的API集 合，它用来创建View。业务放可以通过实现NativeModule来自定义模块，
 * 通过getName()将模块名暴露给JS层，通过@ReactMethod注解将API暴露给JS层。
 */
@DoNotStrip
public interface NativeModule {
  interface NativeMethod {
    void invoke(JSInstance jsInstance, ReadableArray parameters);

    String getType();
  }

  /**
   * @return the name of this module. This will be the name used to {@code require()} this module
   *     from javascript.
   * NativeModule通过getName()将模块名暴露给JS层，通过@ReactMethod注解将API暴露给JS层。
   */
  @NonNull
  String getName();

  /**
   * This is called at the end of {@link CatalystApplicationFragment#createCatalystInstance()} after
   * the CatalystInstance has been created, in order to initialize NativeModules that require the
   * CatalystInstance or JS modules.
   */
  void initialize();

  /**
   * Return true if you intend to override some other native module that was registered e.g. as part
   * of a different package (such as the core one). Trying to override without returning true from
   * this method is considered an error and will throw an exception during initialization. By
   * default all modules return false.
   */
  boolean canOverrideExistingModule();

  /** Called before {CatalystInstance#onHostDestroy} */
  void onCatalystInstanceDestroy();
}
