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



package org.apache.james.remotemanager;

import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.james.api.dnsservice.AbstractDNSServer;
import org.apache.james.api.dnsservice.DNSService;
import org.apache.james.api.domainlist.ManageableDomainList;
import org.apache.james.api.domainlist.SimpleDomainList;
import org.apache.james.api.kernel.mock.FakeLoader;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.api.user.UsersStore;
import org.apache.james.api.vut.management.MockVirtualUserTableManagementService;
import org.apache.james.api.vut.management.VirtualUserTableManagementService;
import org.apache.james.management.BayesianAnalyzerManagementException;
import org.apache.james.management.BayesianAnalyzerManagementService;
import org.apache.james.management.DomainListManagementException;
import org.apache.james.management.DomainListManagementService;
import org.apache.james.management.ProcessorManagementService;
import org.apache.james.management.SpoolFilter;
import org.apache.james.management.SpoolManagementException;
import org.apache.james.management.SpoolManagementService;
import org.apache.james.services.MailServer;
import org.apache.james.socket.AvalonProtocolServer;
import org.apache.james.socket.JamesConnectionManager;
import org.apache.james.socket.SimpleConnectionManager;
import org.apache.james.test.mock.avalon.MockLogger;
import org.apache.james.test.mock.avalon.MockSocketManager;
import org.apache.james.test.mock.avalon.MockStore;
import org.apache.james.test.mock.avalon.MockThreadManager;
import org.apache.james.test.mock.james.MockFileSystem;
import org.apache.james.test.mock.james.MockMailServer;
import org.apache.james.test.mock.james.MockUsersStore;
import org.apache.james.test.mock.james.MockVirtualUserTableManagementImpl;
import org.apache.james.test.mock.james.MockVirtualUserTableStore;
import org.apache.james.test.util.Util;
import org.apache.james.userrepository.MockUsersRepository;
import org.apache.james.util.ConfigurationAdapter;
import org.apache.james.util.InternetPrintWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import junit.framework.TestCase;

/**
 * Tests the org.apache.james.remotemanager.RemoteManager
 * TODO: impl missing command tests for: 
 *       USER 
 *       SHUTDOWN (hard to test, because it does shutdown the whole (testing) JVM 
 */

public class RemoteManagerTest extends TestCase {
    
    protected int m_remoteManagerListenerPort = Util.getNonPrivilegedPort();
    protected RemoteManagerProtocolHandlerFactory handlerFactory;
    protected AvalonProtocolServer server;
    
    protected RemoteManagerTestConfiguration m_testConfiguration;
    protected String m_host = "127.0.0.1";
    protected BufferedReader m_reader;
    protected InternetPrintWriter m_writer;
    protected TelnetClient m_telnetClient;
    private MockUsersRepository m_mockUsersRepository;
    private MockMailServer mailServer;
    protected FakeLoader serviceManager;
    private SimpleConnectionManager connectionManager;
    private MockUsersStore usersStore;
    private MockSocketManager socketManager;
    private MockThreadManager threadManager;
    private DNSService dnsservice;
    private MockFileSystem filesystem;
    private MockVirtualUserTableManagementService vutManagement;
    private AvalonProtocolServer protoserver;
    
    protected void setUp() throws Exception {
        handlerFactory = new RemoteManagerProtocolHandlerFactory();
        setUpServiceManager();
        
        handlerFactory.setLoader(serviceManager);
        handlerFactory.setLog(new SimpleLog("MockLog"));
        handlerFactory.setMailServer(mailServer);
        handlerFactory.setDNSService(dnsservice);
        
        protoserver = new AvalonProtocolServer();
        protoserver.setLog(new SimpleLog("ProtoServerLog"));
        protoserver.setConnectionManager(connectionManager);
        protoserver.setFileSystem(filesystem);
        protoserver.setDNSService(dnsservice);
        protoserver.setProtocolHandlerFactory(handlerFactory);
        protoserver.setSocketManager(socketManager);
        protoserver.setThreadManager(threadManager);
        //ContainerUtil.service(m_remoteManager, serviceManager);
        m_testConfiguration = new RemoteManagerTestConfiguration(m_remoteManagerListenerPort);
    }

