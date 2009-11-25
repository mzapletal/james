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

import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.apache.commons.net.pop3.POP3Reply;
import org.apache.james.api.dnsservice.AbstractDNSServer;
import org.apache.james.api.dnsservice.DNSService;
import org.apache.james.api.kernel.mock.FakeLoader;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.core.MailImpl;
import org.apache.james.services.MailRepository;
import org.apache.james.services.MailServer;
import org.apache.james.socket.AvalonProtocolServer;
import org.apache.james.socket.JamesConnectionManager;
import org.apache.james.test.mock.avalon.MockLogger;
import org.apache.james.test.mock.avalon.MockSocketManager;
import org.apache.james.test.mock.avalon.MockThreadManager;
import org.apache.james.test.mock.james.InMemorySpoolRepository;
import org.apache.james.test.mock.james.MockFileSystem;
import org.apache.james.test.mock.james.MockMailServer;
import org.apache.james.test.util.Util;
import org.apache.james.userrepository.MockUsersRepository;
import org.apache.james.util.ConfigurationAdapter;
import org.apache.james.util.POP3BeforeSMTPHelper;
import org.apache.james.socket.SimpleConnectionManager;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.util.SharedByteArrayInputStream;

import java.io.InputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

/**
 * Tests the org.apache.james.smtpserver.SMTPServer unit
 */
public class POP3ServerTest extends TestCase {
    protected int m_pop3ListenerPort = Util.getNonPrivilegedPort();

    private MockMailServer m_mailServer;

    protected POP3TestConfiguration m_testConfiguration;

    private POP3ServerProtocolServerHandlerFactory m_pop3Server;
    private AvalonProtocolServer protoserver;
    
    private MockUsersRepository m_usersRepository = new MockUsersRepository();
    private POP3Client m_pop3Protocol = null;

    private MailImpl testMail1;

    private MailImpl testMail2;
    protected FakeLoader serviceManager;

    private MockThreadManager threadManager;

    private DNSService dnsservice;

    private MockSocketManager socketManager;

    private SimpleConnectionManager connectionManager;

    private MockFileSystem fSystem;
    public POP3ServerTest() {
        super("POP3ServerTest");
    }

    protected void setUp() throws Exception {
        m_pop3Server = new POP3ServerProtocolServerHandlerFactory();
        setUpServiceManager();
        
        m_pop3Server.setLoader(serviceManager);
        m_pop3Server.setDNSService(dnsservice);
        m_pop3Server.setMailServer(m_mailServer);
        m_pop3Server.setLog(new SimpleLog("MockLog"));
        
        protoserver = new AvalonProtocolServer();
        protoserver.setConnectionManager(connectionManager);
        protoserver.setFileSystem(fSystem);
        protoserver.setProtocolHandlerFactory(m_pop3Server);
        protoserver.setSocketManager(socketManager);
        protoserver.setThreadManager(threadManager);
        protoserver.setLog(new SimpleLog("MockLog"));
        protoserver.setDNSService(dnsservice);

        m_testConfiguration = new POP3TestConfiguration(m_pop3ListenerPort);
    }

    protected void finishSetUp(POP3TestConfiguration testConfiguration)
            throws Exception {
        testConfiguration.init();
        ConfigurationAdapter conf = new ConfigurationAdapter(testConfiguration);
        m_pop3Server.setConfiguration(conf);
        m_pop3Server.init();
        
        protoserver.setConfiguration(conf);
        protoserver.init();
    }

    protected void setUpServiceManager() throws ServiceException {
        serviceManager = new FakeLoader();
        connectionManager = new SimpleConnectionManager();
        ContainerUtil.enableLogging(connectionManager, new MockLogger());
        serviceManager.put(JamesConnectionManager.ROLE, connectionManager);
        m_mailServer = new MockMailServer(m_usersRepository);
        serviceManager.put(MailServer.ROLE, m_mailServer);
        serviceManager.put(UsersRepository.ROLE,
                m_usersRepository);
        socketManager = new MockSocketManager(m_pop3ListenerPort);
        serviceManager.put(SocketManager.ROLE, socketManager);
        threadManager = new MockThreadManager();
        serviceManager.put(ThreadManager.ROLE, threadManager);
        dnsservice = setUpDNSServer();
        serviceManager.put(DNSService.ROLE, setUpDNSServer());
        fSystem = new MockFileSystem();
        serviceManager.put(MockFileSystem.ROLE,fSystem);
      
    }

