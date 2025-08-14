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

/**
 * 定义变量
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class VarDeclSqlNode implements SqlNode {

  // 变量名 Bind
  private final String name;

  // 表达式,从上下文中用该表达式获取值,并且将变量名为name的值设置为之前表达式获取的值
  private final String expression;

  public VarDeclSqlNode(String name, String exp) {
    this.name = name;
    this.expression = exp;
  }

  /**
   * @param context
   *
   * @return
   */
  @Override
  public boolean apply(DynamicContext context) {
    final Object value = OgnlCache.getValue(expression, context.getBindings());
    context.bind(name, value);
    return true;
  }
}
