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

import java.util.*;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.mapping.ParameterMapping;

/**
 * 动态sql的上下文
 * <p>
 * bind标签的后面的标签,会解析成bind的内部标签(当做是一个同一个作用域),并且会创建新的作用域,当bind标签结束之后,就会移除该作用域 for循环标签里面,每次遍历都会创建一个新的作用域
 * </p>
 * <p>
 * <p>
 * 在解析标签的时候,我们就知道是动态sql还是静态sql, 动态的sql的话,需要在动态中获取
 * <p>
 * <p>
 * 1. 如果是静态sql的话,就直接执行 apply,并且将mappingList映射加入到上下文当中,并且相当于全部作用域了
 * 2. 如果是动态sql的话,而是在执行sql的时候,才执行apply,并且也是得到 mappingList
 * 和对应的值 mappingValueList的值
 */
public class IMContext {

  static {
    // ognl表达式
    OgnlRuntime.setPropertyAccessor(IMTreeMap.class, new ContextAccessor());
  }

  /**
   * 用做代理,不做任何实例化,减少性能损耗
   */
  protected IMContext() {
    this.isDynamic = false;
    rootBinds = null;
    text = null;
    mappingList = null;
    mappingValueList = null;
  }

  public IMContext(boolean isDynamic) {
    this(isDynamic, null);
  }

  public IMContext(boolean isDynamic, Map<String, Object> paramMap) {
    this.isDynamic = isDynamic;
    mappingList = new ArrayList<>();
    if (isDynamic) {
      // 动态sql
      mappingValueList = new ArrayList<>();
    } else {
      // 静态sql
      mappingValueList = Collections.emptyList();
    }
    this.rootBinds = new IMTreeMap(null);
    this.text = new StringJoiner(" ");
    currentBinds = rootBinds;
    if (Objects.nonNull(paramMap)) {
      currentBinds.putAll(paramMap);
    }
  }

  /**
   * 用于表明映射 #{}里面的信息,动态sql和静态sql都是一样的
   */
  private final List<ParameterMapping> mappingList;

  /**
   * 用于表明映射 #{}里面的值(经过处理之后的,不是参数封装的值),并且是动态sql才有
   */
  private final List<Object> mappingValueList;

  /**
   * 是否是动态sql 如果是true的话,说明先解析 #{},会对mappingList和mappingValueList一一赋值,然后再解析 ${},直接添加值 如果是false的话,说明只解析
   * #{},会对mappingList和mappingValueList一一赋值,然后不会解析${}
   */
  private final boolean isDynamic;

  /**
   * 是否是动态
   *
   * @return
   */
  public boolean isDynamic() {
    return isDynamic;
  }

  /**
   * 文本
   */
  private final StringJoiner text;

  /**
   * 局部复用的作用域池, 在某些标签里面,如果存在bind标签
   */
  List<IMTreeMap> pool = null;

  // 根作用域
  private final IMTreeMap rootBinds;

  // 当前作用域
  IMTreeMap currentBinds;

  /**
   * 创建作用域
   *
   * @return
   */
  public IMTreeMap createBinds() {
    // 作用域,可以复用,退出作用域可以进行复用(移除数据就可以了)
    IMTreeMap binds;
    if (Objects.isNull(pool)) {
      binds = new IMTreeMap(currentBinds);
    } else {
      binds = pool.remove(0);
      if (Objects.isNull(binds)) {
        binds = new IMTreeMap(currentBinds);
      } else {
        binds.setParent(currentBinds);
      }
    }
    return currentBinds = binds;
  }

  /**
   * 回退作用域
   *
   * @return
   */
  public IMTreeMap backBinds() {
    final IMTreeMap oldBinds = this.currentBinds;
    final IMTreeMap parent = oldBinds.getParent();
    if (Objects.nonNull(parent)) {
      this.currentBinds = parent;
    }

    // 初始化
    oldBinds.setParent(null);
    oldBinds.clear();
    if (Objects.isNull(pool)) {
      pool = new ArrayList<>();
    }
    pool.add(oldBinds);

    return parent;
  }

  public IMTreeMap currentBinds() {
    return this.currentBinds;
  }

  /**
   * 设置指定的key的值为value
   *
   * @param key
   * @param value
   */
  public void put(String key, Object value) {
    currentBinds.put(key, value);
  }

  /**
   * put全部
   *
   * @param map
   */
  public void putAll(Map<Object, Object> map) {
    currentBinds.putAll(map);
  }

  /**
   * 用于bind覆盖掉之前定义的变量的值
   *
   * @param key
   * @param value
   */
  public void getPut(String key, Object value) {
    if (Objects.isNull(currentBinds.getPut(key, value))) {
      currentBinds.put(key, value);
    }
  }

  /**
   * 获取指定key的值
   *
   * @param key
   *
   * @return
   */
  public Object get(String key) {
    return currentBinds.get(key);
  }

  /**
   * 移除值
   *
   * @param key
   *
   * @return
   */
  public Object remove(String key) {
    return currentBinds.remove(key);
  }

  /**
   * 添加文本
   *
   * @param text
   */
  public void appendText(String text) {
    this.text.add(text);
  }

  /**
   * 当前上下文choose中case上下文中isBreak
   */
  private boolean isBreak = false;

  private boolean isMatch = false;

  public void setBreak(final boolean isBreak) {
    this.isBreak = isBreak;
  }

  public boolean getBreak() {
    return this.isBreak;
  }

  public void setMatch(final boolean match) {
    this.isMatch = match;
  }

  public boolean getMatch() {
    return this.isMatch;
  }

  public String getText() {
    return text.toString();
  }

  // 用于bind标签上下文的情况
  private boolean isBinds = false;

  public void setBinds(boolean isBinds) {
    this.isBinds = isBinds;
  }

  public boolean getBinds() {
    return this.isBinds;
  }

  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;

      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }
      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}
