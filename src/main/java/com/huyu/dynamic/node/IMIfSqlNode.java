package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

import java.util.Objects;

import org.apache.ibatis.scripting.xmltags.ExpressionEvaluator;

/**
 * <pre>
 * if - else
 * if - else if
 * if - else if - else
 * </pre>
 * <p>
 * <p>
 * if标签和else-if标签都会解析成该node 然后改node里面有一个else node =》 elseContents 当if标签后面是else的时候,
 * 该elseContents不为空,并且if标签的执行变量为false的时候,会执行,为ture的时候,不会执行 当if标签后面是 else-if的时候,该elseContents是为空的,所以他是不会执行的 而是执行else if
 * 标签, 而执行else-if标签的时候也是该类(但是是其他的事例),逻辑一样
 */
public class IMIfSqlNode implements IMSqlNode {
  // 表达式的计算器
  private final ExpressionEvaluator evaluator;

  // IF 标签的表达式
  private final String test;

  // 为true,就会执行里面的
  private final IMSqlNode contents;

  // else 标签

  // else-if 标签
  // if (aaa) xxx else if(zzz) xxx
  // ===> 最终解释为:
  // if(aaa) xxx
  // else {
  // if(zzz){
  // xxx
  // }
  // }
  private IMSqlNode elseContents;

  // 是否要创建作用域(一般根据该标签下面是否具有bind标签)
  private final boolean createBinds;

  public IMIfSqlNode(IMSqlNode contents, String test, boolean createBinds) {
    this(contents, null, test, createBinds);
  }

  public IMIfSqlNode(IMSqlNode contents, IMSqlNode elseContents, String test, boolean createBinds) {
    this.test = test;
    this.contents = contents;
    this.elseContents = elseContents;
    this.evaluator = new ExpressionEvaluator();
    this.createBinds = createBinds;
  }

  /**
   * @param defaultSqlNode
   */
  public void setDefaultSqlNode(IMSqlNode defaultSqlNode) {
    this.elseContents = defaultSqlNode;
  }

  @Override
  public boolean apply(IMContext context) {
    // if 或者 else if
    if (evaluator.evaluateBoolean(test, context.currentBinds())) {
      try {
        if (createBinds) {
          context.createBinds();
        }
        contents.apply(context);
        return true;
      } finally {
        if (createBinds) {
          context.backBinds();
        }
      }
    }

    // else
    if (Objects.nonNull(elseContents)) {
      elseContents.apply(context);
      return true;
    }
    return false;
  }
}
