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

package org.apache.james.mailetcontainer.camel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.logging.Log;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.lifecycle.LogEnabled;
import org.apache.james.mailetcontainer.MailProcessor;
import org.apache.james.mailetcontainer.MailProcessorList;
import org.apache.james.mailetcontainer.MailetConfigImpl;
import org.apache.james.mailetcontainer.MailetContainer;
import org.apache.james.mailetcontainer.MailetLoader;
import org.apache.james.mailetcontainer.MailetManagement;
import org.apache.james.mailetcontainer.MatcherLoader;
import org.apache.james.mailetcontainer.MatcherManagement;
import org.apache.james.mailetcontainer.ProcessorDetail;
import org.apache.james.mailetcontainer.ProcessorManagementMBean;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.Matcher;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MatcherInverter;

/**
 * Build up the Camel Routes by parsing the mailetcontainer.xml configuration file. 
 * 
 * It also offer the {@link MailProcessorList} implementation which allow to inject {@link Mail} into the routes
 * 
 */
public class CamelMailProcessorList implements Configurable, LogEnabled, MailProcessorList, CamelContextAware, ProcessorManagementMBean {

    private MatcherLoader matcherLoader;
    private HierarchicalConfiguration config;
    private MailetLoader mailetLoader;
    private Log logger;

    private final Map<String,List<MailetManagement>> mailets = new HashMap<String,List<MailetManagement>>();
    private final Map<String,List<MatcherManagement>> matchers = new HashMap<String,List<MatcherManagement>>();
    private final Map<String,MailProcessor> processors = new HashMap<String,MailProcessor>();
    private final UseLatestAggregationStrategy aggr = new UseLatestAggregationStrategy();
       
    @Resource(name = "matcherpackages")
    public void setMatcherLoader(MatcherLoader matcherLoader) {
        this.matcherLoader = matcherLoader;
    }

    @Resource(name = "mailetpackages")
    public void setMailetLoader(MailetLoader mailetLoader) {
        this.mailetLoader = mailetLoader;
    }

    private ProducerTemplate producerTemplate;
	private CamelContext camelContext;
	private MBeanServer mbeanserver;
    


	@PostConstruct
    public void init() throws Exception {
        getCamelContext().addRoutes(new SpoolRouteBuilder());
        producerTemplate = getCamelContext().createProducerTemplate();

        registerMBeans();

    }

