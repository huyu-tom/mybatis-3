package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

import org.apache.ibatis.scripting.xmltags.ExpressionEvaluator;

public class IMCaseSqlNode implements IMSqlNode {

  // 该case是否具有break标签
  private final boolean isBreak;

  // 表达式的计算器
  private final ExpressionEvaluator evaluator;

  // 标签的表达式
  private final String test;

  // 为true,就会执行里面的
  private final IMSqlNode contents;

  // 是否创建一个作用域
  private final boolean isCreateBinds;

  public IMCaseSqlNode(final boolean isBreak, final IMIfSqlNode contents, final String test, boolean isCreateBinds) {
    this.isBreak = isBreak;
    this.contents = contents;
    this.test = test;
    this.isCreateBinds = isCreateBinds;
    this.evaluator = new ExpressionEvaluator();
  }

  /**
   * @param imContext
   *
   * @return
   */
  @Override
  public boolean apply(IMContext imContext) {
    // 如果没有break的话,并且之前有一个返回true,说明后续就不需要判断,而是直接可执行
    try {
      if (isCreateBinds) {
        imContext.createBinds();
      }
      final boolean breakCurrentEnv = imContext.getBreak();
      final boolean matchCurrentEnv = imContext.getMatch();
      if (!breakCurrentEnv && matchCurrentEnv) {
        // 说明,不需要判断
        contents.apply(imContext);
        return true;
      } else {
        // 需要判断的情况就是 (未匹配) 如果都是未匹配,break就更加不可能为true了
        if (evaluator.evaluateBoolean(test, imContext.currentBinds())) {
          contents.apply(imContext);
          imContext.setMatch(true);
          imContext.setBreak(isBreak);
          return true;
        }
      }
    } finally {
      if (isCreateBinds) {
        imContext.backBinds();
      }
    }
    return false;
  }
}
