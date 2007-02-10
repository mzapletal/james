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

package org.apache.james.imapserver.commands;

import org.apache.james.imapserver.ImapRequestLineReader;
import org.apache.james.imapserver.ProtocolException;


/**
 * @version $Revision: 109034 $
 */
class LsubCommand extends ListCommand
{
    public static final String NAME = "LSUB";

    private final LsubCommandParser parser = new LsubCommandParser(this);

    /** @see ImapCommand#getName */
    public String getName()
    {
        return NAME;
    }
    
    protected AbstractImapCommandMessage decode(ImapRequestLineReader request, String tag) throws ProtocolException {
        final AbstractImapCommandMessage result = parser.decode(request, tag);
        return result;
    }

    private class LsubCommandParser extends ListCommandParser {

        public LsubCommandParser(ImapCommand command) {
            super(command);
        }

        protected ListCommandMessage createMessage(ImapCommand command, String referenceName, String mailboxPattern, String tag) {
            final LsubListCommandMessage result = new LsubListCommandMessage(command, referenceName, mailboxPattern, tag);
            return result;
        }
    }
}
