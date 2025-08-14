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
package org.apache.ibatis.scripting.xmltags;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BuilderException;

/**
 * @author Clinton Begin
 */
public class ExpressionEvaluator {

  /**
   * 用于计算true和false 例如在 if标签,when标签,等等标签当中
   *
   * @param expression
   * @param parameterObject
   *
   * @return
   */
  public boolean evaluateBoolean(String expression, Object parameterObject) {
    Object value = OgnlCache.getValue(expression, parameterObject);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof Number) {
      return new BigDecimal(String.valueOf(value)).compareTo(BigDecimal.ZERO) != 0;
    }
    return value != null;
  }

  /**
   * 用于foreach标签的循环
   *
   * @deprecated Since 3.5.9, use the {@link #evaluateIterable(String, Object, boolean)}.
   */
  @Deprecated
  public Iterable<?> evaluateIterable(String expression, Object parameterObject) {
    return evaluateIterable(expression, parameterObject, false);
  }

  /**
   * @since 3.5.9
   */
  public Iterable<?> evaluateIterable(String expression, Object parameterObject, boolean nullable) {
    Object value = OgnlCache.getValue(expression, parameterObject);
    if (value == null) {
      if (nullable) {
        return null;
      }
      throw new BuilderException("The expression '" + expression + "' evaluated to a null value.");
    }

    // 如果已经是迭代器的话,就直接返回
    if (value instanceof Iterable) {
      return (Iterable<?>) value;
    }

    // 如果是一个数组的话,就将其变成集合
    if (value.getClass().isArray()) {
      // the array may be primitive, so Arrays.asList() may throw
      // a ClassCastException (issue 209). Do the work manually
      // Curse primitives! :) (JGB)
      int size = Array.getLength(value);
      List<Object> answer = new ArrayList<>();
      for (int i = 0; i < size; i++) {
        Object o = Array.get(value, i);
        answer.add(o);
      }
      return answer;
    }

    // 如果是一个Map的话,手动调用Map
    if (value instanceof Map) {
      return ((Map) value).entrySet();
    }
    throw new BuilderException(
        "Error evaluating expression '" + expression + "'.  Return value (" + value + ") was not iterable.");
  }

}
