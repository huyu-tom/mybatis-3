package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

/**
 * 用来表示各种标签和普通文本节点和包含#{},${}的文本节点
 */
public interface IMSqlNode {
  boolean apply(IMContext imContext);
}
