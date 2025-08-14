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

import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class ForEachSqlNode implements SqlNode {
  public static final String ITEM_PREFIX = "__frch_";
  // 表达式计算
  private final ExpressionEvaluator evaluator;
  // 集合的数据表达式的名称
  private final String collectionExpression;
  // 可否为null
  private final Boolean nullable;
  // for里面的标签执行
  private final SqlNode contents;
  // 前缀
  private final String open;
  // 后缀
  private final String close;
  // 每个for循环实体的切割
  private final String separator;
  // 值的名称
  private final String item;
  // 索引
  private final String index;
  private final Configuration configuration;

  /**
   * @deprecated Since 3.5.9, use the
   *             {@link #ForEachSqlNode(Configuration, SqlNode, String, Boolean, String, String, String, String, String)}.
   */
  @Deprecated
  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, String index,
      String item, String open, String close, String separator) {
    this(configuration, contents, collectionExpression, null, index, item, open, close, separator);
  }

  /**
   * @since 3.5.9
   */
  public ForEachSqlNode(Configuration configuration, SqlNode contents, String collectionExpression, Boolean nullable,
      String index, String item, String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.nullable = nullable;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
    this.configuration = configuration;
  }

  @Override
  public boolean apply(DynamicContext context) {
    Map<String, Object> bindings = context.getBindings();

    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, bindings,
        Optional.ofNullable(nullable).orElseGet(configuration::isNullableOnForEach));
    if (iterable == null || !iterable.iterator().hasNext()) {
      return true;
    }

    boolean first = true;
    applyOpen(context);

    int i = 0;
    for (Object o : iterable) {
      DynamicContext oldContext = context;

      // 对于每次分割符号的功能,都是在上下文的appendsql逻辑里面,但是默认的上下文只是会默认的添加,不会添加分割符号
      if (first || separator == null) {
        context = new PrefixedContext(context, "");
      } else {
        context = new PrefixedContext(context, separator);
      }

      // 唯一标识,用来解决多个for循环item标识,局部变量的唯一性
      int uniqueNumber = context.getUniqueNumber();

      // Issue #709
      if (o instanceof Map.Entry) {
        @SuppressWarnings("unchecked")
        Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
        applyIndex(context, mapEntry.getKey(), uniqueNumber);
        applyItem(context, mapEntry.getValue(), uniqueNumber);
      } else {
        // key索引,从0开始
        applyIndex(context, i, uniqueNumber);
        applyItem(context, o, uniqueNumber);
      }

      // 遍历以后之后,就执行里面的操作,由于我们采用树形作用域,并且 #{},提前要解析
      contents.apply(new FilteredDynamicContext(configuration, context, index, item, uniqueNumber));

      if (first) {
        first = !((PrefixedContext) context).isPrefixApplied();
      }

      context = oldContext;
      i++;
    }

    applyClose(context);

    // 移除局部变量,移除item和index
    context.getBindings().remove(item);
    context.getBindings().remove(index);
    return true;
  }

  private void applyIndex(DynamicContext context, Object o, int i) {
    if (index != null) {
      context.bind(index, o);
      context.bind(itemizeItem(index, i), o);
    }
  }

  private void applyItem(DynamicContext context, Object o, int i) {
    if (item != null) {
      context.bind(item, o);
      context.bind(itemizeItem(item, i), o);
    }
  }

  private void applyOpen(DynamicContext context) {
    if (open != null) {
      context.appendSql(open);
    }
  }

  private void applyClose(DynamicContext context) {
    if (close != null) {
      context.appendSql(close);
    }
  }

  private static String itemizeItem(String item, int i) {
    return ITEM_PREFIX + item + "_" + i;
  }

  /**
   * 用于For循环的动态动态上下文
   */
  private static class FilteredDynamicContext extends DynamicContext {
    private final DynamicContext delegate;
    private final int index;
    private final String itemIndex;
    private final String item;

    public FilteredDynamicContext(Configuration configuration, DynamicContext delegate, String itemIndex, String item,
        int i) {
      super(configuration, null);
      this.delegate = delegate;
      this.index = i;
      this.itemIndex = itemIndex;
      this.item = item;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public void appendSql(String sql) {
      GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
        // For循环里面 item都是固定的,所有这里要变成唯一的 #{xxxx}
        String newContent = content.replaceFirst("^\\s*" + item + "(?![^.,:\\s])", itemizeItem(item, index));
        if (itemIndex != null && newContent.equals(content)) {
          newContent = content.replaceFirst("^\\s*" + itemIndex + "(?![^.,:\\s])", itemizeItem(itemIndex, index));
        }
        return "#{" + newContent + "}";
      });

      delegate.appendSql(parser.parse(sql));
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }

  }

  /**
   * 前缀上下文
   */
  private class PrefixedContext extends DynamicContext {
    private final DynamicContext delegate;
    private final String prefix;
    private boolean prefixApplied;

    public PrefixedContext(DynamicContext delegate, String prefix) {
      super(configuration, null);
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public Map<String, Object> getBindings() {
      return delegate.getBindings();
    }

    @Override
    public void bind(String name, Object value) {
      delegate.bind(name, value);
    }

    @Override
    public void appendSql(String sql) {
      if (!prefixApplied && sql != null && sql.trim().length() > 0) {
        delegate.appendSql(prefix);
        prefixApplied = true;
      }
      delegate.appendSql(sql);
    }

    @Override
    public String getSql() {
      return delegate.getSql();
    }

    @Override
    public int getUniqueNumber() {
      return delegate.getUniqueNumber();
    }
  }

}
