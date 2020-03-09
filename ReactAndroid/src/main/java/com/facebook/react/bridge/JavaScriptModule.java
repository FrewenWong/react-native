/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.bridge;

import com.facebook.proguard.annotations.DoNotStrip;

/**
 * Interface denoting that a class is the interface to a module with the same name in JS. Calling
 * functions on this interface will result in corresponding methods in JS being called.
 *
 * <p>When extending JavaScriptModule and registering it with a CatalystInstance, all public methods
 * are assumed to be implemented on a JS module with the same name as this class. Calling methods on
 * the object returned from {@link ReactContext#getJSModule} or {@link CatalystInstance#getJSModule}
 * will result in the methods with those names exported by that module being called in JS.
 *
 * <p>NB: JavaScriptModule does not allow method name overloading because JS does not allow method
 * name overloading.
 * JavaScriptModule：这是一个接口，JS Module都会继承此接口，它表示在JS层会有一个相同名字的js文件，该js文件实现了该接口定义的方法，JavaScriptModuleRegistry会利用
动态代理将这个接口生成代理类，并通过C++传递给JS层，进而调用JS层的方法。
 */
@DoNotStrip
public interface JavaScriptModule {}
