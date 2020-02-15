/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow
 * @format
 */

'use strict';

const NativeModules = require('../BatchedBridge/NativeModules');
import type {TurboModule} from './RCTExport';
import invariant from 'invariant';

const turboModuleProxy = global.__turboModuleProxy;

export function get<T: TurboModule>(name: string): ?T {
  // Bridgeless mode requires TurboModules
  if (!global.RN$Bridgeless) {
    // Backward compatibility layer during migration.
    // 主要的代码其实是通过 NativeModules对象来获取对应的module，通过这个module取执⾏show⽅法
    const legacyModule = NativeModules[name];
    if (legacyModule != null) {
      return ((legacyModule: any): T);
    }
  }

  if (turboModuleProxy != null) {
    const module: ?T = turboModuleProxy(name);
    return module;
  }

  return null;
}

export function getEnforcing<T: TurboModule>(name: string): T {
  // 通过名称来获取module
  const module = get(name);
  invariant(
    module != null,
    `TurboModuleRegistry.getEnforcing(...): '${name}' could not be found. ` +
      'Verify that a module by this name is registered in the native binary.',
  );
  return module;
}
