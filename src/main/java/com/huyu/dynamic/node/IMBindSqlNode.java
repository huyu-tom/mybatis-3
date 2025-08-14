package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

import org.apache.ibatis.scripting.xmltags.OgnlCache;

public class IMBindSqlNode implements IMSqlNode {

  // 变量名 Bind
  private final String name;

  // 表达式,从上下文中用该表达式获取值,并且将变量名为name的值设置为之前表达式获取的值
  private final String expression;

  public IMBindSqlNode(String name, String exp) {
    this.name = name;
    this.expression = exp;
  }

  @Override
  public boolean apply(IMContext imContext) {
    final Object value = OgnlCache.getValue(expression, imContext.currentBinds());
    imContext.put(name, value);
    return false;
  }
}
