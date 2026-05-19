package sd2526.trab.impl.oauth;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import sd2526.trab.impl.oauth.zoho.ZohoServiceFactory;
import sd2526.trab.impl.oauth.zoho.ZohoTokenManager;
import sd2526.trab.impl.oauth.zoho.msgs.ZohoAccount;
import sd2526.trab.impl.oauth.zoho.msgs.ZohoAccountReply;
import sd2526.trab.impl.utils.JSON;

public class Zoho {
    static final String MAIL_API_BASE = "https://mail.zoho.eu/api";
    // TODO: change to my credentials
    static final String CLIENT_ID     = "1000.OF3ENQSLN1LX1VH9YRCMRWRODU9LWN";
    static final String CLIENT_SECRET = "6a775bc7518e5b52a2318df0895b3ff44f255e70d6";
    static final String REFRESH_TOKEN = "1000.08f9088e27eff3ce246e4c6b046bcc7c.ffb2831c13f4098702cb1ce111b0ccb0";

    private static final String ACCOUNTS = "/accounts";

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static Zoho instance;

    private Zoho() {
        service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
    }

    synchronized public static Zoho getInstance() {
        if( instance == null )
            instance = new Zoho();
        return instance;
    }

    public ZohoAccount getAccount() throws Exception {
        var accessToken = new OAuth2AccessToken( tokenManager.getValidAccessToken() );

        OAuthRequest request = new OAuthRequest(Verb.GET, MAIL_API_BASE + ACCOUNTS);
        service.signRequest(accessToken, request);

        try (Response response = service.execute(request)) {
            if( response.isSuccessful() ) {
                var body = response.getBody();
                var data = JSON.decode(body, ZohoAccountReply.class).data();
                if (data == null || data.isEmpty()) return null;
                return data.get(0);
            }
            else {
                System.err.println( response.getCode() + "/" + response.getBody() );
                return null;
            }
        }
    }

    public String getInboxFolderId(String accountId) throws Exception {
        var request = signedRequest(Verb.GET, MAIL_API_BASE + ACCOUNTS + "/" + accountId + "/folders");

        try (Response response = service.execute(request)) {
            if (!response.isSuccessful()) {
                System.err.println("getInboxFolderId: " + response.getCode() + "/" + response.getBody());
                return null;
            }
            var reply = JSON.decode(response.getBody(), ZohoFolderListReply.class);
            if (reply == null || reply.data() == null) return null;
            for (var folder : reply.data())
                if ("Inbox".equalsIgnoreCase(folder.folderName()))
                    return folder.folderId();
            return null;
        }
    }

    public boolean sendMessage(String accountId, String fromAddress, String subject, String body) throws Exception {
        var url     = MAIL_API_BASE + ACCOUNTS + "/" + accountId + "/messages";
        var request = signedRequest(Verb.POST, url);
        request.addHeader("Content-Type", "application/json");

        // send to self so it appears in the inbox
        var payload = """
                {
                  "fromAddress": "%s",
                  "toAddress":   "%s",
                  "subject":     "%s",
                  "content":     %s
                }""".formatted(
                fromAddress,
                fromAddress,
                subject.replace("\"", "\\\""),
                JSON.encode(body));

        request.setPayload(payload);

        try (Response response = service.execute(request)) {
            if (!response.isSuccessful()) {
                System.err.println("sendMessage: " + response.getCode() + "/" + response.getBody());
                return false;
            } return true;
        }
    }




    private OAuthRequest signedRequest(Verb verb, String url) throws Exception {
        var token   = new OAuth2AccessToken(tokenManager.getValidAccessToken());
        var request = new OAuthRequest(verb, url);
        service.signRequest(token, request);
        return request;
    }

}