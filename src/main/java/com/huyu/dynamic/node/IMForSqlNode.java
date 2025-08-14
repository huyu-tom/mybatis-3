package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;
import com.huyu.dynamic.context.IMTreeMap;

import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.scripting.xmltags.ExpressionEvaluator;

public class IMForSqlNode implements IMSqlNode {
  // 表达式计算
  private final ExpressionEvaluator evaluator;
  // 集合的数据表达式的名称
  private final String collectionExpression;
  // 可否为null
  private final Boolean nullable;
  // for里面的标签执行
  private final IMSqlNode contents;
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

  public IMForSqlNode(IMSqlNode contents, String collectionExpression, Boolean nullable, String index, String item,
      String open, String close, String separator) {
    this.evaluator = new ExpressionEvaluator();
    this.collectionExpression = collectionExpression;
    this.nullable = nullable;
    this.contents = contents;
    this.open = open;
    this.close = close;
    this.separator = separator;
    this.index = index;
    this.item = item;
  }

  @Override
  public boolean apply(IMContext context) {
    IMTreeMap imTreeMap = context.currentBinds();

    final Iterable<?> iterable = evaluator.evaluateIterable(collectionExpression, imTreeMap,
        Optional.ofNullable(nullable).orElseGet(() -> {
          return true;
        }));

    if (iterable == null || !iterable.iterator().hasNext()) {
      return true;
    }

    boolean first = true;
    applyOpen(context);

    PrefixedContext prefixedContext = new PrefixedContext(context, "");
    int i = 0;
    for (Object o : iterable) {
      try {
        // 开辟一个作用域
        prefixedContext.createBinds();

        // 对于每次分割符号的功能,都是在上下文的appendText逻辑里面,但是默认的上下文只是会默认的添加,不会添加分割符号
        // 这里复用同一个,在mybatis的源码里面是每次都是new的
        if (first || separator == null) {
          prefixedContext.init(context, "");
        } else {
          prefixedContext.init(context, separator);
        }

        if (o instanceof Map.Entry) {
          Map.Entry<Object, Object> mapEntry = (Map.Entry<Object, Object>) o;
          prefixedContext.put(this.index, mapEntry.getKey());
          prefixedContext.put(this.item, mapEntry.getValue());
        } else {
          prefixedContext.put(this.index, i);
          prefixedContext.put(this.item, o);
        }

        // 执行for标签里面的逻辑
        contents.apply(prefixedContext);

        if (first) {
          first = !(prefixedContext).isPrefixApplied();
        }

        i++;
      } finally {
        // 回滚一个作用域
        prefixedContext.backBinds();
      }
    }
    applyClose(context);

    return true;
  }

  private void applyOpen(IMContext context) {
    if (open != null) {
      context.appendText(open);
    }
  }

  private void applyClose(IMContext context) {
    if (close != null) {
      context.appendText(close);
    }
  }

  /**
   * 分割符号
   */
  private class PrefixedContext extends IMContext {
    private IMContext delegate;
    private String prefix;
    private boolean prefixApplied;

    /**
     * 复用
     *
     * @param delegate
     * @param prefix
     *
     * @return
     */
    protected PrefixedContext init(IMContext delegate, String prefix) {
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
      return this;
    }

    public PrefixedContext(IMContext delegate, String prefix) {
      super();
      this.delegate = delegate;
      this.prefix = prefix;
      this.prefixApplied = false;
    }

    public boolean isPrefixApplied() {
      return prefixApplied;
    }

    @Override
    public void appendText(String text) {
      if (!prefixApplied && text != null && !text.trim().isEmpty()) {
        delegate.appendText(prefix);
        prefixApplied = true;
      }
      delegate.appendText(text);
    }

    @Override
    public boolean isDynamic() {
      return delegate.isDynamic();
    }

    @Override
    public IMTreeMap createBinds() {
      return delegate.createBinds();
    }

    @Override
    public IMTreeMap backBinds() {
      return delegate.backBinds();
    }

    @Override
    public IMTreeMap currentBinds() {
      return delegate.currentBinds();
    }

    @Override
    public void put(String key, Object value) {
      delegate.put(key, value);
    }

    @Override
    public void getPut(String key, Object value) {
      delegate.getPut(key, value);
    }

    @Override
    public Object get(String key) {
      return delegate.get(key);
    }

    @Override
    public Object remove(String key) {
      return delegate.remove(key);
    }

    @Override
    public void setBreak(boolean isBreak) {
      delegate.setBreak(isBreak);
    }

    @Override
    public boolean getBreak() {
      return delegate.getBreak();
    }

    @Override
    public void setMatch(boolean match) {
      delegate.setMatch(match);
    }

    @Override
    public boolean getMatch() {
      return delegate.getMatch();
    }

    @Override
    public void setBinds(boolean isBinds) {
      delegate.setBinds(isBinds);
    }

    @Override
    public boolean getBinds() {
      return delegate.getBinds();
    }
  }
}
