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
package com.huyu.dynamic;

import com.huyu.dynamic.context.IMContext;
import com.huyu.dynamic.node.IMSqlNode;

import java.util.Map;

public class CreateSql {

  private final IMSqlNode root;

  // 是否是动态sql,动态sql会解析 ${},非动态sql,只会解析 #{}
  private final boolean isDynamic;

  public CreateSql(IMSqlNode sqlNode, boolean isDynamic) {
    this.root = sqlNode;
    this.isDynamic = isDynamic;
  }

  /**
   * @param paramMap
   *
   * @return
   */
  public String createSql(Map<String, Object> paramMap) {
    IMContext context = new IMContext(isDynamic, paramMap);
    root.apply(context);
    return context.getText();
  }
}
