/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.modules.core;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.common.SystemClock;
import com.facebook.react.devsupport.interfaces.DevSupportManager;
import com.facebook.react.jstasks.HeadlessJsTaskContext;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the native implementation for JS timer execution on Android. It schedules JS timers
 * to be invoked on frame boundaries using {@link ReactChoreographer}.
 *
 * <p>This is used by the NativeModule {@link TimingModule}.
 */
public class JavaTimerManager {

  // These timing constants should be kept in sync with the ones in `JSTimers.js`.
  // The minimum time in milliseconds left in the frame to call idle callbacks.
  private static final float IDLE_CALLBACK_FRAME_DEADLINE_MS = 1.f;
  // The total duration of a frame in milliseconds, this assumes that devices run at 60 fps.
  // TODO: Lower frame duration on devices that are too slow to run consistently
  // at 60 fps.
  private static final float FRAME_DURATION_MS = 1000.f / 60.f;

  /**
   * 这个Timer的对象，我们可以看到很简单
   * 其实就是一个简单的实体对象
   */
  private static class Timer {
    private final int mCallbackID;
    private final boolean mRepeat;
    private final int mInterval;
    private long mTargetTime;

    private Timer(int callbackID, long initialTargetTime, int duration, boolean repeat) {
      mCallbackID = callbackID;
      mTargetTime = initialTargetTime;
      mInterval = duration;
      mRepeat = repeat;
    }
  }
  /**
   * 那既然，我们在优先级队列里面存入Timer的实体。那么我们什么时候把他取出来的？？
   * 怎么让他在规定的时间进行回调回去呢？？
   * 就是下面这个方法了！！！
   */
  private class TimerFrameCallback extends ChoreographerCompat.FrameCallback {

    // Temporary map for constructing the individual arrays of timers to call
    private @Nullable WritableArray mTimersToCall = null;

    /** Calls all timers that have expired since the last time this frame callback was called. */
    @Override
    public void doFrame(long frameTimeNanos) {
      if (isPaused.get() && !isRunningTasks.get()) {
        return;
      }

      long frameTimeMillis = frameTimeNanos / 1000000;
      // 
      synchronized (mTimerGuard) {
        // 我们知道doFrame是每隔？毫秒。然后就可以就行队列里面的数据取出来
        // 这个方法不是一个阻塞式的方法。就是每一次回调doFrame方法，都讲这个队列中所有已经到时间的Timer取出来
        // 一次性回调除去
        while (!mTimers.isEmpty() && mTimers.peek().mTargetTime < frameTimeMillis) {
          // 从队列里面里面期初一个Timer
          Timer timer = mTimers.poll();
          if (mTimersToCall == null) {
            mTimersToCall = Arguments.createArray();
          }
          // 将 Timer的CallbackID加入到数组总
          mTimersToCall.pushInt(timer.mCallbackID);
          // 判断timer是否是一个重复的，如果是的话。再将这个入TImer入队列。
          // 但是不影响这次的回调执行
          if (timer.mRepeat) {
            timer.mTargetTime = frameTimeMillis + timer.mInterval;
            mTimers.add(timer);
          } else {
            // 如果非重复，则移除
            mTimerIdsToTimers.remove(timer.mCallbackID);
          }
        }
      }
      // 调用mJavaScriptTimerManager的callTimers的进行，将所有的满足的Timer的返回除去
      if (mTimersToCall != null) {
        mJavaScriptTimerManager.callTimers(mTimersToCall);
        mTimersToCall = null;
      }

      mReactChoreographer.postFrameCallback(ReactChoreographer.CallbackType.TIMERS_EVENTS, this);
    }
  }

  private class IdleFrameCallback extends ChoreographerCompat.FrameCallback {

    @Override
    public void doFrame(long frameTimeNanos) {
      if (isPaused.get() && !isRunningTasks.get()) {
        return;
      }

      // If the JS thread is busy for multiple frames we cancel any other pending runnable.
      if (mCurrentIdleCallbackRunnable != null) {
        mCurrentIdleCallbackRunnable.cancel();
      }

      mCurrentIdleCallbackRunnable = new IdleCallbackRunnable(frameTimeNanos);
      mReactApplicationContext.runOnJSQueueThread(mCurrentIdleCallbackRunnable);

      mReactChoreographer.postFrameCallback(ReactChoreographer.CallbackType.IDLE_EVENT, this);
    }
  }

  private class IdleCallbackRunnable implements Runnable {
    private volatile boolean mCancelled = false;
    private final long mFrameStartTime;

    public IdleCallbackRunnable(long frameStartTime) {
      mFrameStartTime = frameStartTime;
    }

