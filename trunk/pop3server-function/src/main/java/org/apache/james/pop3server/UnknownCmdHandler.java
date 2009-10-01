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


/**
  * Default command handler for handling unknown commands
  */
public class UnknownCmdHandler implements CommandHandler {
    /**
     * The name of the command handled by the command handler
     */
    public static final String COMMAND_NAME = "UNKNOWN";


    /**
     * Handler method called upon receipt of an unrecognized command.
     * Returns an error response and logs the command.    
     *
     * @see org.apache.james.pop3server.CommandHandler#onCommand(org.apache.james.pop3server.POP3Session, java.lang.String, java.lang.String)
     */
    public POP3Response onCommand(POP3Session session, String command, String parameters) {
        return new POP3Response(POP3Response.ERR_RESPONSE);
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
