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
 * Handles processing for the CHECK imap command.
 *
 * @version $Revision: 109034 $
 */
class CheckCommand extends SelectedStateCommand
{
    public static final String NAME = "CHECK";
    public static final String ARGS = null;

    private final CheckCommandParser parser = new CheckCommandParser(this);
    
    protected AbstractImapCommandMessage decode(ImapRequestLineReader request, String tag) throws ProtocolException {
        final AbstractImapCommandMessage result = parser.decode(request, tag);
        return result;
    }

    /** @see ImapCommand#getName */
    public String getName()
    {
        return NAME;
    }

    /** @see CommandTemplate#getArgSyntax */
    public String getArgSyntax()
    {
        return ARGS;
    }
    
    private static class CheckCommandParser extends CommandParser {

        public CheckCommandParser(ImapCommand command) {
            super(command);
        }

        protected AbstractImapCommandMessage decode(ImapCommand command, ImapRequestLineReader request, String tag) throws ProtocolException {
            endLine( request );
            final CompleteCommandMessage result = new CompleteCommandMessage(command, false, tag);
            return result;
        }
        
    }
}

/*
   6.4.1.  CHECK Command

   Arguments:  none

   Responses:  no specific responses for this command

   Result:     OK - check completed
               BAD - command unknown or arguments invalid

      The CHECK command requests a checkpoint of the currently selected
      mailbox.  A checkpoint refers to any implementation-dependent
      housekeeping associated with the mailbox (e.g. resolving the
      server's in-memory state of the mailbox with the state on its
      disk) that is not normally executed as part of each command.  A
      checkpoint MAY take a non-instantaneous amount of real time to
      complete.  If a server implementation has no such housekeeping
      considerations, CHECK is equivalent to NOOP.

      There is no guarantee that an EXISTS untagged response will happen
      as a result of CHECK.  NOOP, not CHECK, SHOULD be used for new
      mail polling.

   Example:    C: FXXZ CHECK
               S: FXXZ OK CHECK Completed
*/
