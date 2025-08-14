package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;
import com.huyu.dynamic.utils.IMStrUtils;

public class IMStatisticSqlNode implements IMSqlNode {

  /**
   * 静态文本
   */
  private String text;

  public IMStatisticSqlNode(String text) {
    this.text = IMStrUtils.removeExtraWhitespaces(text);
  }

  @Override
  public boolean apply(IMContext imContext) {
    imContext.appendText(text);
    return false;
  }

  public void append(String text) {
    this.text = this.text + IMStrUtils.removeExtraWhitespaces(text);
  }
}
