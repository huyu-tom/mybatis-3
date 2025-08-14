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

import java.sql.Timestamp;

public class SessionPO {
  private Long id;
  private Long userId;
  private Integer sessionType;
  private String title;
  private Timestamp createTime;
  private Timestamp updateTime;
  private Integer delIs;

  public Long getId() {
    return id;
  }

  public SessionPO setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getUserId() {
    return userId;
  }

  public SessionPO setUserId(Long userId) {
    this.userId = userId;
    return this;
  }

  public Integer getSessionType() {
    return sessionType;
  }

  public SessionPO setSessionType(Integer sessionType) {
    this.sessionType = sessionType;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public SessionPO setTitle(String title) {
    this.title = title;
    return this;
  }

  public Timestamp getCreateTime() {
    return createTime;
  }

  public SessionPO setCreateTime(Timestamp createTime) {
    this.createTime = createTime;
    return this;
  }

  public Timestamp getUpdateTime() {
    return updateTime;
  }

  public SessionPO setUpdateTime(Timestamp updateTime) {
    this.updateTime = updateTime;
    return this;
  }

  public Integer getDelIs() {
    return delIs;
  }

  public SessionPO setDelIs(Integer delIs) {
    this.delIs = delIs;
    return this;
  }
}
