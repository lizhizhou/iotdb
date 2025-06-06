/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.schemaengine.template;

import org.apache.tsfile.utils.ReadWriteIOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public enum TemplateInternalRPCUpdateType {
  ROLLBACK_INVALIDATE_TEMPLATE_SET_INFO((byte) 0),
  INVALIDATE_TEMPLATE_SET_INFO((byte) 1),
  ADD_TEMPLATE_PRE_SET_INFO((byte) 2),
  COMMIT_TEMPLATE_SET_INFO((byte) 3),
  UPDATE_TEMPLATE_INFO((byte) 4);

  private final byte operationType;

  TemplateInternalRPCUpdateType(byte operationType) {
    this.operationType = operationType;
  }

  public byte toByte() {
    return operationType;
  }

  public void serialize(OutputStream stream) throws IOException {
    ReadWriteIOUtils.write(operationType, stream);
  }

  public static TemplateInternalRPCUpdateType deserialize(ByteBuffer buffer) {
    byte type = ReadWriteIOUtils.readByte(buffer);
    return getType(type);
  }

  public static TemplateInternalRPCUpdateType getType(byte type) {
    switch (type) {
      case 0:
        return ROLLBACK_INVALIDATE_TEMPLATE_SET_INFO;
      case 1:
        return INVALIDATE_TEMPLATE_SET_INFO;
      case 2:
        return ADD_TEMPLATE_PRE_SET_INFO;
      case 3:
        return COMMIT_TEMPLATE_SET_INFO;
      case 4:
        return UPDATE_TEMPLATE_INFO;
      default:
        throw new IllegalArgumentException("Unknown template update operation type" + type);
    }
  }
}