    @Resource(name = "mbeanserver")
    public void setMbeanServer(MBeanServer mbeanServer) {
        this.mbeanserver = mbeanServer;
    }
	
	
    /**
     * Destroy all mailets and matchers
     */
    @PreDestroy
    public void dispose() {
        boolean debugEnabled = logger.isDebugEnabled();

        Iterator<List<MailetManagement>> it = mailets.values().iterator();
        while (it.hasNext()) {
            List<MailetManagement> mList = it.next();
            for (int i = 0; i < mList.size(); i++) {
                Mailet m = mList.get(i).getMailet();
                if (debugEnabled) {
                    logger.debug("Shutdown mailet " + m.getMailetInfo());
                }
                m.destroy();
            }
           
        }
        
        Iterator<List<MatcherManagement>> mit = matchers.values().iterator();     
        while (mit.hasNext()) {
            List<MatcherManagement> mList = mit.next();
            for (int i = 0; i < mList.size(); i++) {
                Matcher m = mList.get(i);
                if (debugEnabled) {
                    logger.debug("Shutdown matcher " + m.getMatcherInfo());
                }
                m.destroy();
            }
           
        }      
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.lifecycle.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        this.config = config;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.lifecycle.LogEnabled#setLog(org.apache.commons.logging.Log)
     */
    public void setLog(Log log) {
        this.logger = log;

    }


    /*
     * (non-Javadoc)
     * @see org.apache.james.mailetcontainer.MailProcessorList#getProcessorNames()
     */
    public String[] getProcessorNames() {
        return processors.keySet().toArray(new String[processors.size()]);
    }

    
    /**
     * Mailet which protect us to not fall into an endless loop caused by an configuration error
     *
     */
    private final class TerminatingMailet extends GenericMailet {
        /**
         *  The name of the mailet used to terminate the mailet chain.  The
         *  end of the matcher/mailet chain must be a matcher that matches
         *  all mails and a mailet that sets every mail to GHOST status.
         *  This is necessary to ensure that mails are removed from the spool
         *  in an orderly fashion.
         */
        private static final String TERMINATING_MAILET_NAME = "Terminating%Mailet%Name";

        /*
         * (non-Javadoc)
         * @see org.apache.mailet.base.GenericMailet#service(org.apache.mailet.Mail)
         */
        public void service(Mail mail) {
            if (!(Mail.ERROR.equals(mail.getState()))) {
                // Don't complain if we fall off the end of the
                // error processor.  That is currently the
                // normal situation for James, and the message
                // will show up in the error store.
                StringBuffer warnBuffer = new StringBuffer(256)
                                      .append("Message ")
                                      .append(mail.getName())
                                      .append(" reached the end of this processor, and is automatically deleted.  This may indicate a configuration error.");
                logger.warn(warnBuffer.toString());
            }
            
            // Set the mail to ghost state
            mail.setState(Mail.GHOST);
        }
    
        @Override
        public String getMailetInfo() {
            return getMailetName();
        }
    
        @Override
        public String getMailetName() {
            return TERMINATING_MAILET_NAME;
        }
    }

    /**
     * Return the endpoint for the processorname. 
     * 
     * This will return a "direct" endpoint. 
     * 
     * @param processorName
     * @return endPoint
     */
    protected String getEndpoint(String processorName) {
        return "direct:processor." + processorName;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.transport.MailProcessor#service(org.apache.mailet.Mail)
     */
    public void service(Mail mail) throws MessagingException {
        MailProcessor processor = getProcessor(mail.getState());
        if (processor != null) {
            processor.service(mail);
        } else {
            throw new MessagingException("No processor found for mail " + mail.getName() + " with state " + mail.getState());
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.transport.ProcessorList#getProcessor(java.lang.String)
     */
    public MailProcessor getProcessor(String name) {
        return processors.get(name);
    }
    
    
    /**
     * {@link Processor} which just call the {@link CamelMailProcessorList#service(Mail) method
     * 
     *
     */
    private final class MailCamelProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            service(exchange.getIn().getBody(Mail.class));
        }

    }

    private final class RemovePropertiesProcessor implements Processor {

        public void process(Exchange exchange) throws Exception {
            exchange.removeProperty(MatcherSplitter.ON_MATCH_EXCEPTION_PROPERTY);
            exchange.removeProperty(MatcherSplitter.MATCHER_PROPERTY);
        }
    }
    
    
    private final class ChildProcessor implements MailProcessor, MailetContainer {
       
        
        private String processorName;

        public ChildProcessor(String processorName) {
            this.processorName = processorName;
        }
        

        /*
         * (non-Javadoc)
         * @see org.apache.james.transport.MailProcessor#service(org.apache.mailet.Mail)
         */
        public void service(Mail mail) throws MessagingException {
            try {
                producerTemplate.sendBody(getEndpoint(processorName), mail);
                
             } catch (CamelExecutionException ex) {
                 throw new MessagingException("Unable to process mail " + mail.getName(),ex);
             }        
         }
        
        /*
         * (non-Javadoc)
         * @see org.apache.james.transport.MailetContainer#getMailets()
         */
        public List<Mailet> getMailets() {
            return new ArrayList<Mailet>(mailets.get(processorName));
        }

        /*
         * (non-Javadoc)
         * @see org.apache.james.transport.MailetContainer#getMatchers()
         */
        public List<Matcher> getMatchers() {
            return new ArrayList<Matcher>(matchers.get(processorName));       
        }
    }

	public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    
    private void registerMBeans() {
       
        String baseObjectName = "org.apache.james:type=component,name=processor,";

        String[] processorNames = getProcessorNames();
        for (int i = 0; i < processorNames.length; i++) {
            String processorName = processorNames[i];
            createProcessorMBean(baseObjectName, processorName, mbeanserver);
            continue;
        }
    }

    private void createProcessorMBean(String baseObjectName, String processorName, MBeanServer mBeanServer) {
        String processorMBeanName = baseObjectName + "processor=" + processorName;
        registerMBean(mBeanServer, processorMBeanName, getProcessor(processorName));


        // add all mailets but the last, because that is a terminator (see LinearProcessor.closeProcessorLists())
        List<Mailet> mailets =  ((MailetContainer) getProcessor(processorName)).getMailets();
        for (int i = 0; i < mailets.size(); i++) {
            MailetManagement mailet = (MailetManagement) mailets.get(i);

            String mailetMBeanName = processorMBeanName + ",subtype=mailet,index=" + (i+1) + ",mailetname=" + mailet.getMailetName();
            registerMBean(mBeanServer, mailetMBeanName, mailet);
        }

       
        
        // add all matchers but the last, because that is a terminator (see LinearProcessor.closeProcessorLists())
        List<Matcher> matchers =  ((MailetContainer)getProcessor(processorName)).getMatchers();
        for (int i = 0; i < matchers.size(); i++) {
            MatcherManagement matcher = (MatcherManagement) matchers.get(i);

            String matcherMBeanName = processorMBeanName + ",subtype=matcher,index=" + (i+1) + ",matchername=" + matcher.getMatcherName();

            registerMBean(mBeanServer, matcherMBeanName, matcher);
        }
       

    }

    private void registerMBean(MBeanServer mBeanServer, String mBeanName, Object object) {
        ObjectName objectName = null;
        try {
            objectName = new ObjectName(mBeanName);
        } catch (MalformedObjectNameException e) {
        	logger.info("Unable to register mbean", e);

            return;
        }
        try {
            mBeanServer.registerMBean(object, objectName);
        } catch (javax.management.JMException e) {
            logger.error("Unable to register mbean", e);
        }
    }

    
	private final class SpoolRouteBuilder extends RouteBuilder {
		/*
	     * (non-Javadoc)
	     * @see org.apache.camel.builder.RouteBuilder#configure()
	     */
	    @SuppressWarnings("unchecked")
	    @Override
	    public void configure() throws Exception {
	        Processor terminatingMailetProcessor = new MailetProcessor(new TerminatingMailet(), logger);
	        Processor disposeProcessor = new DisposeProcessor();
	        Processor mailProcessor = new MailCamelProcessor();
	        Processor removePropsProcessor = new RemovePropertiesProcessor();
	        
	        List<HierarchicalConfiguration> processorConfs = config.configurationsAt("processor");
	        for (int i = 0; i < processorConfs.size(); i++) {
	            final HierarchicalConfiguration processorConf = processorConfs.get(i);
	            String processorName = processorConf.getString("[@name]");

	          
	            mailets.put(processorName, new ArrayList<MailetManagement>());
	            matchers.put(processorName, new ArrayList<MatcherManagement>());

	            RouteDefinition processorDef = from(getEndpoint(processorName)).inOnly()
	            // store the logger in properties
	            .setProperty(MatcherSplitter.LOGGER_PROPERTY, constant(logger));   
	               
	            
	            final List<HierarchicalConfiguration> mailetConfs = processorConf.configurationsAt("mailet");
	            // Loop through the mailet configuration, load
	            // all of the matcher and mailets, and add
	            // them to the processor.
	            for (int j = 0; j < mailetConfs.size(); j++) {
	                HierarchicalConfiguration c = mailetConfs.get(j);

	                // We need to set this because of correctly parsing comma
	                String mailetClassName = c.getString("[@class]");
	                String matcherName = c.getString("[@match]", null);
	                String invertedMatcherName = c.getString("[@notmatch]", null);

	                Mailet mailet = null;
	                Matcher matcher = null;
	                try {

	                    if (matcherName != null && invertedMatcherName != null) {
	                        // if no matcher is configured throw an Exception
	                        throw new ConfigurationException("Please configure only match or nomatch per mailet");
	                    } else if (matcherName != null) {
	                        matcher = matcherLoader.getMatcher(matcherName);
	                    } else if (invertedMatcherName != null) {
	                        matcher = new MatcherInverter(matcherLoader.getMatcher(invertedMatcherName));

	                    } else {
	                        // default matcher is All
	                        matcher = matcherLoader.getMatcher("All");
	                    }

	                    // The matcher itself should log that it's been inited.
	                    if (logger.isInfoEnabled()) {
	                        StringBuffer infoBuffer = new StringBuffer(64).append("Matcher ").append(matcherName).append(" instantiated.");
	                        logger.info(infoBuffer.toString());
	                    }
	                } catch (MessagingException ex) {
	                    // **** Do better job printing out exception
	                    if (logger.isErrorEnabled()) {
	                        StringBuffer errorBuffer = new StringBuffer(256).append("Unable to init matcher ").append(matcherName).append(": ").append(ex.toString());
	                        logger.error(errorBuffer.toString(), ex);
	                        if (ex.getNextException() != null) {
	                            logger.error("Caused by nested exception: ", ex.getNextException());
	                        }
	                    }
	                    System.err.println("Unable to init matcher " + matcherName);
	                    System.err.println("Check spool manager logs for more details.");
	                    // System.exit(1);
	                    throw new ConfigurationException("Unable to init matcher", ex);
	                }
	                try {
	                    mailet = mailetLoader.getMailet(mailetClassName, c);
	                    if (logger.isInfoEnabled()) {
	                        StringBuffer infoBuffer = new StringBuffer(64).append("Mailet ").append(mailetClassName).append(" instantiated.");
	                        logger.info(infoBuffer.toString());
	                    }
	                } catch (MessagingException ex) {
	                    // **** Do better job printing out exception
	                    if (logger.isErrorEnabled()) {
	                        StringBuffer errorBuffer = new StringBuffer(256).append("Unable to init mailet ").append(mailetClassName).append(": ").append(ex.toString());
	                        logger.error(errorBuffer.toString(), ex);
	                        if (ex.getNextException() != null) {
	                            logger.error("Caused by nested exception: ", ex.getNextException());
	                        }
	                    }
	                    System.err.println("Unable to init mailet " + mailetClassName);
	                    System.err.println("Check spool manager logs for more details.");
	                    throw new ConfigurationException("Unable to init mailet", ex);
	                }
	                if (mailet != null && matcher != null) {
	                    MailetManagement wrappedMailet = new MailetManagement(mailet);
	                    MatcherManagement wrappedMatcher = new MatcherManagement(matcher);
	                    String onMatchException = null;
	                    MailetConfig mailetConfig = wrappedMailet.getMailetConfig();
	                    
	                    if (mailetConfig instanceof MailetConfigImpl) {
	                        onMatchException = ((MailetConfigImpl) mailetConfig).getInitAttribute("onMatchException");
	                    }
	                    
	                    MailetProcessor mailetProccessor = new MailetProcessor(wrappedMailet, logger);
	                    // Store the matcher to use for splitter in properties
	                    processorDef
	                            .setProperty(MatcherSplitter.MATCHER_PROPERTY, constant(wrappedMatcher)).setProperty(MatcherSplitter.ON_MATCH_EXCEPTION_PROPERTY, constant(onMatchException))
	                            
	                            // do splitting of the mail based on the stored matcher
	                            .split().method(MatcherSplitter.class).aggregationStrategy(aggr).parallelProcessing()

	                            .choice().when(new MatcherMatch()).process(mailetProccessor).end()
	                            
	                            .choice().when(new MailStateEquals(Mail.GHOST)).process(disposeProcessor).stop().otherwise().process(removePropsProcessor).end()

	                            .choice().when(new MailStateNotEquals(processorName)).process(mailProcessor).stop().end();

	                    // store mailet and matcher
	                    mailets.get(processorName).add(wrappedMailet);
	                    matchers.get(processorName).add(wrappedMatcher);
	                }
	              

	            }
	            
	            processorDef
	                    // start choice
	                    .choice()
	                 
	                    // when the mail state did not change till yet ( the end of the route) we need to call the TerminatingMailet to
	                    // make sure we don't fall into a endless loop
	                    .when(new MailStateEquals(processorName)).process(terminatingMailetProcessor).stop()
	                    
	                       
	                    // dispose when needed
	                    .when(new MailStateEquals(Mail.GHOST)).process(disposeProcessor).stop()
	                    
	                     // route it to the next processor
	                    .otherwise().process(mailProcessor).stop();
	                  
	            processors.put(processorName, new ProcessorDetail(processorName,new ChildProcessor(processorName)));
	        }
	                
	    }
	}
}
