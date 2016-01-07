/*
 * Copyright (c) 2010-2016 SnappyData, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package io.snappydata

import com.pivotal.gemfirexd.internal.iapi.tools.i18n.LocalizedResource

/**
 * Created by soubhikc on 6/10/15.
 */
object LocalizedMessages {

  private val MESSAGE_FILE = "i18n.snappymessages"

  val res = new LocalizedResource(null, null, MESSAGE_FILE)

}
