# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
from iotdb.thrift.common.ttypes import TEndPoint, TSStatus


class IoTDBConnectionException(Exception):
    def __init__(self, reason=None, cause=None, message=None):
        if reason is not None:
            super().__init__(reason)
        elif cause is not None:
            super().__init__(cause)
        elif message is not None and cause is not None:
            super().__init__(message, cause)
        else:
            super().__init__()


class StatementExecutionException(Exception):
    def __init__(self, status: TSStatus = None, message=None):
        if status is not None:
            super().__init__(f"{status.code}: {status.message}")
        elif message is not None:
            super().__init__(message)
        else:
            super().__init__()


class RedirectException(Exception):
    def __init__(self, redirect_info):
        Exception.__init__(self)
        if isinstance(redirect_info, TEndPoint):
            self.redirect_node = redirect_info
        else:
            self.device_to_endpoint = redirect_info
