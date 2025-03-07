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

package org.apache.iotdb.db.storageengine.load.splitter;

import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.db.storageengine.dataregion.modification.ModEntry;
import org.apache.iotdb.db.storageengine.dataregion.modification.ModificationFile;

import org.apache.tsfile.utils.ReadWriteIOUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DeletionData implements TsFileData {
  private final ModEntry deletion;

  public DeletionData(ModEntry deletion) {
    this.deletion = deletion;
  }

  @Override
  public long getDataSize() {
    return deletion.serializedSize();
  }

  public void writeToModificationFile(ModificationFile modificationFile) throws IOException {
    modificationFile.write(deletion);
  }

  @Override
  public TsFileDataType getType() {
    return TsFileDataType.DELETION;
  }

  @Override
  public void serialize(DataOutputStream stream) throws IOException {
    ReadWriteIOUtils.write(getType().ordinal(), stream);
    deletion.serialize(stream);
  }

  public static DeletionData deserialize(InputStream stream)
      throws IllegalPathException, IOException {
    return new DeletionData(ModEntry.createFrom(new DataInputStream(stream)));
  }
}
