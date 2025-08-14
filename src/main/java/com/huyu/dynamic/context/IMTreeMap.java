/*
 *    Copyright 2009-2023 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.huyu.dynamic.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IMTreeMap extends HashMap<Object, Object> {

  public IMTreeMap(int initialCapacity, float loadFactor, IMTreeMap parent) {
    super(initialCapacity, loadFactor);
    this.parent = parent;
  }

  public IMTreeMap(int initialCapacity, IMTreeMap parent) {
    super(initialCapacity);
    this.parent = parent;
  }

  public IMTreeMap(IMTreeMap parent) {
    this.parent = parent;
  }

  public IMTreeMap(Map<? extends String, ?> m, IMTreeMap parent) {
    super(m);
    this.parent = parent;
  }

  private IMTreeMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  private IMTreeMap(int initialCapacity) {
    super(initialCapacity);
  }

  private IMTreeMap() {
  }

  private IMTreeMap(Map<? extends String, ?> m) {
    super(m);
  }

  /**
   * 父作用域
   */
  private IMTreeMap parent;

  public IMTreeMap getParent() {
    return parent;
  }

  protected IMTreeMap setParent(IMTreeMap parent) {
    return this.parent = parent;
  }

  /**
   * 获取,当前没有获取到的话,就从父中进行获取
   *
   * @param key
   *
   * @return
   */
  @Override

  public Object get(Object key) {
    final Object o = super.get(key);
    if (Objects.isNull(o) && Objects.nonNull(parent)) {
      return parent.get(key);
    }
    return o;
  }

  @Override
  public Object put(Object key, Object value) {
    return super.put(key, value);
  }

  @Override
  public Object remove(Object key) {
    final Object o = super.remove(key);
    if (Objects.nonNull(o)) {
      return o;
    }
    if (Objects.nonNull(parent)) {
      return parent.remove(key);
    }
    return o;
  }

  /**
   * 从获取该key的作用域里面,然后覆盖掉该值 用于bind已定义了变量,这里相当于重新赋值(覆盖)
   *
   * @param key
   * @param value
   */
  public Object getPut(Object key, Object value) {
    Object o = super.get(key);
    if (Objects.isNull(o)) {
      // 当前作用域没有找到,并且具有父作用域,就调用getPut
      if (Objects.nonNull(parent)) {
        return parent.getPut(key, value);
      }
    } else {
      // 有值,就覆盖掉
      return super.put(key, value);
    }
    return null;
  }
}