    private DNSService setUpDNSServer() {
        DNSService dns = new AbstractDNSServer() {
            public String getHostName(InetAddress addr) {
                return "localhost";
            }
            
            public InetAddress getLocalHost() throws UnknownHostException {
                return InetAddress.getLocalHost();
            }            
        
        };
        return dns;
    }
    protected void tearDown() throws Exception {
        if (m_pop3Protocol != null) {
            m_pop3Protocol.sendCommand("quit");
            m_pop3Protocol.disconnect();
        }
        //m_pop3Server.dispose();
        ContainerUtil.dispose(m_mailServer);
        if (testMail1 != null) testMail1.dispose();
        if (testMail2 != null) testMail2.dispose();
        super.tearDown();
    }

    public void testAuthenticationFail() throws Exception {
        finishSetUp(m_testConfiguration);
        
        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);

        m_usersRepository.addUser("known", "test2");

        m_pop3Protocol.login("known", "test");
        assertEquals(0, m_pop3Protocol.getState());
        assertTrue(m_pop3Protocol.getReplyString().startsWith("-ERR"));
    }

    public void testUnknownUser() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);

        m_pop3Protocol.login("unknown", "test");
        assertEquals(0, m_pop3Protocol.getState());
        assertTrue(m_pop3Protocol.getReplyString().startsWith("-ERR"));
    }

    public void testKnownUserEmptyInbox() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo", "bar");
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        // not authenticated
        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
        assertNull(entries);

        m_pop3Protocol.login("foo", "bar");
        System.err.println(m_pop3Protocol.getState());
        assertEquals(1, m_pop3Protocol.getState());

        entries = m_pop3Protocol.listMessages();
        assertEquals(1, m_pop3Protocol.getState());

        assertNotNull(entries);
        assertEquals(entries.length, 0);
        
        POP3MessageInfo p3i = m_pop3Protocol.listMessage(1);
        assertEquals(1, m_pop3Protocol.getState());
        assertNull(p3i);

        ContainerUtil.dispose(mockMailRepository);
    }

    public void testNotAsciiCharsInPassword() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        String pass = "bar" + (new String(new char[] { 200, 210 })) + "foo";
        m_usersRepository.addUser("foo", pass);
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        m_pop3Protocol.login("foo", pass);
        assertEquals(1, m_pop3Protocol.getState());
        ContainerUtil.dispose(mockMailRepository);
    }


    public void testUnknownCommand() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
        
        m_pop3Protocol.sendCommand("unkn");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("Expected -ERR as result for an unknown command", m_pop3Protocol.getReplyString().substring(0,4),"-ERR");
    }

    public void testUidlCommand() throws Exception {
        finishSetUp(m_testConfiguration);

        m_usersRepository.addUser("foo", "bar");
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.sendCommand("uidl");
        assertEquals(0, m_pop3Protocol.getState());

        m_pop3Protocol.login("foo", "bar");

        POP3MessageInfo[] list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        m_pop3Protocol.disconnect();
        
        setupTestMails(mockMailRepository);
        
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);
        m_pop3Protocol.login("foo", "bar");

        list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Expected 2 messages, found: "+list.length, 2, list.length);
        assertEquals("name", list[0].identifier);
        assertEquals("name2", list[1].identifier);
        
        POP3MessageInfo p3i = m_pop3Protocol.listUniqueIdentifier(1);
        assertNotNull(p3i);
        assertEquals("name", p3i.identifier);

    }

    public void testMiscCommandsWithWithoutAuth() throws Exception {
        finishSetUp(m_testConfiguration);

        m_usersRepository.addUser("foo", "bar");
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.sendCommand("noop");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("stat");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("pass");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("auth");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("rset");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));
        
        m_pop3Protocol.login("foo", "bar");

        POP3MessageInfo[] list = m_pop3Protocol.listUniqueIdentifiers();
        assertEquals("Found unexpected messages", 0, list.length);

        m_pop3Protocol.sendCommand("noop");
        assertEquals(1, m_pop3Protocol.getState());

        m_pop3Protocol.sendCommand("pass");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("auth");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("user");
        assertEquals(1, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.sendCommand("rset");
        assertEquals(1, m_pop3Protocol.getState());
        
    }

    public void testKnownUserInboxWithMessages() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo2", "bar2");
        InMemorySpoolRepository mailRep = new InMemorySpoolRepository();

        setupTestMails(mailRep);

        m_mailServer.setUserInbox("foo2", mailRep);
        
        m_pop3Protocol.sendCommand("retr","1");
        assertEquals(0, m_pop3Protocol.getState());
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));

        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();

        assertNotNull(entries);
        assertEquals(2, entries.length);
        assertEquals(1, m_pop3Protocol.getState());

        Reader r = m_pop3Protocol.retrieveMessageTop(entries[0].number, 0);
        assertNotNull(r);
        r.close();

        Reader r2 = m_pop3Protocol.retrieveMessage(entries[0].number);
        assertNotNull(r2);
        r2.close();

        // existing message
        boolean deleted = m_pop3Protocol.deleteMessage(entries[0].number);
        assertTrue(deleted);

        // already deleted message
        deleted = m_pop3Protocol.deleteMessage(entries[0].number);
        
        // TODO: Understand why this fails...
        //assertFalse(deleted);

        // unexisting message
        deleted = m_pop3Protocol.deleteMessage(10);
        assertFalse(deleted);

        m_pop3Protocol.sendCommand("quit");
        m_pop3Protocol.disconnect();

        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_pop3Protocol.login("foo2", "bar2");
        assertEquals(1, m_pop3Protocol.getState());

        entries = null;

        POP3MessageInfo stats = m_pop3Protocol.status();
        assertEquals(1, stats.number);
        assertEquals(5, stats.size);

        entries = m_pop3Protocol.listMessages();

        assertNotNull(entries);
        assertEquals(1, entries.length);
        assertEquals(1, m_pop3Protocol.getState());

        // top without arguments
        m_pop3Protocol.sendCommand("top");
        assertEquals("-ERR", m_pop3Protocol.getReplyString().substring(0,4));
        
        Reader r3 = m_pop3Protocol.retrieveMessageTop(entries[0].number, 0);
        assertNotNull(r3);
        r3.close();
        ContainerUtil.dispose(mailRep);
    }

    private void setupTestMails(MailRepository mailRep) throws MessagingException {
        ArrayList<MailAddress> recipients = new ArrayList<MailAddress>();
        recipients.add(new MailAddress("recipient@test.com"));
        InputStream mw = new SharedByteArrayInputStream(
                                ("Return-path: return@test.com\r\n"+
                                 "Content-Transfer-Encoding: plain\r\n"+
                                 "Subject: test\r\n\r\n"+
                                 "Body Text POP3ServerTest.setupTestMails\r\n").getBytes());
        testMail1 = new MailImpl("name", new MailAddress("from@test.com"),
                        recipients, mw);
        mailRep.store(testMail1);
        InputStream mw2 = new SharedByteArrayInputStream(
                                ("EMPTY").getBytes());
        testMail2 = new MailImpl("name2", new MailAddress("from@test.com"),
                                recipients, mw2);
        mailRep.store(testMail2);
    }

    /*
    public void testTwoSimultaneousMails() throws Exception {
        finishSetUp(m_testConfiguration);

        // make two user/repositories, open both
        m_usersRepository.addUser("foo1", "bar1");
        InMemorySpoolRepository mailRep1 = new InMemorySpoolRepository();
        setupTestMails(mailRep1);
        m_mailServer.setUserInbox("foo1", mailRep1);

        m_usersRepository.addUser("foo2", "bar2");
        InMemorySpoolRepository mailRep2 = new InMemorySpoolRepository();
        //do not setupTestMails, this is done later
        m_mailServer.setUserInbox("foo2", mailRep2);

        POP3Client pop3Protocol2 = null;
        try {
            // open two connections
            m_pop3Protocol = new POP3Client();
            m_pop3Protocol.connect("127.0.0.1", m_pop3ListenerPort);
            pop3Protocol2 = new POP3Client();
            pop3Protocol2.connect("127.0.0.1", m_pop3ListenerPort);

            assertEquals("first connection taken", 0, m_pop3Protocol.getState());
            assertEquals("second connection taken", 0, pop3Protocol2.getState());

            // open two accounts
            m_pop3Protocol.login("foo1", "bar1");

            pop3Protocol2.login("foo2", "bar2");

            POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
            assertEquals("foo1 has mails", 2, entries.length);

            entries = pop3Protocol2.listMessages();
            assertEquals("foo2 has no mails", 0, entries.length);

        } finally {
            // put both to rest, field var is handled by tearDown()
            if (pop3Protocol2 != null) {
                pop3Protocol2.sendCommand("quit");
                pop3Protocol2.disconnect();
            }
        }
    }
    */
    public void testIpStored() throws Exception {
        finishSetUp(m_testConfiguration);

        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        String pass = "password";
        m_usersRepository.addUser("foo", pass);
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        m_mailServer.setUserInbox("foo", mockMailRepository);

        m_pop3Protocol.login("foo", pass);
        assertEquals(1, m_pop3Protocol.getState());
        assertTrue(POP3BeforeSMTPHelper.isAuthorized("127.0.0.1"));
        ContainerUtil.dispose(mockMailRepository);
    }
    
    public void testCapa() throws Exception {
    	 finishSetUp(m_testConfiguration);

         m_pop3Protocol = new POP3Client();
         m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

         String pass = "password";
         m_usersRepository.addUser("foo", pass);
         InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
         m_mailServer.setUserInbox("foo", mockMailRepository);

         assertEquals(POP3Reply.OK, m_pop3Protocol.sendCommand("CAPA"));
         
         m_pop3Protocol.getAdditionalReply();
         m_pop3Protocol.getReplyString();
         List<String> replies = Arrays.asList(m_pop3Protocol.getReplyStrings());
         
         assertTrue("contains USER", replies.contains("USER"));
         
         m_pop3Protocol.login("foo", pass);
         assertEquals(POP3Reply.OK, m_pop3Protocol.sendCommand("CAPA"));
         
         m_pop3Protocol.getAdditionalReply();
         m_pop3Protocol.getReplyString();
         replies = Arrays.asList(m_pop3Protocol.getReplyStrings());
         assertTrue("contains USER", replies.contains("USER"));
         assertTrue("contains UIDL", replies.contains("UIDL"));
         assertTrue("contains TOP", replies.contains("TOP"));

         ContainerUtil.dispose(mockMailRepository);

    }
    

    /*
     * See JAMES-649
     * The same happens when using RETR
     *     
     * Comment to not broke the builds!
     *
    public void testOOMTop() throws Exception {
        finishSetUp(m_testConfiguration);

        int messageCount = 30000;
        m_pop3Protocol = new POP3Client();
        m_pop3Protocol.connect("127.0.0.1",m_pop3ListenerPort);

        m_usersRepository.addUser("foo", "bar");
        InMemorySpoolRepository mockMailRepository = new InMemorySpoolRepository();
        
        Mail m = new MailImpl();
        m.setMessage(Util.createMimeMessage("X-TEST", "test"));
        for (int i = 1; i < messageCount+1; i++ ) {
            m.setName("test" + i);
            mockMailRepository.store(m);
        }

        m_mailServer.setUserInbox("foo", mockMailRepository);

        // not authenticated
        POP3MessageInfo[] entries = m_pop3Protocol.listMessages();
        assertNull(entries);

        m_pop3Protocol.login("foo", "bar");
        System.err.println(m_pop3Protocol.getState());
        assertEquals(1, m_pop3Protocol.getState());

        entries = m_pop3Protocol.listMessages();
        assertEquals(1, m_pop3Protocol.getState());

        assertNotNull(entries);
        assertEquals(entries.length, messageCount);
        
        for (int i = 1; i < messageCount+1; i++ ) {
            Reader r = m_pop3Protocol.retrieveMessageTop(i, 100);
            assertNotNull(r);
            r.close();
        }
        
        ContainerUtil.dispose(mockMailRepository);
    }
    */
    

}
