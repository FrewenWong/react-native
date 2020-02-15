/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react;

import android.app.Application;
import androidx.annotation.Nullable;
import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.JSIModulePackage;
import com.facebook.react.bridge.JavaScriptExecutorFactory;
import com.facebook.react.bridge.ReactMarker;
import com.facebook.react.bridge.ReactMarkerConstants;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.devsupport.RedBoxHandler;
import com.facebook.react.uimanager.UIImplementationProvider;
import java.util.List;

/**
 * Simple class that holds an instance of {@link ReactInstanceManager}. This can be used in your
 * {@link Application class} (see {@link ReactApplication}), or as a static field.
 */
public abstract class ReactNativeHost {

  private final Application mApplication;
  private @Nullable ReactInstanceManager mReactInstanceManager;

  protected ReactNativeHost(Application application) {
    mApplication = application;
  }

  /** Get the current {@link ReactInstanceManager} instance, or create one. */
  public ReactInstanceManager getReactInstanceManager() {
    if (mReactInstanceManager == null) {
      ReactMarker.logMarker(ReactMarkerConstants.GET_REACT_INSTANCE_MANAGER_START);
      mReactInstanceManager = createReactInstanceManager();
      ReactMarker.logMarker(ReactMarkerConstants.GET_REACT_INSTANCE_MANAGER_END);
    }
    return mReactInstanceManager;
  }

  /**
   * Get whether this holder contains a {@link ReactInstanceManager} instance, or not. I.e. if
   * {@link #getReactInstanceManager()} has been called at least once since this object was created
   * or {@link #clear()} was called.
   */
  public boolean hasInstance() {
    return mReactInstanceManager != null;
  }

  /**
   * Destroy the current instance and release the internal reference to it, allowing it to be GCed.
   */
  public void clear() {
    if (mReactInstanceManager != null) {
      mReactInstanceManager.destroy();
      mReactInstanceManager = null;
    }
  }
  /**
   * 实例化ReactInstanceManager
   * 
   */
  protected ReactInstanceManager createReactInstanceManager() {
    ReactMarker.logMarker(ReactMarkerConstants.BUILD_REACT_INSTANCE_MANAGER_START);
    // React实例化对象管理的。使用创建者模式。
    ReactInstanceManagerBuilder builder =
        ReactInstanceManager.builder()
            // //应用上下文
            .setApplication(mApplication)
            //JSMainModuleP相当于应用首页的js Bundle，可以传递url从服务器拉取js Bundle
            //当然这个只在dev模式下可以使用
            .setJSMainModulePath(getJSMainModuleName())
            // 是否开启dev模式
            .setUseDeveloperSupport(getUseDeveloperSupport())
            // 红盒的回调
            .setRedBoxHandler(getRedBoxHandler())
            .setJavaScriptExecutorFactory(getJavaScriptExecutorFactory())
            //自定义UI实现机制，这个我们一般用不到
            .setUIImplementationProvider(getUIImplementationProvider())
            .setJSIModulesPackage(getJSIModulePackage())
            .setInitialLifecycleState(LifecycleState.BEFORE_CREATE);
    /**
     * 添加ReactPackage
     * 这个也就是在Application的获取ReactPackage列表
     */
    for (ReactPackage reactPackage : getPackages()) {
      builder.addPackage(reactPackage);
    }
    /**
     * 获取JS Bundle的文件路径
     */
    String jsBundleFile = getJSBundleFile();
    if (jsBundleFile != null) {
      builder.setJSBundleFile(jsBundleFile);
    } else {
      builder.setBundleAssetName(Assertions.assertNotNull(getBundleAssetName()));
    }
    // 实例化ReactInstanceManager对象
    ReactInstanceManager reactInstanceManager = builder.build();
    ReactMarker.logMarker(ReactMarkerConstants.BUILD_REACT_INSTANCE_MANAGER_END);
    return reactInstanceManager;
  }

  /** Get the {@link RedBoxHandler} to send RedBox-related callbacks to. */
  protected @Nullable RedBoxHandler getRedBoxHandler() {
    return null;
  }

  /** Get the {@link JavaScriptExecutorFactory}. Override this to use a custom Executor. */
  protected @Nullable JavaScriptExecutorFactory getJavaScriptExecutorFactory() {
    return null;
  }

  protected final Application getApplication() {
    return mApplication;
  }

  /**
   * Get the {@link UIImplementationProvider} to use. Override this method if you want to use a
   * custom UI implementation.
   *
   * <p>Note: this is very advanced functionality, in 99% of cases you don't need to override this.
   */
  protected UIImplementationProvider getUIImplementationProvider() {
    return new UIImplementationProvider();
  }

  protected @Nullable JSIModulePackage getJSIModulePackage() {
    return null;
  }

  /**
   * Returns the name of the main module. Determines the URL used to fetch the JS bundle from the
   * packager server. It is only used when dev support is enabled. This is the first file to be
   * executed once the {@link ReactInstanceManager} is created. e.g. "index.android"
   */
  protected String getJSMainModuleName() {
    return "index.android";
  }

  /**
   * Returns a custom path of the bundle file. This is used in cases the bundle should be loaded
   * from a custom path. By default it is loaded from Android assets, from a path specified by
   * {@link getBundleAssetName}. e.g. "file://sdcard/myapp_cache/index.android.bundle"
   */
  protected @Nullable String getJSBundleFile() {
    return null;
  }

  /**
   * Returns the name of the bundle in assets. If this is null, and no file path is specified for
   * the bundle, the app will only work with {@code getUseDeveloperSupport} enabled and will always
   * try to load the JS bundle from the packager server. e.g. "index.android.bundle"
   */
  protected @Nullable String getBundleAssetName() {
    return "index.android.bundle";
  }

  /** Returns whether dev mode should be enabled. This enables e.g. the dev menu. */
  public abstract boolean getUseDeveloperSupport();

  /**
   * Returns a list of {@link ReactPackage} used by the app. You'll most likely want to return at
   * least the {@code MainReactPackage}. If your app uses additional views or modules besides the
   * default ones, you'll want to include more packages here.
   */
  protected abstract List<ReactPackage> getPackages();
}
