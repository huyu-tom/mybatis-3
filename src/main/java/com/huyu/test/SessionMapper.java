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
package com.huyu.test;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SessionMapper {

  /**
   * 更新session
   *
   * @param PO
   *
   * @return
   */
  Integer updateSessionById(@Param(value = "PO") SessionPO PO, @Param(value = "list") List<SessionPO> sessionPOS);

  /**
   * getMap
   *
   * @param ids
   *
   * @return
   */
  List<WaybillPO> getMap(@Param("ids") List<Long> ids);
}