    protected void tearDown() throws Exception {
        ContainerUtil.dispose(mailServer);
        super.tearDown();
    }

    protected void finishSetUp(RemoteManagerTestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        try {
            ConfigurationAdapter conf = new ConfigurationAdapter(testConfiguration);
            protoserver.setConfiguration(conf);
            handlerFactory.setConfiguration(conf);
            handlerFactory.init();
            protoserver.init();
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void login() throws IOException {
        login(m_testConfiguration.getLoginName(), m_testConfiguration.getLoginPassword());
    }

    protected void login(String name, String password) throws IOException {
        sendCommand(name);
        List answers = readAnswer();
        String last = getLastLine(answers);
        assertTrue("Last line does not start with Password: "+last,last.startsWith("Password:"));
        sendCommand(password);
        answers = readAnswer();
        last = getLastLine(answers);
        assertTrue("Last line does not start with Welcome: "+last,last.startsWith("Welcome"));
    }

    protected String getLastLine(List list) {
        if (list == null || list.isEmpty()) return null;
        return (String)list.get(list.size()-1);
    }

    protected List readAnswer() {
        return readAnswer(1);
    }
    
    protected List readAnswer(int numLines) {
        List allAnswerLines = new ArrayList();
        try {
            if (numLines > 0) {
                for (int i = 0; i < numLines; i++) {
                    allAnswerLines.add(m_reader.readLine());
                }
            } else {
                String line = m_reader.readLine();
                allAnswerLines.add(line);
                
                while (m_reader.ready()) {
                    allAnswerLines.add(m_reader.readLine());
                }
            }
            return allAnswerLines;
        } catch (IOException e) {
            return null;
        }
    }

    protected void sendCommand(String command) throws IOException {
        m_writer.println(command);
        m_writer.flush();
    }

    protected void connect() throws IOException {
        m_telnetClient = new TelnetClient();
        m_telnetClient.connect(m_host, m_remoteManagerListenerPort);

        m_reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(m_telnetClient.getInputStream(), 1024), "ASCII"));
        m_writer = new InternetPrintWriter(new BufferedOutputStream(m_telnetClient.getOutputStream(), 1024), true);
        
        readAnswer(3);
    }

    protected void setUpServiceManager() throws ServiceException {
        serviceManager = new FakeLoader();
        connectionManager = new SimpleConnectionManager();
        ContainerUtil.enableLogging(connectionManager, new MockLogger());
        serviceManager.put(JamesConnectionManager.ROLE, connectionManager);
        
        m_mockUsersRepository = new MockUsersRepository();
       
        mailServer = new MockMailServer(m_mockUsersRepository);      
        usersStore = new MockUsersStore(m_mockUsersRepository);

        serviceManager.put(MailServer.ROLE, mailServer);
        serviceManager.put(UsersRepository.ROLE, m_mockUsersRepository);
        
        filesystem = new MockFileSystem();
        serviceManager.put(MockFileSystem.ROLE, filesystem);
        
        serviceManager.put(UsersStore.ROLE, usersStore);
        socketManager = new MockSocketManager(m_remoteManagerListenerPort);
        serviceManager.put(SocketManager.ROLE, socketManager);
        threadManager = new MockThreadManager();
        serviceManager.put(ThreadManager.ROLE, threadManager);
        
        dnsservice = setUpDNSServer();
        serviceManager.put(DNSService.ROLE, dnsservice);
        vutManagement = new MockVirtualUserTableManagementService();
        //VirtualUserTableManagementService vutManagement = new VirtualUserTableManagement();
        //vutManagement.setVirtualUserTableStore(vutStore);
        //vutManagement.setVirtualUserTableManagement(new MockVirtualUserTableManagementImpl());
        serviceManager.put(VirtualUserTableManagementService.ROLE, new MockVirtualUserTableManagementService());

            
       
        ManageableDomainList xml = new SimpleDomainList();
        
        DomainListManagementService domManagement = new DomainListManagementService() {

            private ManageableDomainList domainList;

            public boolean addDomain(String domain)
                    throws DomainListManagementException {
                return domainList.addDomain(domain);
            }

            public DomainListManagementService setDomainList(ManageableDomainList xml) {
                this.domainList = xml;
                return this;
            }

            public boolean containsDomain(String domain) {
                return domainList.containsDomain(domain);
            }

            public List getDomains() {
                return domainList.getDomains();
            }

            public boolean removeDomain(String domain)
                    throws DomainListManagementException {
                return domainList.removeDomain(domain);
            }
            
        }.setDomainList(xml);
        
        serviceManager.put(DomainListManagementService.ROLE, domManagement);
        serviceManager.put(BayesianAnalyzerManagementService.ROLE, new BayesianAnalyzerManagementService() {
            
            public void resetData() throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                
            }
            
            public void importData(String file)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                
            }
            
            public void exportData(String file)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                
            }
            
