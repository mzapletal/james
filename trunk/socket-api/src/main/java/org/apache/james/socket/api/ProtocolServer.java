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

package org.apache.james.socket.api;

/**
 * Each socket provider implements this interface. A ProtocolHandlerFactory can
 * retrieve transport informations using this interface
 */
public interface ProtocolServer {

    public boolean isEnabled();

    /**
     * Returns the server socket type, plain or SSL
     * 
     * @return String The socket type, plain or SSL
     */
    public String getSocketType();

    /**
     * Returns the port that the service is bound to
     * 
     * @return int The port number
     */
    public int getPort();

    /**
     * Returns the address if the network interface the socket is bound to
     * 
     * @return String The network interface name
     */
    public String getNetworkInterface();

}
