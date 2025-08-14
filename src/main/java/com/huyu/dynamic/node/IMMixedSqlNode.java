package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

import java.util.Collections;
import java.util.List;

public class IMMixedSqlNode implements IMSqlNode {

  private final List<IMSqlNode> contents;

  public IMMixedSqlNode(List<IMSqlNode> contents) {
    this.contents = contents;
  }

  @Override
  public boolean apply(IMContext context) {
    contents.forEach(node -> node.apply(context));
    return true;
  }

  public List<IMSqlNode> getContents() {
    return Collections.unmodifiableList(contents);
  }
}
