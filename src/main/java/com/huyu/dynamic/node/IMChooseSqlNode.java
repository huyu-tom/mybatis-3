package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;

import java.util.List;

/**
 * <pre>
 * choose
 *     case
 *     case
 *     case
 *     default
 * </pre>
 */
public class IMChooseSqlNode implements IMSqlNode {
  private final IMSqlNode defaultSqlNode;
  private final List<IMSqlNode> caseSqlNodes;

  /**
   * @param caseSqlNodes
   * @param defaultSqlNode
   */
  public IMChooseSqlNode(List<IMSqlNode> caseSqlNodes, IMSqlNode defaultSqlNode) {
    this.defaultSqlNode = defaultSqlNode;
    this.caseSqlNodes = caseSqlNodes;
  }

  @Override
  public boolean apply(IMContext context) {
    final boolean breakCurrentEnv = context.getBreak();
    final boolean matchCurrentEnv = context.getMatch();

    try {
      // 这里就进行
      for (IMSqlNode sqlNode : caseSqlNodes) {
        // case
        sqlNode.apply(context);
        if (context.getBreak() && context.getMatch()) {
          break;
        }
      }

      boolean match = context.getMatch();
      if (!match && defaultSqlNode != null) {
        defaultSqlNode.apply(context);
        return true;
      }
    } finally {
      context.setMatch(matchCurrentEnv);
      context.setBreak(breakCurrentEnv);
    }
    return false;
  }
}
