/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.uimanager;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.facebook.react.uimanager.common.UIManagerType;

/** Interface for the root native view of a React native application */
public interface ReactRoot {

  /** Return cached launch properties for app */
  @Nullable
  Bundle getAppProperties();

  @Nullable
  String getInitialUITemplate();

  String getJSModuleName();

  /** Fabric or Default UI Manager, see {@link UIManagerType} */
  @UIManagerType
  int getUIManagerType();

  int getRootViewTag();

  void setRootViewTag(int rootViewTag);

  /** Calls into JS to start the React application. */
  // 我们看这个方法里面的注释：调用JS层执行启动RN的Application
  void runApplication();

  /** Handler for stages {@link com.facebook.react.surface.ReactStage} */
  void onStage(int stage);

  /** Return native view for root */
  ViewGroup getRootViewGroup();

  /** @return Cached values for widthMeasureSpec and heightMeasureSpec */
  int getWidthMeasureSpec();

  int getHeightMeasureSpec();

  /** Sets a flag that determines whether to log that content appeared on next view added. */
  void setShouldLogContentAppeared(boolean shouldLogContentAppeared);

  /**
   * @return a {@link String} that represents the root js application that is being rendered with
   *     this {@link ReactRoot}
   * @deprecated We recommend to not use this method as it is will be replaced in the near future.
   */
  @Deprecated
  @Nullable
  String getSurfaceID();
}
