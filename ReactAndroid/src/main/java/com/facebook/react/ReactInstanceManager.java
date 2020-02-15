/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react;

import static com.facebook.infer.annotation.ThreadConfined.UI;
import static com.facebook.react.bridge.ReactMarkerConstants.ATTACH_MEASURED_ROOT_VIEWS_END;
import static com.facebook.react.bridge.ReactMarkerConstants.ATTACH_MEASURED_ROOT_VIEWS_START;
import static com.facebook.react.bridge.ReactMarkerConstants.BUILD_NATIVE_MODULE_REGISTRY_END;
import static com.facebook.react.bridge.ReactMarkerConstants.BUILD_NATIVE_MODULE_REGISTRY_START;
import static com.facebook.react.bridge.ReactMarkerConstants.CHANGE_THREAD_PRIORITY;
import static com.facebook.react.bridge.ReactMarkerConstants.CREATE_CATALYST_INSTANCE_END;
import static com.facebook.react.bridge.ReactMarkerConstants.CREATE_CATALYST_INSTANCE_START;
import static com.facebook.react.bridge.ReactMarkerConstants.CREATE_REACT_CONTEXT_START;
import static com.facebook.react.bridge.ReactMarkerConstants.CREATE_VIEW_MANAGERS_END;
import static com.facebook.react.bridge.ReactMarkerConstants.CREATE_VIEW_MANAGERS_START;
import static com.facebook.react.bridge.ReactMarkerConstants.PRE_SETUP_REACT_CONTEXT_END;
import static com.facebook.react.bridge.ReactMarkerConstants.PRE_SETUP_REACT_CONTEXT_START;
import static com.facebook.react.bridge.ReactMarkerConstants.PROCESS_PACKAGES_END;
import static com.facebook.react.bridge.ReactMarkerConstants.PROCESS_PACKAGES_START;
import static com.facebook.react.bridge.ReactMarkerConstants.REACT_CONTEXT_THREAD_END;
import static com.facebook.react.bridge.ReactMarkerConstants.REACT_CONTEXT_THREAD_START;
import static com.facebook.react.bridge.ReactMarkerConstants.SETUP_REACT_CONTEXT_END;
import static com.facebook.react.bridge.ReactMarkerConstants.SETUP_REACT_CONTEXT_START;
import static com.facebook.react.bridge.ReactMarkerConstants.VM_INIT;
import static com.facebook.react.uimanager.common.UIManagerType.FABRIC;
import static com.facebook.systrace.Systrace.TRACE_TAG_REACT_APPS;
import static com.facebook.systrace.Systrace.TRACE_TAG_REACT_JAVA_BRIDGE;
import static com.facebook.systrace.Systrace.TRACE_TAG_REACT_JS_VM_CALLS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import com.facebook.common.logging.FLog;
import com.facebook.debug.holder.PrinterHolder;
import com.facebook.debug.tags.ReactDebugOverlayTags;
import com.facebook.infer.annotation.Assertions;
import com.facebook.infer.annotation.ThreadConfined;
import com.facebook.infer.annotation.ThreadSafe;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.CatalystInstance;
import com.facebook.react.bridge.CatalystInstanceImpl;
import com.facebook.react.bridge.JSBundleLoader;
import com.facebook.react.bridge.JSIModule;
import com.facebook.react.bridge.JSIModulePackage;
import com.facebook.react.bridge.JSIModuleType;
import com.facebook.react.bridge.JavaJSExecutor;
import com.facebook.react.bridge.JavaScriptExecutor;
import com.facebook.react.bridge.JavaScriptExecutorFactory;
import com.facebook.react.bridge.NativeModuleCallExceptionHandler;
import com.facebook.react.bridge.NativeModuleRegistry;
import com.facebook.react.bridge.NotThreadSafeBridgeIdleDebugListener;
import com.facebook.react.bridge.ProxyJavaScriptExecutor;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactMarker;
import com.facebook.react.bridge.ReactMarkerConstants;
import com.facebook.react.bridge.ReactNoCrashSoftException;
import com.facebook.react.bridge.ReactSoftException;
import com.facebook.react.bridge.UIManager;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.queue.ReactQueueConfigurationSpec;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.config.ReactFeatureFlags;
import com.facebook.react.devsupport.DevSupportManagerFactory;
import com.facebook.react.devsupport.ReactInstanceManagerDevHelper;
import com.facebook.react.devsupport.RedBoxHandler;
import com.facebook.react.devsupport.interfaces.DevBundleDownloadListener;
import com.facebook.react.devsupport.interfaces.DevSupportManager;
import com.facebook.react.devsupport.interfaces.PackagerStatusCallback;
import com.facebook.react.modules.appearance.AppearanceModule;
import com.facebook.react.modules.appregistry.AppRegistry;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.ReactChoreographer;
import com.facebook.react.modules.debug.interfaces.DeveloperSettings;
import com.facebook.react.modules.fabric.ReactFabric;
import com.facebook.react.packagerconnection.RequestHandler;
import com.facebook.react.surface.ReactStage;
import com.facebook.react.turbomodule.core.interfaces.TurboModuleRegistry;
import com.facebook.react.uimanager.DisplayMetricsHolder;
import com.facebook.react.uimanager.ReactRoot;
import com.facebook.react.uimanager.UIImplementationProvider;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.ViewManager;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;
import com.facebook.soloader.SoLoader;
import com.facebook.systrace.Systrace;
import com.facebook.systrace.SystraceMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is managing instances of {@link CatalystInstance}. It exposes a way to configure
 * catalyst instance using {@link ReactPackage} and keeps track of the lifecycle of that instance.
 * It also sets up connection between the instance and developers support functionality of the
 * framework.
 *
 * <p>An instance of this manager is required to start JS application in {@link ReactRootView} (see
 * {@link ReactRootView#startReactApplication} for more info).
 *
 * <p>The lifecycle of the instance of {@link ReactInstanceManager} should be bound to the activity
 * that owns the {@link ReactRootView} that is used to render react application using this instance
 * manager (see {@link ReactRootView#startReactApplication}). It's required to pass owning
 * activity's lifecycle events to the instance manager (see {@link #onHostPause}, {@link
 * #onHostDestroy} and {@link #onHostResume}).
 *
 * <p>To instantiate an instance of this class use {@link #builder}.
 * ReactInstanceManager是ReactNative应用总的管理类，创建ReactContext、CatalystInstance等类，
 * 解析ReactPackage生成映射表，并且配合ReactRootView管理View的创建与生命周期等功能。
 */
@ThreadSafe
public class ReactInstanceManager {

  private static final String TAG = ReactInstanceManager.class.getSimpleName();
  /** Listener interface for react instance events. */
  public interface ReactInstanceEventListener {

    /**
     * Called when the react context is initialized (all modules registered). Always called on the
     * UI thread.
     */
    void onReactContextInitialized(ReactContext context);
  }

  private final Set<ReactRoot> mAttachedReactRoots =
      Collections.synchronizedSet(new HashSet<ReactRoot>());

  private volatile LifecycleState mLifecycleState;

  private @Nullable @ThreadConfined(UI) ReactContextInitParams mPendingReactContextInitParams;
  private volatile @Nullable Thread mCreateReactContextThread;
  /* accessed from any thread */
  private final JavaScriptExecutorFactory mJavaScriptExecutorFactory;

  private final @Nullable JSBundleLoader mBundleLoader;
  private final @Nullable String mJSMainModulePath; /* path to JS bundle root on packager server */
  private final List<ReactPackage> mPackages;
  private final DevSupportManager mDevSupportManager;
  private final boolean mUseDeveloperSupport;
  private final @Nullable NotThreadSafeBridgeIdleDebugListener mBridgeIdleDebugListener;
  private final Object mReactContextLock = new Object();
  private @Nullable volatile ReactContext mCurrentReactContext;
  private final Context mApplicationContext;
  private @Nullable @ThreadConfined(UI) DefaultHardwareBackBtnHandler mDefaultBackButtonImpl;
  private @Nullable Activity mCurrentActivity;
  private final Collection<ReactInstanceEventListener> mReactInstanceEventListeners =
      Collections.synchronizedList(new ArrayList<ReactInstanceEventListener>());
  // Identifies whether the instance manager is or soon will be initialized (on background thread)
  private volatile boolean mHasStartedCreatingInitialContext = false;
  // Identifies whether the instance manager destroy function is in process,
  // while true any spawned create thread should wait for proper clean up before initializing
  private volatile Boolean mHasStartedDestroying = false;
  private final MemoryPressureRouter mMemoryPressureRouter;
  private final @Nullable NativeModuleCallExceptionHandler mNativeModuleCallExceptionHandler;
  private final @Nullable JSIModulePackage mJSIModulePackage;
  private List<ViewManager> mViewManagers;

  private class ReactContextInitParams {
    private final JavaScriptExecutorFactory mJsExecutorFactory;
    private final JSBundleLoader mJsBundleLoader;

    public ReactContextInitParams(
        JavaScriptExecutorFactory jsExecutorFactory, JSBundleLoader jsBundleLoader) {
      mJsExecutorFactory = Assertions.assertNotNull(jsExecutorFactory);
      mJsBundleLoader = Assertions.assertNotNull(jsBundleLoader);
    }

    public JavaScriptExecutorFactory getJsExecutorFactory() {
      return mJsExecutorFactory;
    }

    public JSBundleLoader getJsBundleLoader() {
      return mJsBundleLoader;
    }
  }

  /** Creates a builder that is capable of creating an instance of {@link ReactInstanceManager}. */
  public static ReactInstanceManagerBuilder builder() {
    return new ReactInstanceManagerBuilder();
  }

  /* package */ ReactInstanceManager(
      Context applicationContext,
      @Nullable Activity currentActivity,
      @Nullable DefaultHardwareBackBtnHandler defaultHardwareBackBtnHandler,
      JavaScriptExecutorFactory javaScriptExecutorFactory,
      @Nullable JSBundleLoader bundleLoader,
      @Nullable String jsMainModulePath,
      List<ReactPackage> packages,
      boolean useDeveloperSupport,
      @Nullable NotThreadSafeBridgeIdleDebugListener bridgeIdleDebugListener,
      LifecycleState initialLifecycleState,
      @Nullable UIImplementationProvider mUIImplementationProvider,
      NativeModuleCallExceptionHandler nativeModuleCallExceptionHandler,
      @Nullable RedBoxHandler redBoxHandler,
      boolean lazyViewManagersEnabled,
      @Nullable DevBundleDownloadListener devBundleDownloadListener,
      int minNumShakes,
      int minTimeLeftInFrameForNonBatchedOperationMs,
      @Nullable JSIModulePackage jsiModulePackage,
      @Nullable Map<String, RequestHandler> customPackagerCommandHandlers) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.ctor()");
    initializeSoLoaderIfNecessary(applicationContext);

    DisplayMetricsHolder.initDisplayMetricsIfNotInitialized(applicationContext);
    // 就是传入的MainApplication
    mApplicationContext = applicationContext;
    mCurrentActivity = currentActivity;
    mDefaultBackButtonImpl = defaultHardwareBackBtnHandler;
    mJavaScriptExecutorFactory = javaScriptExecutorFactory;
    // 传入的JS的JSBundleLoader
    mBundleLoader = bundleLoader;
    mJSMainModulePath = jsMainModulePath;
    mPackages = new ArrayList<>();
    mUseDeveloperSupport = useDeveloperSupport;
    Systrace.beginSection(
        Systrace.TRACE_TAG_REACT_JAVA_BRIDGE, "ReactInstanceManager.initDevSupportManager");
    mDevSupportManager =
        DevSupportManagerFactory.create(
            applicationContext,
            createDevHelperInterface(),
            mJSMainModulePath,
            useDeveloperSupport,
            redBoxHandler,
            devBundleDownloadListener,
            minNumShakes,
            customPackagerCommandHandlers);
    Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
    mBridgeIdleDebugListener = bridgeIdleDebugListener;
    mLifecycleState = initialLifecycleState;
    mMemoryPressureRouter = new MemoryPressureRouter(applicationContext);
    mNativeModuleCallExceptionHandler = nativeModuleCallExceptionHandler;
    synchronized (mPackages) {
      PrinterHolder.getPrinter()
          .logMessage(ReactDebugOverlayTags.RN_CORE, "RNCore: Use Split Packages");
      mPackages.add(
          new CoreModulesPackage(
              this,
              new DefaultHardwareBackBtnHandler() {
                @Override
                public void invokeDefaultOnBackPressed() {
                  ReactInstanceManager.this.invokeDefaultOnBackPressed();
                }
              },
              mUIImplementationProvider,
              lazyViewManagersEnabled,
              minTimeLeftInFrameForNonBatchedOperationMs));
      if (mUseDeveloperSupport) {
        mPackages.add(new DebugCorePackage());
      }
      mPackages.addAll(packages);
    }
    mJSIModulePackage = jsiModulePackage;

    // Instantiate ReactChoreographer in UI thread.
    ReactChoreographer.initialize();
    if (mUseDeveloperSupport) {
      mDevSupportManager.startInspector();
    }
  }

  private ReactInstanceManagerDevHelper createDevHelperInterface() {
    return new ReactInstanceManagerDevHelper() {
      @Override
      public void onReloadWithJSDebugger(JavaJSExecutor.Factory jsExecutorFactory) {
        ReactInstanceManager.this.onReloadWithJSDebugger(jsExecutorFactory);
      }

      @Override
      public void onJSBundleLoadedFromServer() {
        ReactInstanceManager.this.onJSBundleLoadedFromServer();
      }

      @Override
      public void toggleElementInspector() {
        ReactInstanceManager.this.toggleElementInspector();
      }

      @Override
      public @Nullable Activity getCurrentActivity() {
        return ReactInstanceManager.this.mCurrentActivity;
      }

      @Override
      public JavaScriptExecutorFactory getJavaScriptExecutorFactory() {
        return ReactInstanceManager.this.getJSExecutorFactory();
      }

      @Override
      public @Nullable View createRootView(String appKey) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity != null) {
          ReactRootView rootView = new ReactRootView(currentActivity);

          rootView.startReactApplication(ReactInstanceManager.this, appKey, null);

          return rootView;
        }

        return null;
      }

      @Override
      public void destroyRootView(View rootView) {
        if (rootView instanceof ReactRootView) {
          ((ReactRootView) rootView).unmountReactApplication();
        }
      }
    };
  }

  private JavaScriptExecutorFactory getJSExecutorFactory() {
    return mJavaScriptExecutorFactory;
  }

  public DevSupportManager getDevSupportManager() {
    return mDevSupportManager;
  }

  public MemoryPressureRouter getMemoryPressureRouter() {
    return mMemoryPressureRouter;
  }

  public List<ReactPackage> getPackages() {
    return new ArrayList<>(mPackages);
  }

  static void initializeSoLoaderIfNecessary(Context applicationContext) {
    // Call SoLoader.initialize here, this is required for apps that does not use exopackage and
    // does not use SoLoader for loading other native code except from the one used by React Native
    // This way we don't need to require others to have additional initialization code and to
    // subclass android.app.Application.

    // Method SoLoader.init is idempotent, so if you wish to use native exopackage, just call
    // SoLoader.init with appropriate args before initializing ReactInstanceManager
    SoLoader.init(applicationContext, /* native exopackage */ false);
  }

  /**
   * Trigger react context initialization asynchronously in a background async task. This enables
   * applications to pre-load the application JS, and execute global code before {@link
   * ReactRootView} is available and measured.
   *
   * <p>Called from UI thread.
   * loadApp的方法中调用startReactApplication的对象
   */
  @ThreadConfined(UI)
  public void createReactContextInBackground() {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.createReactContextInBackground()");
    // 判断是否是在UI线程的进行道德逻辑调用
    UiThreadUtil
        .assertOnUiThread(); // Assert before setting mHasStartedCreatingInitialContext = true
    // 判断是否已经开始初始化Init的ReactContext    
    if (!mHasStartedCreatingInitialContext) {
      mHasStartedCreatingInitialContext = true;
      // 进一步创建ReactContext
      recreateReactContextInBackgroundInner();
    }
  }

  /**
   * Recreate the react application and context. This should be called if configuration has changed
   * or the developer has requested the app to be reloaded. It should only be called after an
   * initial call to createReactContextInBackground.
   *
   * <p>Called from UI thread.
   */
  @ThreadConfined(UI)
  public void recreateReactContextInBackground() {
    Assertions.assertCondition(
        mHasStartedCreatingInitialContext,
        "recreateReactContextInBackground should only be called after the initial "
            + "createReactContextInBackground call.");
    recreateReactContextInBackgroundInner();
  }

  /**
   * ReactInstanceManager的内部的上下文的创建
   */
  @ThreadConfined(UI)
  private void recreateReactContextInBackgroundInner() {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.recreateReactContextInBackgroundInner()");
    PrinterHolder.getPrinter()
        .logMessage(ReactDebugOverlayTags.RN_CORE, "RNCore: recreateReactContextInBackground");
    UiThreadUtil.assertOnUiThread();
    // 开发模式，实现在线更新Bundle，晃动弹出调试菜单等功能，这一部分属于调试功能流程。
    if (mUseDeveloperSupport && mJSMainModulePath != null) {
      final DeveloperSettings devSettings = mDevSupportManager.getDevSettings();

      if (!Systrace.isTracing(TRACE_TAG_REACT_APPS | TRACE_TAG_REACT_JS_VM_CALLS)) {
        if (mBundleLoader == null) {
          mDevSupportManager.handleReloadJS();
        } else {
          mDevSupportManager.isPackagerRunning(
              new PackagerStatusCallback() {
                @Override
                public void onPackagerStatusFetched(final boolean packagerIsRunning) {
                  UiThreadUtil.runOnUiThread(
                      new Runnable() {
                        @Override
                        public void run() {
                          if (packagerIsRunning) {
                            mDevSupportManager.handleReloadJS();
                          } else if (mDevSupportManager.hasUpToDateJSBundleInCache()
                              && !devSettings.isRemoteJSDebugEnabled()) {
                            //判断是否处于开发模式，如果处于开发模式，则从Dev Server中获取JSBundle，如果不是则从文件中获取。    
                            // If there is a up-to-date bundle downloaded from server,
                            // with remote JS debugging disabled, always use that.
                            onJSBundleLoadedFromServer();
                          } else {
                            // If dev server is down, disable the remote JS debugging.
                            devSettings.setRemoteJSDebugEnabled(false);
                            recreateReactContextInBackgroundFromBundleLoader();
                          }
                        }
                      });
                }
              });
        }
        return;
      }
    }
    // FromBundleLoader的来实例化RN的上下文
    recreateReactContextInBackgroundFromBundleLoader();
  }

  @ThreadConfined(UI)
  private void recreateReactContextInBackgroundFromBundleLoader() {
    Log.d(
        ReactConstants.TAG,
        "ReactInstanceManager.recreateReactContextInBackgroundFromBundleLoader()");
    PrinterHolder.getPrinter()
        .logMessage(ReactDebugOverlayTags.RN_CORE, "RNCore: load from BundleLoader");
    recreateReactContextInBackground(mJavaScriptExecutorFactory, mBundleLoader);
  }

  /**
   * @return whether createReactContextInBackground has been called. Will return false after
   *     onDestroy until a new initial context has been created.
   */
  public boolean hasStartedCreatingInitialContext() {
    return mHasStartedCreatingInitialContext;
  }

  /**
   * This method will give JS the opportunity to consume the back button event. If JS does not
   * consume the event, mDefaultBackButtonImpl will be invoked at the end of the round trip to JS.
   */
  public void onBackPressed() {
    UiThreadUtil.assertOnUiThread();
    ReactContext reactContext = mCurrentReactContext;
    if (reactContext == null) {
      // Invoke without round trip to JS.
      FLog.w(ReactConstants.TAG, "Instance detached from instance manager");
      invokeDefaultOnBackPressed();
    } else {
      DeviceEventManagerModule deviceEventManagerModule =
          reactContext.getNativeModule(DeviceEventManagerModule.class);
      deviceEventManagerModule.emitHardwareBackPressed();
    }
  }

  private void invokeDefaultOnBackPressed() {
    UiThreadUtil.assertOnUiThread();
    if (mDefaultBackButtonImpl != null) {
      mDefaultBackButtonImpl.invokeDefaultOnBackPressed();
    }
  }

  /** This method will give JS the opportunity to receive intents via Linking. */
  @ThreadConfined(UI)
  public void onNewIntent(Intent intent) {
    UiThreadUtil.assertOnUiThread();
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext == null) {
      FLog.w(ReactConstants.TAG, "Instance detached from instance manager");
    } else {
      String action = intent.getAction();
      Uri uri = intent.getData();

      if (uri != null
          && (Intent.ACTION_VIEW.equals(action)
              || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action))) {
        DeviceEventManagerModule deviceEventManagerModule =
            currentContext.getNativeModule(DeviceEventManagerModule.class);
        deviceEventManagerModule.emitNewIntentReceived(uri);
      }
      currentContext.onNewIntent(mCurrentActivity, intent);
    }
  }

  private void toggleElementInspector() {
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null && currentContext.hasActiveCatalystInstance()) {
      currentContext
          .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
          .emit("toggleElementInspector", null);
    } else {
      ReactSoftException.logSoftException(
          TAG,
          new ReactNoCrashSoftException(
              "Cannot toggleElementInspector, CatalystInstance not available"));
    }
  }

  /**
   * Call this from {@link Activity#onPause()}. This notifies any listening modules so they can do
   * any necessary cleanup.
   *
   * @deprecated Use {@link #onHostPause(Activity)} instead.
   */
  @ThreadConfined(UI)
  public void onHostPause() {
    UiThreadUtil.assertOnUiThread();

    mDefaultBackButtonImpl = null;
    if (mUseDeveloperSupport) {
      mDevSupportManager.setDevSupportEnabled(false);
    }

    moveToBeforeResumeLifecycleState();
  }

  /**
   * Call this from {@link Activity#onPause()}. This notifies any listening modules so they can do
   * any necessary cleanup. The passed Activity is the current Activity being paused. This will
   * always be the foreground activity that would be returned by {@link
   * ReactContext#getCurrentActivity()}.
   *
   * @param activity the activity being paused
   */
  @ThreadConfined(UI)
  public void onHostPause(Activity activity) {
    Assertions.assertNotNull(mCurrentActivity);
    Assertions.assertCondition(
        activity == mCurrentActivity,
        "Pausing an activity that is not the current activity, this is incorrect! "
            + "Current activity: "
            + mCurrentActivity.getClass().getSimpleName()
            + " "
            + "Paused activity: "
            + activity.getClass().getSimpleName());
    onHostPause();
  }

  /**
   * Use this method when the activity resumes to enable invoking the back button directly from JS.
   *
   * <p>This method retains an instance to provided mDefaultBackButtonImpl. Thus it's important to
   * pass from the activity instance that owns this particular instance of {@link
   * ReactInstanceManager}, so that once this instance receive {@link #onHostDestroy} event it will
   * clear the reference to that defaultBackButtonImpl.
   *
   * @param defaultBackButtonImpl a {@link DefaultHardwareBackBtnHandler} from an Activity that owns
   *     this instance of {@link ReactInstanceManager}.
   */
  @ThreadConfined(UI)
  public void onHostResume(Activity activity, DefaultHardwareBackBtnHandler defaultBackButtonImpl) {
    UiThreadUtil.assertOnUiThread();

    mDefaultBackButtonImpl = defaultBackButtonImpl;
    onHostResume(activity);
  }

  /** Use this method when the activity resumes. */
  @ThreadConfined(UI)
  public void onHostResume(Activity activity) {
    UiThreadUtil.assertOnUiThread();

    mCurrentActivity = activity;

    if (mUseDeveloperSupport) {
      // Resume can be called from one of two different states:
      // a) when activity was paused
      // b) when activity has just been created
      // In case of (a) the activity is attached to window and it is ok to add new views to it or
      // open dialogs. In case of (b) there is often a slight delay before such a thing happens.
      // As dev support manager can add views or open dialogs immediately after it gets enabled
      // (e.g. in the case when JS bundle is being fetched in background) we only want to enable
      // it once we know for sure the current activity is attached.

      // We check if activity is attached to window by checking if decor view is attached
      final View decorView = mCurrentActivity.getWindow().getDecorView();
      if (!ViewCompat.isAttachedToWindow(decorView)) {
        decorView.addOnAttachStateChangeListener(
            new View.OnAttachStateChangeListener() {
              @Override
              public void onViewAttachedToWindow(View v) {
                // we can drop listener now that we know the view is attached
                decorView.removeOnAttachStateChangeListener(this);
                mDevSupportManager.setDevSupportEnabled(true);
              }

              @Override
              public void onViewDetachedFromWindow(View v) {
                // do nothing
              }
            });
      } else {
        // activity is attached to window, we can enable dev support immediately
        mDevSupportManager.setDevSupportEnabled(true);
      }
    }

    moveToResumedLifecycleState(false);
  }

  /**
   * Call this from {@link Activity#onDestroy()}. This notifies any listening modules so they can do
   * any necessary cleanup.
   *
   * @deprecated use {@link #onHostDestroy(Activity)} instead
   */
  @ThreadConfined(UI)
  public void onHostDestroy() {
    UiThreadUtil.assertOnUiThread();

    if (mUseDeveloperSupport) {
      mDevSupportManager.setDevSupportEnabled(false);
    }

    moveToBeforeCreateLifecycleState();
    mCurrentActivity = null;
  }

  /**
   * Call this from {@link Activity#onDestroy()}. This notifies any listening modules so they can do
   * any necessary cleanup. If the activity being destroyed is not the current activity, no modules
   * are notified.
   *
   * @param activity the activity being destroyed
   */
  @ThreadConfined(UI)
  public void onHostDestroy(Activity activity) {
    if (activity == mCurrentActivity) {
      onHostDestroy();
    }
  }

  /** Destroy this React instance and the attached JS context. */
  @ThreadConfined(UI)
  public void destroy() {
    UiThreadUtil.assertOnUiThread();
    PrinterHolder.getPrinter().logMessage(ReactDebugOverlayTags.RN_CORE, "RNCore: Destroy");

    mHasStartedDestroying = true;

    if (mUseDeveloperSupport) {
      mDevSupportManager.setDevSupportEnabled(false);
      mDevSupportManager.stopInspector();
    }

    moveToBeforeCreateLifecycleState();

    if (mCreateReactContextThread != null) {
      mCreateReactContextThread = null;
    }

    mMemoryPressureRouter.destroy(mApplicationContext);

    synchronized (mReactContextLock) {
      if (mCurrentReactContext != null) {
        mCurrentReactContext.destroy();
        mCurrentReactContext = null;
      }
    }
    mHasStartedCreatingInitialContext = false;
    mCurrentActivity = null;

    ResourceDrawableIdHelper.getInstance().clear();
    mHasStartedDestroying = false;
    synchronized (mHasStartedDestroying) {
      mHasStartedDestroying.notifyAll();
    }
  }

  private synchronized void moveToResumedLifecycleState(boolean force) {
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null) {
      // we currently don't have an onCreate callback so we call onResume for both transitions
      if (force
          || mLifecycleState == LifecycleState.BEFORE_RESUME
          || mLifecycleState == LifecycleState.BEFORE_CREATE) {
        currentContext.onHostResume(mCurrentActivity);
      }
    }
    mLifecycleState = LifecycleState.RESUMED;
  }

  private synchronized void moveToBeforeResumeLifecycleState() {
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null) {
      if (mLifecycleState == LifecycleState.BEFORE_CREATE) {
        currentContext.onHostResume(mCurrentActivity);
        currentContext.onHostPause();
      } else if (mLifecycleState == LifecycleState.RESUMED) {
        currentContext.onHostPause();
      }
    }
    mLifecycleState = LifecycleState.BEFORE_RESUME;
  }

  private synchronized void moveToBeforeCreateLifecycleState() {
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null) {
      if (mLifecycleState == LifecycleState.RESUMED) {
        currentContext.onHostPause();
        mLifecycleState = LifecycleState.BEFORE_RESUME;
      }
      if (mLifecycleState == LifecycleState.BEFORE_RESUME) {
        currentContext.onHostDestroy();
      }
    }
    mLifecycleState = LifecycleState.BEFORE_CREATE;
  }

  private synchronized void moveReactContextToCurrentLifecycleState() {
    if (mLifecycleState == LifecycleState.RESUMED) {
      moveToResumedLifecycleState(true);
    }
  }

  @ThreadConfined(UI)
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null) {
      currentContext.onActivityResult(activity, requestCode, resultCode, data);
    }
  }

  @ThreadConfined(UI)
  public void onWindowFocusChange(boolean hasFocus) {
    UiThreadUtil.assertOnUiThread();
    ReactContext currentContext = getCurrentReactContext();
    if (currentContext != null) {
      currentContext.onWindowFocusChange(hasFocus);
    }
  }

  /** Call this from {@link Activity#onConfigurationChanged()}. */
  @ThreadConfined(UI)
  public void onConfigurationChanged(Context updatedContext, @Nullable Configuration newConfig) {
    UiThreadUtil.assertOnUiThread();

    ReactContext currentReactContext = getCurrentReactContext();
    if (currentReactContext != null) {
      currentReactContext
          .getNativeModule(AppearanceModule.class)
          .onConfigurationChanged(updatedContext);
    }
  }

  @ThreadConfined(UI)
  public void showDevOptionsDialog() {
    UiThreadUtil.assertOnUiThread();
    mDevSupportManager.showDevOptionsDialog();
  }

  private void clearReactRoot(ReactRoot reactRoot) {
    reactRoot.getRootViewGroup().removeAllViews();
    reactRoot.getRootViewGroup().setId(View.NO_ID);
  }

  /**
   * Attach given {@param reactRoot} to a catalyst instance manager and start JS application using
   * JS module provided by {@link ReactRootView#getJSModuleName}. If the react context is currently
   * being (re)-created, or if react context has not been created yet, the JS application associated
   * with the provided reactRoot reactRoot will be started asynchronously, i.e this method won't
   * block. This reactRoot will then be tracked by this manager and in case of catalyst instance
   * restart it will be re-attached.
   */
  @ThreadConfined(UI)
  public void attachRootView(ReactRoot reactRoot) {
    UiThreadUtil.assertOnUiThread();
    mAttachedReactRoots.add(reactRoot);

    // Reset reactRoot content as it's going to be populated by the application content from JS.
    clearReactRoot(reactRoot);

    // If react context is being created in the background, JS application will be started
    // automatically when creation completes, as reactRoot reactRoot is part of the attached
    // reactRoot reactRoot list.
    ReactContext currentContext = getCurrentReactContext();
    if (mCreateReactContextThread == null && currentContext != null) {
      attachRootViewToInstance(reactRoot);
    }
  }

  /**
   * Detach given {@param reactRoot} from current catalyst instance. It's safe to call this method
   * multiple times on the same {@param reactRoot} - in that case view will be detached with the
   * first call.
   */
  @ThreadConfined(UI)
  public void detachRootView(ReactRoot reactRoot) {
    UiThreadUtil.assertOnUiThread();
    synchronized (mAttachedReactRoots) {
      if (mAttachedReactRoots.contains(reactRoot)) {
        ReactContext currentContext = getCurrentReactContext();
        mAttachedReactRoots.remove(reactRoot);
        if (currentContext != null && currentContext.hasActiveCatalystInstance()) {
          detachViewFromInstance(reactRoot, currentContext.getCatalystInstance());
        }
      }
    }
  }

  /** Uses configured {@link ReactPackage} instances to create all view managers. */
  public List<ViewManager> getOrCreateViewManagers(
      ReactApplicationContext catalystApplicationContext) {
    ReactMarker.logMarker(CREATE_VIEW_MANAGERS_START);
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "createAllViewManagers");
    try {
      if (mViewManagers == null) {
        synchronized (mPackages) {
          if (mViewManagers == null) {
            mViewManagers = new ArrayList<>();
            for (ReactPackage reactPackage : mPackages) {
              mViewManagers.addAll(reactPackage.createViewManagers(catalystApplicationContext));
            }
            return mViewManagers;
          }
        }
      }
      return mViewManagers;
    } finally {
      Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
      ReactMarker.logMarker(CREATE_VIEW_MANAGERS_END);
    }
  }

  public @Nullable ViewManager createViewManager(String viewManagerName) {
    ReactApplicationContext context;
    synchronized (mReactContextLock) {
      context = (ReactApplicationContext) getCurrentReactContext();
      if (context == null || !context.hasActiveCatalystInstance()) {
        return null;
      }
    }

    synchronized (mPackages) {
      for (ReactPackage reactPackage : mPackages) {
        if (reactPackage instanceof ViewManagerOnDemandReactPackage) {
          ViewManager viewManager =
              ((ViewManagerOnDemandReactPackage) reactPackage)
                  .createViewManager(context, viewManagerName);
          if (viewManager != null) {
            return viewManager;
          }
        }
      }
    }
    return null;
  }

  public @Nullable List<String> getViewManagerNames() {
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "ReactInstanceManager.getViewManagerNames");
    ReactApplicationContext context;
    synchronized (mReactContextLock) {
      context = (ReactApplicationContext) getCurrentReactContext();
      if (context == null || !context.hasActiveCatalystInstance()) {
        return null;
      }
    }

    synchronized (mPackages) {
      Set<String> uniqueNames = new HashSet<>();
      for (ReactPackage reactPackage : mPackages) {
        SystraceMessage.beginSection(
                TRACE_TAG_REACT_JAVA_BRIDGE, "ReactInstanceManager.getViewManagerName")
            .arg("Package", reactPackage.getClass().getSimpleName())
            .flush();
        if (reactPackage instanceof ViewManagerOnDemandReactPackage) {
          List<String> names =
              ((ViewManagerOnDemandReactPackage) reactPackage).getViewManagerNames(context);
          if (names != null) {
            uniqueNames.addAll(names);
          }
        }
        SystraceMessage.endSection(TRACE_TAG_REACT_JAVA_BRIDGE).flush();
      }
      Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
      return new ArrayList<>(uniqueNames);
    }
  }

  /** Add a listener to be notified of react instance events. */
  public void addReactInstanceEventListener(ReactInstanceEventListener listener) {
    mReactInstanceEventListeners.add(listener);
  }

  /** Remove a listener previously added with {@link #addReactInstanceEventListener}. */
  public void removeReactInstanceEventListener(ReactInstanceEventListener listener) {
    mReactInstanceEventListeners.remove(listener);
  }

  @VisibleForTesting
  public @Nullable ReactContext getCurrentReactContext() {
    synchronized (mReactContextLock) {
      return mCurrentReactContext;
    }
  }

  public LifecycleState getLifecycleState() {
    return mLifecycleState;
  }

  public String getJsExecutorName() {
    return mJavaScriptExecutorFactory.toString();
  }

  @ThreadConfined(UI)
  private void onReloadWithJSDebugger(JavaJSExecutor.Factory jsExecutorFactory) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.onReloadWithJSDebugger()");
    recreateReactContextInBackground(
        new ProxyJavaScriptExecutor.Factory(jsExecutorFactory),
        JSBundleLoader.createRemoteDebuggerBundleLoader(
            mDevSupportManager.getJSBundleURLForRemoteDebugging(),
            mDevSupportManager.getSourceUrl()));
  }

  @ThreadConfined(UI)
  private void onJSBundleLoadedFromServer() {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.onJSBundleLoadedFromServer()");

    JSBundleLoader bundleLoader =
        JSBundleLoader.createCachedBundleFromNetworkLoader(
            mDevSupportManager.getSourceUrl(), mDevSupportManager.getDownloadedJSBundleFile());

    recreateReactContextInBackground(mJavaScriptExecutorFactory, bundleLoader);
  }

  @ThreadConfined(UI)
  private void recreateReactContextInBackground(
      JavaScriptExecutorFactory jsExecutorFactory, JSBundleLoader jsBundleLoader) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.recreateReactContextInBackground()");
    UiThreadUtil.assertOnUiThread();

    final ReactContextInitParams initParams =
        new ReactContextInitParams(jsExecutorFactory, jsBundleLoader);
    if (mCreateReactContextThread == null) {
      //在子线程初始化ReactNative的上下文对象
      runCreateReactContextOnNewThread(initParams);
    } else {
      //创建ReactContext的后台任务已经开启，缓存initParams在队列中等待重新创建ReactContext
      mPendingReactContextInitParams = initParams;
    }
  }

  @ThreadConfined(UI)
  private void runCreateReactContextOnNewThread(final ReactContextInitParams initParams) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.runCreateReactContextOnNewThread()");
    UiThreadUtil.assertOnUiThread();
    synchronized (mAttachedReactRoots) {
      synchronized (mReactContextLock) {
        if (mCurrentReactContext != null) {
          tearDownReactContext(mCurrentReactContext);
          mCurrentReactContext = null;
        }
      }
    }
    /**
     * 实例化一个子线程。来进行创建ReactNative的Context
     * 所以下面我们来分析一下这个线程的run方法
     */
    mCreateReactContextThread =
        new Thread(
            null,
            new Runnable() {
              @Override
              public void run() {

                ReactMarker.logMarker(REACT_CONTEXT_THREAD_END);

                synchronized (ReactInstanceManager.this.mHasStartedDestroying) {
                  while (ReactInstanceManager.this.mHasStartedDestroying) {
                    try {
                      ReactInstanceManager.this.mHasStartedDestroying.wait();
                    } catch (InterruptedException e) {
                      continue;
                    }
                  }
                }
                // As destroy() may have run and set this to false, ensure that it is true before we
                // create
                mHasStartedCreatingInitialContext = true;

                try {
                  // 设置线程的优先级
                  Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
                  ReactMarker.logMarker(VM_INIT);
                  // 创建ReactApplicationContext的对象。然后同时开始加载JSBundle文件
                  // 我们这个方法传入的两个参数：
                  // initParams.getJsExecutorFactory().create()生成的JsExecutor
                  // 还有JsBundleLoader
                  final ReactApplicationContext reactApplicationContext =
                      createReactContext(
                          initParams.getJsExecutorFactory().create(),
                          initParams.getJsBundleLoader());

                  mCreateReactContextThread = null;
                  ReactMarker.logMarker(PRE_SETUP_REACT_CONTEXT_START);
                  final Runnable maybeRecreateReactContextRunnable =
                      new Runnable() {
                        @Override
                        public void run() {
                          if (mPendingReactContextInitParams != null) {
                            runCreateReactContextOnNewThread(mPendingReactContextInitParams);
                            mPendingReactContextInitParams = null;
                          }
                        }
                      };
                  // 设置setupReactContext    
                  Runnable setupReactContextRunnable =
                      new Runnable() {
                        @Override
                        public void run() {
                          try {
                            setupReactContext(reactApplicationContext);
                          } catch (Exception e) {
                            mDevSupportManager.handleException(e);
                          }
                        }
                      };

                  reactApplicationContext.runOnNativeModulesQueueThread(setupReactContextRunnable);
                  UiThreadUtil.runOnUiThread(maybeRecreateReactContextRunnable);
                } catch (Exception e) {
                  mDevSupportManager.handleException(e);
                }
              }
            },
            "create_react_context");
    ReactMarker.logMarker(REACT_CONTEXT_THREAD_START);
    mCreateReactContextThread.start();
  }
  /**
   * 
   */
  private void setupReactContext(final ReactApplicationContext reactContext) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.setupReactContext()");
    ReactMarker.logMarker(PRE_SETUP_REACT_CONTEXT_END);
    ReactMarker.logMarker(SETUP_REACT_CONTEXT_START);
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "setupReactContext");
    synchronized (mAttachedReactRoots) {
      synchronized (mReactContextLock) {
        mCurrentReactContext = Assertions.assertNotNull(reactContext);
      }

      CatalystInstance catalystInstance =
          Assertions.assertNotNull(reactContext.getCatalystInstance());
      // 执行Native Java module的初始化
      catalystInstance.initialize();
      // 重置DevSupportManager的ReactContext
      mDevSupportManager.onNewReactContextCreated(reactContext);
      // 内存状态回调设置
      mMemoryPressureRouter.addMemoryPressureListener(catalystInstance);
      //复位生命周期
      moveReactContextToCurrentLifecycleState();

      ReactMarker.logMarker(ATTACH_MEASURED_ROOT_VIEWS_START);
      // 遍历所有的ViewRoot。调用attachRootViewToInstance
      for (ReactRoot reactRoot : mAttachedReactRoots) {
        attachRootViewToInstance(reactRoot);
      }
      ReactMarker.logMarker(ATTACH_MEASURED_ROOT_VIEWS_END);
    }

    // There is a race condition here - `finalListeners` can contain null entries
    // See usage below for more details.
    ReactInstanceEventListener[] listeners =
        new ReactInstanceEventListener[mReactInstanceEventListeners.size()];
    final ReactInstanceEventListener[] finalListeners =
        mReactInstanceEventListeners.toArray(listeners);

    UiThreadUtil.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            for (ReactInstanceEventListener listener : finalListeners) {
              // Sometimes this listener is null - probably due to race
              // condition between allocating listeners with a certain
              // size, and getting a `final` version of the array on
              // the following line.
              if (listener != null) {
                listener.onReactContextInitialized(reactContext);
              }
            }
          }
        });
    Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
    ReactMarker.logMarker(SETUP_REACT_CONTEXT_END);
    reactContext.runOnJSQueueThread(
        new Runnable() {
          @Override
          public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
            ReactMarker.logMarker(CHANGE_THREAD_PRIORITY, "js_default");
          }
        });
    reactContext.runOnNativeModulesQueueThread(
        new Runnable() {
          @Override
          public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
          }
        });
  }

  private void attachRootViewToInstance(final ReactRoot reactRoot) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.attachRootViewToInstance()");
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "attachRootViewToInstance");

    @Nullable
    UIManager uiManager =
        UIManagerHelper.getUIManager(mCurrentReactContext, reactRoot.getUIManagerType());

    // If we can't get a UIManager something has probably gone horribly wrong
    if (uiManager == null) {
      throw new IllegalStateException(
          "Unable to attache a rootView to ReactInstance when UIManager is not properly initialized.");
    }

    @Nullable Bundle initialProperties = reactRoot.getAppProperties();

    final int rootTag =
        uiManager.addRootView(
            reactRoot.getRootViewGroup(),
            initialProperties == null
                ? new WritableNativeMap()
                : Arguments.fromBundle(initialProperties),
            reactRoot.getInitialUITemplate());
    reactRoot.setRootViewTag(rootTag);
    if (reactRoot.getUIManagerType() == FABRIC) {
      // Fabric requires to call updateRootLayoutSpecs before starting JS Application,
      // this ensures the root will hace the correct pointScaleFactor.
      uiManager.updateRootLayoutSpecs(
          rootTag, reactRoot.getWidthMeasureSpec(), reactRoot.getHeightMeasureSpec());
      reactRoot.setShouldLogContentAppeared(true);
    } else {
      // 调用ReactRoot的runApplication方法。我们来看一下这个方法.这个方法其实就是ReactRootView的runApplication
      // 我们可以查看ReactRootView的runApplication
      reactRoot.runApplication();
    }
    Systrace.beginAsyncSection(
        TRACE_TAG_REACT_JAVA_BRIDGE, "pre_rootView.onAttachedToReactInstance", rootTag);
    UiThreadUtil.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            Systrace.endAsyncSection(
                TRACE_TAG_REACT_JAVA_BRIDGE, "pre_rootView.onAttachedToReactInstance", rootTag);
            reactRoot.onStage(ReactStage.ON_ATTACH_TO_INSTANCE);
          }
        });
    Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
  }

  private void detachViewFromInstance(ReactRoot reactRoot, CatalystInstance catalystInstance) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.detachViewFromInstance()");
    UiThreadUtil.assertOnUiThread();
    if (reactRoot.getUIManagerType() == FABRIC) {
      catalystInstance
          .getJSModule(ReactFabric.class)
          .unmountComponentAtNode(reactRoot.getRootViewTag());
    } else {
      catalystInstance
          .getJSModule(AppRegistry.class)
          .unmountApplicationComponentAtRootTag(reactRoot.getRootViewTag());
    }
  }

  private void tearDownReactContext(ReactContext reactContext) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.tearDownReactContext()");
    UiThreadUtil.assertOnUiThread();
    if (mLifecycleState == LifecycleState.RESUMED) {
      reactContext.onHostPause();
    }

    synchronized (mAttachedReactRoots) {
      for (ReactRoot reactRoot : mAttachedReactRoots) {
        clearReactRoot(reactRoot);
      }
    }

    // Remove memory pressure listener before tearing down react context
    // We cannot access the CatalystInstance after destroying the ReactContext.
    mMemoryPressureRouter.removeMemoryPressureListener(reactContext.getCatalystInstance());

    reactContext.destroy();
    mDevSupportManager.onReactInstanceDestroyed(reactContext);
  }

  /**
   * 创建ReactApplicationContext对象的对象的方法
   * 1 创建JavaModule注册表与JavaScriptModule注册表，这两张表最后都交由CatalystInstance管理。
   * 3 处理ReactPackage，将JavaModule与JavaScriptModule放进各自对应的注册表里。
   * 3 通过上面jsExecutor、nativeModuleRegistry、jsModulesRegistry、jsBundleLoader、exceptionHandler等参数创建CatalystInstance实例。
   * 4 关联ReactContext与CatalystInstance，并将JS Bundle加载进来，这个线程执行完毕结束以后调用JS入口渲染页面。
   */
  /** @return instance of {@link ReactContext} configured a {@link CatalystInstance} set */
  private ReactApplicationContext createReactContext(
      JavaScriptExecutor jsExecutor, JSBundleLoader jsBundleLoader) {
    Log.d(ReactConstants.TAG, "ReactInstanceManager.createReactContext()");
    ReactMarker.logMarker(CREATE_REACT_CONTEXT_START, jsExecutor.getName());
    // ReactApplicationContext对象的实例化。这个对象的实例化我们传入的就是MainApplication的对象实例
    // 从这里我们也可以看出来ReactApplicationContext是Android的Application的包装类
    final ReactApplicationContext reactContext = new ReactApplicationContext(mApplicationContext);
    //查看外部是否设置NativeModuleCallExceptionHandler，
    //它是在ReactInstanceManagerBuilder构建ReactInstanceManager是传递进来的 
    //如果设置了则使用外部NativeModuleCallExceptionHandler，如果没有设置则使用DevSupportManager。   
    NativeModuleCallExceptionHandler exceptionHandler =
        mNativeModuleCallExceptionHandler != null
            ? mNativeModuleCallExceptionHandler
            : mDevSupportManager;
    reactContext.setNativeModuleCallExceptionHandler(exceptionHandler);
    // 循环处理我们在Application里注入的ReactPackage，处理的过程就是把各自的Module添加到对应的注册表中。
    // 然后生成Java的NativeModule的注册表对象
    NativeModuleRegistry nativeModuleRegistry = processPackages(reactContext, mPackages, false);
    /**
     * jsExecutor、nativeModuleRegistry、nativeModuleRegistry等各种参数处理好之后，开始构建CatalystInstanceImpl实例。
     * 这个类的对象的实例化，我们需要中重点学习
     */
    CatalystInstanceImpl.Builder catalystInstanceBuilder =
        new CatalystInstanceImpl.Builder()
            .setReactQueueConfigurationSpec(ReactQueueConfigurationSpec.createDefault())
            .setJSExecutor(jsExecutor)
            .setRegistry(nativeModuleRegistry)
            .setJSBundleLoader(jsBundleLoader)
            .setNativeModuleCallExceptionHandler(exceptionHandler);

    ReactMarker.logMarker(CREATE_CATALYST_INSTANCE_START);
    // CREATE_CATALYST_INSTANCE_END is in JSCExecutor.cpp
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "createCatalystInstance");
    final CatalystInstance catalystInstance;
    // 催化器对象
    try {
      catalystInstance = catalystInstanceBuilder.build();
    } finally {
      Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
      ReactMarker.logMarker(CREATE_CATALYST_INSTANCE_END);
    }
    // 我们进行初始化ReactContext对象。用来关联ReacContext与CatalystInstance（RN催化器实例）
    reactContext.initializeWithInstance(catalystInstance);

    if (mJSIModulePackage != null) {
      catalystInstance.addJSIModules(
          mJSIModulePackage.getJSIModules(
              reactContext, catalystInstance.getJavaScriptContextHolder()));

      if (ReactFeatureFlags.useTurboModules) {
        JSIModule turboModuleManager =
            catalystInstance.getJSIModule(JSIModuleType.TurboModuleManager);
        catalystInstance.setTurboModuleManager(turboModuleManager);

        TurboModuleRegistry registry = (TurboModuleRegistry) turboModuleManager;

        // Eagerly initialize TurboModules
        for (String moduleName : registry.getEagerInitModuleNames()) {
          registry.getModule(moduleName);
        }
      }
    }
    if (mBridgeIdleDebugListener != null) {
      catalystInstance.addBridgeIdleDebugListener(mBridgeIdleDebugListener);
    }
    if (Systrace.isTracing(TRACE_TAG_REACT_APPS | TRACE_TAG_REACT_JS_VM_CALLS)) {
      //调用CatalystInstanceImpl的Native方法把Java Registry转换为Json，再由C++层传送到JS层。
      catalystInstance.setGlobalVariable("__RCTProfileIsProfiling", "true");
    }
    ReactMarker.logMarker(ReactMarkerConstants.PRE_RUN_JS_BUNDLE_START);
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "runJSBundle");
    // 该函数的最后调用CatalystInstance.runJSBundle()去加载JS Bundle
    // 通过CatalystInstance开始加载JS Bundle
    // 这个方法最终会调用到C++层去加载JSBundle文件
    catalystInstance.runJSBundle();
    Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);

    return reactContext;
  }

  private NativeModuleRegistry processPackages(
      ReactApplicationContext reactContext,
      List<ReactPackage> packages,
      boolean checkAndUpdatePackageMembership) {
    NativeModuleRegistryBuilder nativeModuleRegistryBuilder =
        new NativeModuleRegistryBuilder(reactContext, this);

    ReactMarker.logMarker(PROCESS_PACKAGES_START);

    synchronized (mPackages) {
      for (ReactPackage reactPackage : packages) {
        if (checkAndUpdatePackageMembership && mPackages.contains(reactPackage)) {
          continue;
        }
        Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "createAndProcessCustomReactPackage");
        try {
          if (checkAndUpdatePackageMembership) {
            mPackages.add(reactPackage);
          }
          processPackage(reactPackage, nativeModuleRegistryBuilder);
        } finally {
          Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
        }
      }
    }
    ReactMarker.logMarker(PROCESS_PACKAGES_END);

    ReactMarker.logMarker(BUILD_NATIVE_MODULE_REGISTRY_START);
    Systrace.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "buildNativeModuleRegistry");
    NativeModuleRegistry nativeModuleRegistry;
    try {
      nativeModuleRegistry = nativeModuleRegistryBuilder.build();
    } finally {
      Systrace.endSection(TRACE_TAG_REACT_JAVA_BRIDGE);
      ReactMarker.logMarker(BUILD_NATIVE_MODULE_REGISTRY_END);
    }

    return nativeModuleRegistry;
  }

  private void processPackage(
      ReactPackage reactPackage, NativeModuleRegistryBuilder nativeModuleRegistryBuilder) {
    SystraceMessage.beginSection(TRACE_TAG_REACT_JAVA_BRIDGE, "processPackage")
        .arg("className", reactPackage.getClass().getSimpleName())
        .flush();
    if (reactPackage instanceof ReactPackageLogger) {
      ((ReactPackageLogger) reactPackage).startProcessPackage();
    }
    nativeModuleRegistryBuilder.processPackage(reactPackage);

    if (reactPackage instanceof ReactPackageLogger) {
      ((ReactPackageLogger) reactPackage).endProcessPackage();
    }
    SystraceMessage.endSection(TRACE_TAG_REACT_JAVA_BRIDGE).flush();
  }
}
