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



package org.apache.james.pop3server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;

/**
  * Handles STAT command
  */
public class StatCmdHandler implements CommandHandler {
	private final static String COMMAND_NAME = "STAT";

	/**
     * Handler method called upon receipt of a STAT command.
     * Returns the number of messages in the mailbox and its
     * aggregate size.
     *
  	 * @see org.apache.james.pop3server.CommandHandler#onCommand(org.apache.james.pop3server.POP3Session, java.lang.String, java.lang.String)
	 */
    public POP3Response onCommand(POP3Session session, String command, String parameters) {
        POP3Response response = null;
        if (session.getHandlerState() == POP3Handler.TRANSACTION) {
            long size = 0;
            int count = 0;
            try {
                for (Mail mc: session.getUserMailbox()) {
                    if (mc != POP3Handler.DELETED) {
                        size += mc.getMessageSize();
                        count++;
                    }
                }
                StringBuilder responseBuffer =
                    new StringBuilder(32)
                            .append(count)
                            .append(" ")
                            .append(size);
                response = new POP3Response(POP3Response.OK_RESPONSE,responseBuffer.toString());
            } catch (MessagingException me) {
                response = new POP3Response(POP3Response.ERR_RESPONSE);
            }
        } else {
            response = new POP3Response(POP3Response.ERR_RESPONSE);
        }
        return response;
    }



    /**
     * @see org.apache.james.socket.CommonCommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        List<String> commands = new ArrayList<String>();
        commands.add(COMMAND_NAME);
        return commands;
    }

}