            public int addSpamFromMbox(String file)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public int addSpamFromDir(String dir)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public int addHamFromMbox(String file)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public int addHamFromDir(String dir)
                    throws BayesianAnalyzerManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
        });
        
        serviceManager.put(SpoolManagementService.ROLE, new SpoolManagementService() {
            
            public int resendSpoolItems(String spoolRepositoryURL, String key,
                    List lockingFailures, SpoolFilter filter)
                    throws MessagingException, SpoolManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public int removeSpoolItems(String spoolRepositoryURL, String key,
                    List lockingFailures, SpoolFilter filter)
                    throws MessagingException, SpoolManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public int moveSpoolItems(String srcSpoolRepositoryURL,
                    String dstSpoolRepositoryURL, String dstState, SpoolFilter filter)
                    throws MessagingException, SpoolManagementException {
                // TODO Auto-generated method stub
                return 0;
            }
            
            public List getSpoolItems(String spoolRepositoryURL, SpoolFilter filter)
                    throws MessagingException, SpoolManagementException {
                // TODO Auto-generated method stub
                return null;
            }
        });
        serviceManager.put(MockStore.ROLE,new MockStore());
        serviceManager.put(ProcessorManagementService.ROLE, new ProcessorManagementService() {
            
            public String[] getProcessorNames() {
                // TODO Auto-generated method stub
                return null;
            }
            
            public String[] getMatcherParameters(String processorName, int matcherIndex) {
                // TODO Auto-generated method stub
                return null;
            }
            
            public String[] getMatcherNames(String processorName) {
                // TODO Auto-generated method stub
                return null;
            }
            
            public String[] getMailetParameters(String processorName, int mailetIndex) {
                // TODO Auto-generated method stub
                return null;
            }
            
            public String[] getMailetNames(String processorName) {
                // TODO Auto-generated method stub
                return null;
            }
        });
    }
    
    private DNSService setUpDNSServer() {
        DNSService dns = new AbstractDNSServer() {
            public String getHostName(InetAddress addr) {
                return "localhost";
            }
        
            public InetAddress getLocalHost() throws UnknownHostException {
                return InetAddress.getLocalHost();
            }
            
            public InetAddress[] getAllByName(String name) throws UnknownHostException {
            return new InetAddress[]{InetAddress.getLocalHost()};
            }
        };
    
        return dns;
    }

    /*
    public void testCustomCommand() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("echo hsif eht lla rof sknaht");
        String lastLine = getLastLine(readAnswer());
        assertEquals("Arguments echoed", "hsif eht lla rof sknaht", lastLine);
    }
    */
    public void testLogin() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();

        login();
    }

    public void testWrongLoginUser() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();

        sendCommand("sindbad");
        List answers = readAnswer();
        sendCommand(m_testConfiguration.getLoginPassword());

        // we should receive the fail message and a new Login id.
        answers = readAnswer(2);
        String last = getLastLine(answers);
        assertTrue("Last line does not start with 'Login id:' but with '"+last+"'",last.startsWith("Login id:")); // login failed, getting new login prompt
    }

    public void testWrongLoginPassword() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();

        sendCommand(m_testConfiguration.getLoginName());
        List answers = readAnswer();
        sendCommand("getmethru");

        answers = readAnswer(2);
        String last = getLastLine(answers);
        assertTrue("Line does not start with 'Login id:' but with '"+last+"'", last.startsWith("Login id:")); // login failed, getting new login prompt
    }

    public void testUserCount() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("countusers");
        assertTrue(getLastLine(readAnswer()).endsWith(" 0")); // no user yet

        sendCommand("adduser testCount1 testCount");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("countusers");
        assertTrue(getLastLine(readAnswer()).endsWith(" 1")); // 1 total

        sendCommand("adduser testCount2 testCount");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("countusers");
        assertTrue(getLastLine(readAnswer()).endsWith(" 2")); // 2 total

        m_mockUsersRepository.removeUser("testCount1");

        sendCommand("countusers");
        assertTrue(getLastLine(readAnswer()).endsWith(" 1")); // 1 total
    }

    public void testAddUserAndVerify() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adduser testAdd test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("verify testNotAdded");
        assertTrue(getLastLine(readAnswer()).endsWith(" does not exist"));

        sendCommand("verify testAdd");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));

        sendCommand("deluser testAdd");
        readAnswer();

        sendCommand("verify testAdd");
        assertTrue(getLastLine(readAnswer()).endsWith(" does not exist"));
    }

    public void testDelUser() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adduser testDel test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("deluser testNotDeletable");
        assertTrue(getLastLine(readAnswer()).endsWith(" doesn't exist"));

        sendCommand("verify testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));

        sendCommand("deluser testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" deleted"));

        sendCommand("verify testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" does not exist"));
    }

    public void testQuit() throws Exception {
    
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("help");
        delay();
        assertTrue("command line is effective", readAnswer().size() > 0);
        
        sendCommand("quit");
        delay();
        assertTrue("",readAnswer(39).contains("Bye"));

        sendCommand("help");
        delay();
        assertNull("connection is closed", m_reader.readLine());
    }   
  

    public void testListUsers() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        String[] users = new String[] {"ccc", "aaa", "dddd", "bbbbb"};

        for (int i = 0; i < users.length; i++) {
            String user = users[i];
            sendCommand("adduser " + user + " test");
            readAnswer(1);
        }

        delay();

        sendCommand("listusers");
        List list = readAnswer(5);

        assertEquals("user count line", "Existing accounts " + users.length, list.get(0));
        
        List readUserNames = new ArrayList();
        for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            String answerLine = (String) iterator.next();
            if (!answerLine.startsWith("user: ")) continue;
            readUserNames.add(answerLine.substring(6));
        }
        assertEquals("user count", users.length, readUserNames.size());

        for (int i = 0; i < users.length; i++) {
            String user = users[i];
            assertTrue("name found", readUserNames.contains(user));
        }
    }

    private void delay() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            ; // ignore
        }
    }

    public void testCommandCaseInsensitive() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adduser testDel test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("verify testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));

        sendCommand("VERIFY testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));

        sendCommand("vErIfY testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));
    }

    public void testParameterCaseSensitive() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adduser testDel test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added")); // success

        sendCommand("verify testDel");
        assertTrue(getLastLine(readAnswer()).endsWith(" exists"));

        sendCommand("verify TESTDEL");
        assertTrue(getLastLine(readAnswer()).endsWith(" does not exist"));

        sendCommand("verify testdel");
        assertTrue(getLastLine(readAnswer()).endsWith(" does not exist"));
    }

    public void testAlias() throws Exception {
        m_mockUsersRepository.setForceUseJamesUser();
        finishSetUp(m_testConfiguration);
        connect();
        login();
        
        // do some tests when parameter users don't exist
        sendCommand("setalias testNonExist1 testNonExist2");
        assertTrue(getLastLine(readAnswer()).equals("No such user testNonExist1"));

        sendCommand("adduser testAlias1 test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added"));

        sendCommand("showalias testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("User testAlias1 does not currently have an alias"));

        sendCommand("setalias testAlias1 testNonExist2");
        assertTrue(getLastLine(readAnswer()).equals("Alias unknown to server - create that user first."));

        sendCommand("setalias testNonExist1 testAlias");
        assertTrue(getLastLine(readAnswer()).equals("No such user testNonExist1"));

        sendCommand("adduser testAlias2 test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added"));

        // regular alias
        sendCommand("setalias testAlias1 testAlias2");
        assertTrue(getLastLine(readAnswer()).equals("Alias for testAlias1 set to:testAlias2"));

        //TODO: is this correct? even primitive circular aliasing allowed!
        sendCommand("setalias testAlias2 testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("Alias for testAlias2 set to:testAlias1"));

        // did first one persist?
        sendCommand("showalias testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("Current alias for testAlias1 is: testAlias2"));

        //TODO: is this correct? setting self as alias!
        sendCommand("setalias testAlias1 testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("Alias for testAlias1 set to:testAlias1"));

        sendCommand("adduser testAlias3 test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added"));

        // re-set, simply overwrites
        sendCommand("setalias testAlias1 testAlias3");
        assertTrue(getLastLine(readAnswer()).equals("Alias for testAlias1 set to:testAlias3"));

        // check overwrite
        sendCommand("showalias testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("Current alias for testAlias1 is: testAlias3"));

        // retreat
        sendCommand("unsetalias testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("Alias for testAlias1 unset"));

        // check removed alias
        sendCommand("showalias testAlias1");
        assertTrue(getLastLine(readAnswer()).equals("User testAlias1 does not currently have an alias"));

    }

    public void testForward() throws Exception {
        m_mockUsersRepository.setForceUseJamesUser();
        finishSetUp(m_testConfiguration);
        connect();
        login();

        // do some tests when parameter users don't exist
        sendCommand("setforwarding testNonExist1 testForward1@locahost");
        assertTrue(getLastLine(readAnswer()).equals("No such user testNonExist1"));
        
        sendCommand("adduser testForwardUser test");
        assertTrue(getLastLine(readAnswer()).endsWith(" added"));

        sendCommand("showforwarding testForwardUser");
        assertTrue(getLastLine(readAnswer()).equals("User testForwardUser is not currently being forwarded"));

        sendCommand("setforwarding testForwardUser testForward1@locahost");
        assertTrue(getLastLine(readAnswer()).equals("Forwarding destination for testForwardUser set to:testForward1@locahost"));
        
        // did it persist?
        sendCommand("showforwarding testForwardUser");
        assertTrue(getLastLine(readAnswer()).equals("Current forwarding destination for testForwardUser is: testForward1@locahost"));

        // re-set, simply overwrites
        sendCommand("setforwarding testForwardUser testForward2@locahost");
        assertTrue(getLastLine(readAnswer()).equals("Forwarding destination for testForwardUser set to:testForward2@locahost"));

        // check overwrite
        sendCommand("showforwarding testForwardUser");
        assertTrue(getLastLine(readAnswer()).equals("Current forwarding destination for testForwardUser is: testForward2@locahost"));

        // retreat
        sendCommand("unsetforwarding testForwardUser");
        assertTrue(getLastLine(readAnswer()).equals("Forward for testForwardUser unset"));

        // check removed forward
        sendCommand("showforwarding testForwardUser");
        assertTrue(getLastLine(readAnswer()).equals("User testForwardUser is not currently being forwarded"));

    }

    public void testSetPassword() throws Exception {
        finishSetUp(m_testConfiguration);
        connect();
        login();

        String lastLine;
        
        sendCommand("adduser testPwdUser pwd1");
        lastLine = getLastLine(readAnswer());
        assertTrue(lastLine.endsWith(" added"));

        assertTrue("initial password", m_mockUsersRepository.test("testPwdUser", "pwd1"));
        
        sendCommand("setpassword testPwdUser     ");
        lastLine = getLastLine(readAnswer());
        assertTrue("password changed to empty: "+lastLine, m_mockUsersRepository.test("testPwdUser", "pwd1"));

        // change pwd
        sendCommand("setpassword testPwdUser pwd2");
        lastLine = getLastLine(readAnswer());
        assertTrue("password not changed to pwd2: "+lastLine, m_mockUsersRepository.test("testPwdUser", "pwd2"));
        
        // assure case sensitivity
        sendCommand("setpassword testPwdUser pWD2");
        lastLine = getLastLine(readAnswer());
        assertFalse("password not changed to pWD2: "+lastLine, m_mockUsersRepository.test("testPwdUser", "pwd2"));
        assertTrue("password not changed to pWD2: "+lastLine, m_mockUsersRepository.test("testPwdUser", "pWD2"));
        
    }
    
    public void testAddMapping() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();
        
        sendCommand("addmapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
        
        sendCommand("addmapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Not add mapping... allready exists",lastLine.endsWith("false"));
    }
    
    public void testRemoveMapping() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();
        
        sendCommand("addmapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
        
        sendCommand("removemapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("remove mapping",lastLine.endsWith("true"));
        
        sendCommand("removemapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Not remove mapping... mapping not exists",lastLine.endsWith("false"));
    }
    
    public void testListAllMappings() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("addmapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
        
        sendCommand("addmapping test2@test junit2");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
       
        sendCommand("listallmappings");
        List answer = readAnswer(3);
        assertTrue("Read first mapping", answer.get(1).toString().contains("junit"));
        assertTrue("Read second mapping line", answer.get(2).toString().contains("junit2"));
    }
    
    public void testListMapping() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("addmapping test@test junit");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
        
        sendCommand("addmapping test2@test junit2");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add mapping", lastLine.endsWith("true"));
       
        sendCommand("listmapping test@test");
        lastLine = readAnswer(2).get(1).toString();
        assertTrue("list mapping", lastLine.endsWith("junit"));
    }
    
    public void testaddDomain() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adddomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add domain", lastLine.endsWith("successful"));
       
        sendCommand("adddomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add domain which exists", lastLine.endsWith("fail"));
       
        sendCommand("listdomains");

        lastLine = readAnswer(2).get(1).toString();
        assertTrue("list domain", lastLine.endsWith("domain"));       
    }
    
    public void testremoveDomain() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adddomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add domain", lastLine.endsWith("successful"));
       
        sendCommand("removedomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Remove domain", lastLine.endsWith("successful"));
       
        sendCommand("removedomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Remove domain which not exist", lastLine.endsWith("fail"));    
    }
    
    public void testListDomains() throws Exception {
        String lastLine;
        finishSetUp(m_testConfiguration);
        connect();
        login();

        sendCommand("adddomain domain");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add domain", lastLine.endsWith("successful"));
       
        sendCommand("adddomain domain2");
        lastLine = getLastLine(readAnswer());
        assertTrue("Add domain", lastLine.endsWith("successful")); 
        
        sendCommand("listdomains");
        List answer = readAnswer(3);
        assertTrue("list domain 1", answer.get(1).toString().endsWith("domain")); 
        assertTrue("list domain 2", answer.get(2).toString().endsWith("domain2"));   
    }
}
