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
package com.huyu.dynamic.parse;

import com.huyu.dynamic.CreateSql;
import com.huyu.dynamic.node.*;
import com.huyu.dynamic.utils.IMStrUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.parsing.XNode;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class IMXmlScriptBuilder {
  /**
   * 抽象出来了一个xml的节点 (获取节点的属性(指定类型))
   */
  private final XNode context;

  /**
   * 是否是动态sql
   */
  private boolean isDynamic;

  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public IMXmlScriptBuilder(XNode context) {
    this.context = context;
    initNodeHandlerMap();
  }

  private void initNodeHandlerMap() {
    // nodeHandlerMap.put("trim", new TrimHandler());
    // nodeHandlerMap.put("where", new WhereHandler());
    // nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
    nodeHandlerMap.put("else-if", new ElseIfHandler());
    nodeHandlerMap.put("else", new ElseHandler());
  }

  public CreateSql parseScriptNode() {
    IMSqlNode rootSqlNode = parseDynamicTags(context);
    return new CreateSql(rootSqlNode, isDynamic);
  }

  /**
   * 解析动态标签,每到下一层就相当于递归
   *
   * @param node
   *
   * @return
   */
  protected IMSqlNode parseDynamicTags(XNode node) {
    List<IMSqlNode> contents = new ArrayList<>();
    // 获取子node,有多种类型, 我们只要 文本节点 和 标签节点
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      // 获取子节点中的一个节点
      XNode child = node.newXNode(children.item(i));
      // 文本节点和
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        String data = child.getStringBody("");
        // 移除多余的空格
        data = IMStrUtils.removeExtraWhitespaces(data);
        if (!"".equals(data.trim())) {
          IMTextSqlNode textSqlNode = new IMTextSqlNode(data);
          // 动态sql
          isDynamic = textSqlNode.isDynamic();
          // 具有 #{} 会替代成 ?
          boolean isPrepared = textSqlNode.isPrepared();
          if (isDynamic || isPrepared) {
            IMSqlNode lastNode;
            if (!contents.isEmpty() && ((lastNode = contents.get(contents.size() - 1)) instanceof IMTextSqlNode)) {
              ((IMTextSqlNode) lastNode).appendText(data);
            } else {
              contents.add(textSqlNode);
            }
          } else {
            // 纯静态
            IMSqlNode lastNode;
            if (!contents.isEmpty() && ((lastNode = contents.get(contents.size() - 1)) instanceof IMStatisticSqlNode)) {
              ((IMStatisticSqlNode) lastNode).append(data);
            } else {
              contents.add(new IMStatisticSqlNode(data));
            }
          }
        }
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler == null) {
          throw new RuntimeException("不合法的标签");
        }
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    if (contents.size() == 1) {
      return contents.get(0);
    }
    return new IMMixedSqlNode(contents);
  }

  /**
   * 是否具有bind标签的节点
   *
   * @param mixedSqlNode
   *
   * @return
   */
  private boolean containsBindNode(IMSqlNode mixedSqlNode) {
    if (mixedSqlNode instanceof IMBindSqlNode) {
      return true;
    }
    if (mixedSqlNode instanceof IMMixedSqlNode) {
      List<IMSqlNode> contents = ((IMMixedSqlNode) mixedSqlNode).getContents();
      for (IMSqlNode content : contents) {
        if (content instanceof IMBindSqlNode) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 动态标签的处理
   */
  private interface NodeHandler {
    /**
     * @param nodeToHandle
     *          当前动态标签的node
     * @param targetContents
     *          上下文(运行的上下文)
     */
    void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents);
  }

  private static class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      final String name = nodeToHandle.getStringAttribute("name");
      final String expression = nodeToHandle.getStringAttribute("value");
      final IMBindSqlNode node = new IMBindSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      // MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // String prefix = nodeToHandle.getStringAttribute("prefix");
      // String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      // String suffix = nodeToHandle.getStringAttribute("suffix");
      // String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      // TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix,
      // suffixOverrides);
      // targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      // MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      // targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      // MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      //// SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      // targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      IMSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String collection = nodeToHandle.getStringAttribute("collection");
      Boolean nullable = nodeToHandle.getBooleanAttribute("nullable");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      IMForSqlNode forEachSqlNode = new IMForSqlNode(mixedSqlNode, collection, nullable, index, item, open, close,
          separator);
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      IMSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String test = nodeToHandle.getStringAttribute("test");
      IMIfSqlNode ifSqlNode = new IMIfSqlNode(mixedSqlNode, test, containsBindNode(mixedSqlNode));
      targetContents.add(ifSqlNode);
    }
  }

  private class ElseIfHandler implements NodeHandler {

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      // 看看前一个节点是否是if节点
      IMSqlNode preSqlNode = targetContents.get(targetContents.size() - 1);
      if (!(preSqlNode instanceof IMIfSqlNode)) {
        throw new RuntimeException("else-if标签前面没有if标签");
      }
      IMSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);

      String test = nodeToHandle.getStringAttribute("test");
      IMIfSqlNode ifSqlNode = new IMIfSqlNode(mixedSqlNode, test, containsBindNode(mixedSqlNode));
      ((IMIfSqlNode) preSqlNode).setDefaultSqlNode(ifSqlNode);
    }
  }

  private class ElseHandler implements NodeHandler {

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      IMSqlNode preSqlNode = targetContents.get(targetContents.size() - 1);
      if (!(preSqlNode instanceof IMIfSqlNode)) {
        // 抛出异常
        throw new RuntimeException("else标签前面没有if或者else-if标签");
      }
      IMSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // IMSqlNode sqlNode = new IMDefaultSqlNode(mixedSqlNode, containsBindNode(mixedSqlNode));
      ((IMIfSqlNode) preSqlNode).setDefaultSqlNode(mixedSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      IMSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<IMSqlNode> targetContents) {
      // 类似于Java语言的switch语句
      // 类似于case
      List<IMSqlNode> whenSqlNodes = new ArrayList<>();
      // 类似于default
      List<IMSqlNode> otherwiseSqlNodes = new ArrayList<>();

      // case 和 default 都是一个标签
      // 相当于又得获取子标签
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);

      // 获取默认的标签
      IMSqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);

      // case集合 和 默认值处理
      IMChooseSqlNode chooseSqlNode = new IMChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    /**
     * @param chooseSqlNode
     * @param ifSqlNodes
     * @param defaultSqlNodes
     */
    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<IMSqlNode> ifSqlNodes,
        List<IMSqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();

      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    /**
     * @param defaultSqlNodes
     *
     * @return
     */
    private IMSqlNode getDefaultSqlNode(List<IMSqlNode> defaultSqlNodes) {
      IMSqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }
}
