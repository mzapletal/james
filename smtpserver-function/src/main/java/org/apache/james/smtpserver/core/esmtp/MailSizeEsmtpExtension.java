/**
 * 
 */
package org.apache.james.smtpserver.core.esmtp;

import java.util.LinkedList;
import java.util.List;

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.dsn.DSNStatus;
import org.apache.james.smtpserver.LineHandler;
import org.apache.james.smtpserver.SMTPRetCode;
import org.apache.james.smtpserver.SMTPSession;
import org.apache.james.smtpserver.core.DataLineFilter;
import org.apache.james.smtpserver.hook.HookResult;
import org.apache.james.smtpserver.hook.HookReturnCode;
import org.apache.james.smtpserver.hook.MailParametersHook;
import org.apache.james.smtpserver.hook.MessageHook;
import org.apache.mailet.Mail;

/**
 * Handle the ESMTP SIZE extension.
 */
public class MailSizeEsmtpExtension extends AbstractLogEnabled implements
        MailParametersHook, EhloExtension, DataLineFilter, MessageHook {

    private final static String MESG_SIZE = "MESG_SIZE"; // The size of the
    private final static String MESG_FAILED = "MESG_FAILED";   // Message failed flag


    /**
     * @see org.apache.james.smtpserver.hook.MailParametersHook#doMailParameter(org.apache.james.smtpserver.SMTPSession, java.lang.String, java.lang.String)
     */
    public HookResult doMailParameter(SMTPSession session, String paramName,
            String paramValue) {
        HookResult res = doMailSize(session, paramValue,
                (String) session.getState().get(SMTPSession.SENDER));
        return res;
    }

    /**
     * @see org.apache.james.smtpserver.hook.MailParametersHook#getMailParamNames()
     */
    public String[] getMailParamNames() {
        return new String[] { "SIZE" };
    }

    /**
     * @see org.apache.james.smtpserver.core.esmtp.EhloExtension#getImplementedEsmtpFeatures(org.apache.james.smtpserver.SMTPSession)
     */
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        LinkedList<String> resp = new LinkedList<String>();
        // Extension defined in RFC 1870
        long maxMessageSize = session.getMaxMessageSize();
        if (maxMessageSize > 0) {
            resp.add("SIZE " + maxMessageSize);
        }
        return resp;
    }


    /**
     * Handles the SIZE MAIL option.
     * 
     * @param session
     *            SMTP session object
     * @param mailOptionValue
     *            the option string passed in with the SIZE option
     * @param tempSender
     *            the sender specified in this mail command (for logging
     *            purpose)
     * @return true if further options should be processed, false otherwise
     */
    private HookResult doMailSize(SMTPSession session,
            String mailOptionValue, String tempSender) {
        int size = 0;
        try {
            size = Integer.parseInt(mailOptionValue);
        } catch (NumberFormatException pe) {
            getLogger().error("Rejected syntactically incorrect value for SIZE parameter.");
            
            // This is a malformed option value. We return an error
            return new HookResult(HookReturnCode.DENY, 
                    SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
                    DSNStatus.getStatus(DSNStatus.PERMANENT,
                            DSNStatus.DELIVERY_INVALID_ARG)
                            + " Syntactically incorrect value for SIZE parameter");
        }
        if (getLogger().isDebugEnabled()) {
            StringBuffer debugBuffer = new StringBuffer(128).append(
                    "MAIL command option SIZE received with value ").append(
                    size).append(".");
            getLogger().debug(debugBuffer.toString());
        }
        long maxMessageSize = session.getMaxMessageSize();
        if ((maxMessageSize > 0) && (size > maxMessageSize)) {
            // Let the client know that the size limit has been hit.
            StringBuffer errorBuffer = new StringBuffer(256).append(
                    "Rejected message from ").append(
                    tempSender != null ? tempSender : null).append(
                    " from host ").append(session.getRemoteHost()).append(" (")
                    .append(session.getRemoteIPAddress()).append(") of size ")
                    .append(size).append(
                            " exceeding system maximum message size of ")
                    .append(maxMessageSize).append("based on SIZE option.");
            getLogger().error(errorBuffer.toString());

            return new HookResult(HookReturnCode.DENY, SMTPRetCode.QUOTA_EXCEEDED, DSNStatus
                    .getStatus(DSNStatus.PERMANENT,
                            DSNStatus.SYSTEM_MSG_TOO_BIG)
                    + " Message size exceeds fixed maximum message size");
        } else {
            // put the message size in the message state so it can be used
            // later to restrict messages for user quotas, etc.
            session.getState().put(MESG_SIZE, new Integer(size));
        }
        return null;
    }

    /**
     * @see org.apache.james.smtpserver.core.DataLineFilter#onLine(org.apache.james.smtpserver.SMTPSession, byte[], org.apache.james.smtpserver.LineHandler)
     */
    public void onLine(SMTPSession session, byte[] line, LineHandler next) {
        Boolean failed = (Boolean) session.getState().get(MESG_FAILED);
        // If we already defined we failed and sent a reply we should simply
        // wait for a CRLF.CRLF to be sent by the client.
        if (failed != null && failed.booleanValue()) {
            // TODO
        } else {
            if (line.length == 3 && line[0] == 46) {
                next.onLine(session, line);
            } else {
                Long currentSize = (Long) session.getState().get("CURRENT_SIZE");
                Long newSize;
                if (currentSize == null) {
                    newSize = new Long(line.length);
                } else {
                    newSize = new Long(currentSize.intValue()+line.length);
                }
                
                if (session.getMaxMessageSize() > 0 && newSize.intValue() > session.getMaxMessageSize()) {
                    // Add an item to the state to suppress
                    // logging of extra lines of data
                    // that are sent after the size limit has
                    // been hit.
                    session.getState().put(MESG_FAILED, Boolean.TRUE);
                    // then let the client know that the size
                    // limit has been hit.
                    next.onLine(session, ".\r\n".getBytes());
                } else {
                    next.onLine(session, line);
                }
                
                session.getState().put("CURRENT_SIZE", newSize);
            }
        }
    }

    /**
     * @see org.apache.james.smtpserver.hook.MessageHook#onMessage(org.apache.james.smtpserver.SMTPSession, org.apache.mailet.Mail)
     */
    public HookResult onMessage(SMTPSession session, Mail mail) {
        Boolean failed = (Boolean) session.getState().get(MESG_FAILED);
        if (failed != null && failed.booleanValue()) {
            HookResult response = new HookResult(HookReturnCode.DENY, SMTPRetCode.QUOTA_EXCEEDED,DSNStatus.getStatus(DSNStatus.PERMANENT,
                    DSNStatus.SYSTEM_MSG_TOO_BIG) + " Maximum message size exceeded");
  
            StringBuffer errorBuffer = new StringBuffer(256).append(
                    "Rejected message from ").append(
                    session.getState().get(SMTPSession.SENDER).toString())
                    .append(" from host ").append(session.getRemoteHost())
                    .append(" (").append(session.getRemoteIPAddress())
                    .append(") exceeding system maximum message size of ")
                    .append(
                            session.getMaxMessageSize());
            getLogger().error(errorBuffer.toString());
            // TODO ???
            // session.pushLineHandler(new DataCmdHandler.DataConsumerLineHandler());
            return response;
        } else {
            return new HookResult(HookReturnCode.DECLINED);
        }
    }

}