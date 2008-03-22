/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

/**
 * 
 */
package org.apache.james.imapserver.codec.decode.imap4rev1;

import org.apache.avalon.framework.logger.Logger;

final class MockLogger implements Logger {
    public void debug(String arg0) {
    }

    public void debug(String arg0, Throwable arg1) {
    }

    public void error(String arg0) {
    }

    public void error(String arg0, Throwable arg1) {
    }

    public void fatalError(String arg0) {
    }

    public void fatalError(String arg0, Throwable arg1) {
    }

    public Logger getChildLogger(String arg0) {
        return null;
    }

    public void info(String arg0) {
    }

    public void info(String arg0, Throwable arg1) {
    }

    public boolean isDebugEnabled() {
        return false;
    }

    public boolean isErrorEnabled() {
        return false;
    }

    public boolean isFatalErrorEnabled() {
        return false;
    }

    public boolean isInfoEnabled() {
        return false;
    }

    public boolean isWarnEnabled() {
        return false;
    }

    public void warn(String arg0) {
        
    }

    public void warn(String arg0, Throwable arg1) {
        
    }
}