    @Override
    public void run() {
      if (mCancelled) {
        return;
      }

      long frameTimeMillis = mFrameStartTime / 1000000;
      long timeSinceBoot = SystemClock.uptimeMillis();
      long frameTimeElapsed = timeSinceBoot - frameTimeMillis;
      long time = SystemClock.currentTimeMillis();
      long absoluteFrameStartTime = time - frameTimeElapsed;

      if (FRAME_DURATION_MS - (float) frameTimeElapsed < IDLE_CALLBACK_FRAME_DEADLINE_MS) {
        return;
      }

      boolean sendIdleEvents;
      synchronized (mIdleCallbackGuard) {
        sendIdleEvents = mSendIdleEvents;
      }

      if (sendIdleEvents) {
        mJavaScriptTimerManager.callIdleCallbacks(absoluteFrameStartTime);
      }

      mCurrentIdleCallbackRunnable = null;
    }

    public void cancel() {
      mCancelled = true;
    }
  }

  private final ReactApplicationContext mReactApplicationContext;
  private final JavaScriptTimerManager mJavaScriptTimerManager;
  private final ReactChoreographer mReactChoreographer;
  private final DevSupportManager mDevSupportManager;
  /// JavaTimerManager里面的锁对象，主要锁入队列、合出队列的的逻辑
  private final Object mTimerGuard = new Object();
  private final Object mIdleCallbackGuard = new Object();
  // 这个是一个优先级对象。这样就可以使得从timer在入队列的时候按照时间的先后顺序
  private final PriorityQueue<Timer> mTimers;
  private final SparseArray<Timer> mTimerIdsToTimers;
  private final AtomicBoolean isPaused = new AtomicBoolean(true);
  private final AtomicBoolean isRunningTasks = new AtomicBoolean(false);
  private final TimerFrameCallback mTimerFrameCallback = new TimerFrameCallback();
  private final IdleFrameCallback mIdleFrameCallback = new IdleFrameCallback();
  private @Nullable IdleCallbackRunnable mCurrentIdleCallbackRunnable;
  private boolean mFrameCallbackPosted = false;
  private boolean mFrameIdleCallbackPosted = false;
  private boolean mSendIdleEvents = false;

  /**
   * 我们看第一个参数mJavaScriptTimerManager构造函数参数
   * 这个就是TimingModule的中传入的，主要用来负责将到时间的Timer回调到JS侧
   */
  public JavaTimerManager(
      ReactApplicationContext reactContext,
      JavaScriptTimerManager javaScriptTimerManager,
      ReactChoreographer reactChoreographer,
      DevSupportManager devSupportManager) {
    mReactApplicationContext = reactContext;
    mJavaScriptTimerManager = javaScriptTimerManager;
    mReactChoreographer = reactChoreographer;
    mDevSupportManager = devSupportManager;

    // We store timers sorted by finish time.
    // 我们来进行优先级队列的实例化，其实就是按照结束时间的
    mTimers =
        new PriorityQueue<Timer>(
            11, // Default capacity: for some reason they don't expose a (Comparator) constructor
            new Comparator<Timer>() {
              @Override
              public int compare(Timer lhs, Timer rhs) {
                // 判断两个Timer的结束时间
                long diff = lhs.mTargetTime - rhs.mTargetTime;
                if (diff == 0) {
                  return 0;
                } else if (diff < 0) {
                  return -1;
                } else {
                  return 1;
                }
              }
            });
    mTimerIdsToTimers = new SparseArray<>();
  }

  public void onHostPause() {
    isPaused.set(true);
    clearFrameCallback();
    maybeIdleCallback();
  }

  public void onHostDestroy() {
    clearFrameCallback();
    maybeIdleCallback();
  }

  public void onHostResume() {
    isPaused.set(false);
    // TODO(5195192) Investigate possible problems related to restarting all tasks at the same
    // moment
    // 
    setChoreographerCallback();
    maybeSetChoreographerIdleCallback();
  }

  public void onHeadlessJsTaskStart(int taskId) {
    if (!isRunningTasks.getAndSet(true)) {
      setChoreographerCallback();
      maybeSetChoreographerIdleCallback();
    }
  }

  public void onHeadlessJsTaskFinish(int taskId) {
    HeadlessJsTaskContext headlessJsTaskContext =
        HeadlessJsTaskContext.getInstance(mReactApplicationContext);
    if (!headlessJsTaskContext.hasActiveTasks()) {
      isRunningTasks.set(false);
      clearFrameCallback();
      maybeIdleCallback();
    }
  }

  public void onInstanceDestroy() {
    clearFrameCallback();
    clearChoreographerIdleCallback();
  }

  private void maybeSetChoreographerIdleCallback() {
    synchronized (mIdleCallbackGuard) {
      if (mSendIdleEvents) {
        setChoreographerIdleCallback();
      }
    }
  }

  private void maybeIdleCallback() {
    if (isPaused.get() && !isRunningTasks.get()) {
      clearFrameCallback();
    }
  }

