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
 * Handles processeing for the LOGIN imap command.
 *
 * @version $Revision: 109034 $
 */
class LoginCommand extends NonAuthenticatedStateCommand
{
    public static final String NAME = "LOGIN";
    public static final String ARGS = "<userid> <password>";

    private final LoginCommandParser parser = new LoginCommandParser(this);
     
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

    protected AbstractImapCommandMessage decode(ImapRequestLineReader request, String tag) throws ProtocolException {
        final AbstractImapCommandMessage result = parser.decode(request, tag);
        return result;
    }
    
    private static class LoginCommandParser extends CommandParser {

        public LoginCommandParser(ImapCommand command) {
            super(command);
        }

        protected AbstractImapCommandMessage decode(ImapCommand command, ImapRequestLineReader request, String tag) throws ProtocolException {
            final String userid = astring( request );
            final String password = astring( request );
            endLine( request );
            final LoginCommandMessage result = new LoginCommandMessage(command, userid, password, tag);
            return result;
        }
        
    }
}

/*
6.2.2.  LOGIN Command

   Arguments:  user name
               password

   Responses:  no specific responses for this command

   Result:     OK - login completed, now in authenticated state
               NO - login failure: user name or password rejected
               BAD - command unknown or arguments invalid

      The LOGIN command identifies the client to the server and carries
      the plaintext password authenticating this user.

   Example:    C: a001 LOGIN SMITH SESAME
               S: a001 OK LOGIN completed
*/
