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




package org.apache.james.smtpserver.protocol.core.fastfail;

import org.apache.james.dsn.DSNStatus;
import org.apache.james.smtpserver.protocol.SMTPRetCode;
import org.apache.james.smtpserver.protocol.SMTPSession;
import org.apache.james.smtpserver.protocol.hook.HookResult;
import org.apache.james.smtpserver.protocol.hook.HookReturnCode;
import org.apache.james.smtpserver.protocol.hook.RcptHook;
import org.apache.mailet.MailAddress;


/**
 * Handler which want todo an recipient check should extend this
 *
 */
public abstract class AbstractValidRcptHandler implements RcptHook {

    
    /**
     * @see org.apache.james.smtpserver.protocol.hook.RcptHook#doRcpt(org.apache.james.smtpserver.protocol.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        
        if (!session.isRelayingAllowed()) {
            if (isValidRecipient(session, rcpt) == false) {
                //user not exist
                session.getLogger().info("Rejected message. Unknown user: " + rcpt.toString());
                return new HookResult(HookReturnCode.DENY,SMTPRetCode.TRANSACTION_FAILED, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_MAILBOX) + " Unknown user: " + rcpt.toString());
            }
        } else {
            session.getLogger().debug("Sender allowed");
        }
        return new HookResult(HookReturnCode.DECLINED);
    }
    
  
    /**
     * Return true if email for the given recipient should get accepted
     * 
     * @param recipient
     * @return isValid
     */
    protected abstract boolean isValidRecipient(SMTPSession session, MailAddress recipient);
}