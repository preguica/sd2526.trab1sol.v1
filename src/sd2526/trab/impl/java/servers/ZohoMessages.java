package sd2526.trab.impl.java.servers;

import java.util.List;
import java.util.logging.Logger;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.oauth.Zoho;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.java.servers.JavaMessages.JobDispatcher;

public class ZohoMessages implements Messages, AdminMessages {

    private static final Logger Log = Logger.getLogger(ZohoMessages.class.getName());

    private static ZohoMessages instance;

    private final Zoho zoho = Zoho.getInstance();
    private final JobDispatcher jobs = new JobDispatcher();

    private String accountId;
    private String zohoEmailAddress;
    private String inboxFolderId;
    private String localUser;
    private String domain;

    private ZohoMessages() {}

    public synchronized static ZohoMessages getInstance() {
        if (instance == null)
            instance = new ZohoMessages();
        return instance;
    }

    public void init(String localUser, String domain, boolean cleanState) throws Exception {
        // TODO
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        return null;
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        return null;
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        return null;
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        return null;
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return null;
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        return null;
    }

    @Override
    public Result<Void> remotePostMessage(Message m) {
        return null;
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        return null;
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        return null;
    }
}