  /**
   * 设置Choreographer的Callback的犯法
   */
  private void setChoreographerCallback() {
    if (!mFrameCallbackPosted) {
      mReactChoreographer.postFrameCallback(
          ReactChoreographer.CallbackType.TIMERS_EVENTS, mTimerFrameCallback);
      mFrameCallbackPosted = true;
    }
  }

  private void clearFrameCallback() {
    HeadlessJsTaskContext headlessJsTaskContext =
        HeadlessJsTaskContext.getInstance(mReactApplicationContext);
    if (mFrameCallbackPosted && isPaused.get() && !headlessJsTaskContext.hasActiveTasks()) {
      mReactChoreographer.removeFrameCallback(
          ReactChoreographer.CallbackType.TIMERS_EVENTS, mTimerFrameCallback);
      mFrameCallbackPosted = false;
    }
  }

  private void setChoreographerIdleCallback() {
    if (!mFrameIdleCallbackPosted) {
      mReactChoreographer.postFrameCallback(
          ReactChoreographer.CallbackType.IDLE_EVENT, mIdleFrameCallback);
      mFrameIdleCallbackPosted = true;
    }
  }

  private void clearChoreographerIdleCallback() {
    if (mFrameIdleCallbackPosted) {
      mReactChoreographer.removeFrameCallback(
          ReactChoreographer.CallbackType.IDLE_EVENT, mIdleFrameCallback);
      mFrameIdleCallbackPosted = false;
    }
  }

  /**
   * A method to be used for synchronously creating a timer. The timer will not be invoked until the
   * next frame, regardless of whether it has already expired (i.e. the delay is 0).
   *
   * @param callbackID An identifier for the callback that can be passed to JS or C++ to invoke it.
   * @param delay The time in ms before the callback should be invoked.
   * @param repeat Whether the timer should be repeated (used for setInterval).
   */
  public void createTimer(final int callbackID, final long delay, final boolean repeat) {
    // 实例化Timer触发的时机
    long initialTargetTime = SystemClock.nanoTime() / 1000000 + delay;
    // 创建一个Timer对象
    Timer timer = new Timer(callbackID, initialTargetTime, (int) delay, repeat);
    synchronized (mTimerGuard) {
      // 然后将
      mTimers.add(timer);
      mTimerIdsToTimers.put(callbackID, timer);
    }
  }

  /**
   * A method to be used for asynchronously creating a timer. If the timer has already expired,
   * (based on the provided jsSchedulingTime) then it will be immediately invoked.
   *
   * @param callbackID An identifier that can be passed back to JS to invoke the callback.
   * @param duration The time in ms before the callback should be invoked.
   * @param jsSchedulingTime The time (ms since epoch) when this timer was created in JS.
   * @param repeat Whether the timer should be repeated (used for setInterval)
   */
  public void createAndMaybeCallTimer(
      final int callbackID,
      final int duration,
      final double jsSchedulingTime,
      final boolean repeat) {
    // Java虚拟机的本地时间    
    long deviceTime = SystemClock.currentTimeMillis();
    // 这个我们很熟悉，其实就是JS那边传递过来的时间
    long remoteTime = (long) jsSchedulingTime;

    // If the times on the server and device have drifted throw an exception to warn the developer
    // that things might not work or results may not be accurate. This is required only for
    // developer builds.
    // 开发模式下，进行对时的异常告警
    if (mDevSupportManager.getDevSupportEnabled()) {
      long driftTime = Math.abs(remoteTime - deviceTime);
      if (driftTime > 60000) {
        mJavaScriptTimerManager.emitTimeDriftWarning(
            "Debugger and device times have drifted by more than 60s. Please correct this by "
                + "running adb shell \"date `date +%m%d%H%M%Y.%S`\" on your debugger machine.");
      }
    }

    // Adjust for the amount of time it took for native to receive the timer registration call
    long adjustedDuration = Math.max(0, remoteTime - deviceTime + duration);
    // 如果Timer的回调时长为0 
    if (duration == 0 && !repeat) {
      WritableArray timerToCall = Arguments.createArray();
      timerToCall.pushInt(callbackID);
      mJavaScriptTimerManager.callTimers(timerToCall);
      return;
    }
    // 创建计时器
    createTimer(callbackID, adjustedDuration, repeat);
  }

  public void deleteTimer(int timerId) {
    synchronized (mTimerGuard) {
      Timer timer = mTimerIdsToTimers.get(timerId);
      if (timer == null) {
        return;
      }
      mTimerIdsToTimers.remove(timerId);
      mTimers.remove(timer);
    }
  }

  public void setSendIdleEvents(final boolean sendIdleEvents) {
    synchronized (mIdleCallbackGuard) {
      mSendIdleEvents = sendIdleEvents;
    }

    UiThreadUtil.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            synchronized (mIdleCallbackGuard) {
              if (sendIdleEvents) {
                setChoreographerIdleCallback();
              } else {
                clearChoreographerIdleCallback();
              }
            }
          }
        });
  }
}
