package sd2526.trab.impl.java.servers;

import static sd2526.trab.api.java.Result.error;
import static sd2526.trab.api.java.Result.ok;
import static sd2526.trab.api.java.Result.ErrorCode.*;

import java.util.List;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;

import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.oauth.Zoho;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.java.servers.JavaMessages.JobDispatcher;
import sd2526.trab.impl.java.clients.Clients;
import sd2526.trab.impl.utils.JSON;

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
        this.localUser = localUser;
        this.domain    = domain;
        var account           = zoho.getAccount();
        this.accountId        = account.accountId();
        this.zohoEmailAddress = account.primaryEmailAddress();
        this.inboxFolderId    = fetchInboxFolderId();

        Log.info("ZohoMessages init – accountId=%s address=%s user=%s@%s cleanState=%b"
                .formatted(accountId, zohoEmailAddress, localUser, domain, cleanState));

        if (cleanState) cleanInbox();
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        Log.info("postMessage sender=%s".formatted(msg.getSender()));
        if (badParams(msg.getSender(), pwd) || msg.getDestination() == null || msg.getDestination().isEmpty())
            return error(BAD_REQUEST);

        var senderName = msg.getSender().contains("@") ? msg.getSender().split("@")[0] : msg.getSender();
        var userResult = Clients.UsersClient.get().getUser(senderName, pwd);
        if (!userResult.isOK()) return error(userResult.error());

        // TODO
        return null;
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        Log.info("getInboxMessage user=%s mid=%s".formatted(name, mid));

        if (badParams(name, mid, pwd)) return error(BAD_REQUEST);

        var auth = Clients.UsersClient.get().getUser(name, pwd);
        if (!auth.isOK()) return error(auth.error());

        // TODO
        return null;
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        Log.info("getAllInboxMessages user=%s".formatted(name));

        if (badParams(name, pwd)) return error(BAD_REQUEST);

        var auth = Clients.UsersClient.get().getUser(name, pwd);
        if (!auth.isOK()) return error(auth.error());

        // TODO
        return null;
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        Log.info("removeInboxMessage user=%s mid=%s".formatted(name, mid));

        if (badParams(name, mid, pwd))
            return error(BAD_REQUEST);

        var auth = Clients.UsersClient.get().getUser(name, pwd);
        if (!auth.isOK()) return error(auth.error());

        return deleteByMid(mid);
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        Log.info("deleteMessage name=%s mid=%s".formatted(name, mid));

        if (badParams(name, mid, pwd))
            return error(BAD_REQUEST);

        var auth = Clients.UsersClient.get().getUser(name, pwd);
        if (!auth.isOK()) return error(auth.error());

        // TODO
        return null;
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        Log.info("searchInbox user=%s query=%s".formatted(name, query));

        if (badParams(name, pwd))
            return error(BAD_REQUEST);

        var auth = Clients.UsersClient.get().getUser(name, pwd);
        if (!auth.isOK()) return error(auth.error());

        // TODO
        return null;
    }

    @Override
    public Result<Void> remotePostMessage(Message m) {
        Log.info("remotePostMessage id=%s".formatted(msg.getId()));
        return storeInZoho(msg);
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        Log.info("remoteDeleteMessage mid=%s".formatted(mid));
        return deleteByMid(mid);
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        Log.info("remoteDeleteUserInbox user=%s".formatted(name));
        if (!localUser.equals(name))
            return error(NOT_FOUND);
        try {
            cleanInbox();
            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

    // Deletes all messages in the Zoho inbox
    private void cleanInbox() throws Exception {
        Log.info("Cleaning Zoho inbox...");
        for (var summary : listEmailSummaries()) deleteEmail(summary.folderId(), summary.messageId());
        Log.info("Inbox cleaned.");
    }

    private void deleteEmail(String folderId, String zohoMsgId) throws Exception {
        var url = Zoho.MAIL_API_BASE + "/accounts/" + accountId + "/folders/" + folderId + "/messages/" + zohoMsgId;
        var request = signedRequest(Verb.DELETE, url);
        // TODO
    }

    // Lists all email summaries in the Zoho inbox
    private List<EmailSummary> listEmailSummaries() throws Exception {
        var url     = Zoho.MAIL_API_BASE + "/accounts/" + accountId
                + "/messages/view?folderId=" + inboxFolderId + "&limit=200";
        var request = signedRequest(Verb.GET, url);
        // TODO
        return null;
    }

    private Result<Void> deleteByMid(String mid) {
        try {
            for (var summary : zoho.listMessages(accountId, inboxFolderId)) {
                var msg = fetchAndParse(summary);
                if (msg != null && mid.equals(msg.getId())) {
                    zoho.deleteMessage(accountId, summary.folderId(), summary.messageId());
                    return ok();
                }
            }
            return error(NOT_FOUND);
        } catch (Exception e) {
            Log.warning("deleteByMid: " + e.getMessage());
            return error(INTERNAL_ERROR);
        }
    }

    private OAuthRequest signedRequest(Verb verb, String url) throws Exception {
        var token   = new OAuth2AccessToken(zoho.tokenManager.getValidAccessToken());
        var request = new OAuthRequest(verb, url);
        zoho.service.signRequest(token, request);
        return request;
    }

    private Result<Void> storeMessage(Message msg) {
        try {
            var meta = new StoredMetadata(
                    msg.getId(),
                    msg.getSender(),
                    new ArrayList<>(msg.getDestination()),
                    msg.getSubject(),
                    msg.getCreationTime());

            var body    = msg.getContents() + SEPARATOR + JSON.encode(meta);
            var subject = msg.getSubject() != null ? msg.getSubject() : "(no subject)";

            boolean ok = zoho.sendMessage(accountId, zohoEmailAddress, subject, body);
            return ok ? ok() : error(INTERNAL_ERROR);
        } catch (Exception e) {
            Log.warning("storeMessage: " + e.getMessage());
            return error(INTERNAL_ERROR);
        }
    }

    private boolean matches(Message msg, String s) {
        return (msg.getSubject()  != null && msg.getSubject().toLowerCase().contains(s))
                || (msg.getContents() != null && msg.getContents().toLowerCase().contains(s));
    }

    private boolean badParams(String... params) {
        for (var p : params)
            if (p == null || p.isBlank()) return true;
        return false;
    }

    private <T> Result<T> reTry(java.util.function.Supplier<Result<T>> action, long deadlineMs) {
        var start = System.currentTimeMillis();
        Result<T> last;
        do {
            last = action.get();
            if (last.error() != Result.ErrorCode.TIMEOUT) return last;
        } while (System.currentTimeMillis() - start < deadlineMs);
        return last;
    }

    record StoredMetadata(
            String       id,
            String       sender,
            List<String> destination,
            String       subject,
            long         creationTime) {}

}