package org.openmhealth.shim;

import oauth.signpost.OAuth;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common code for all OAuth1.0 based shims.
 */
public abstract class OAuth1ShimBase implements Shim, OAuth1Shim {

    protected HttpClient httpClient = HttpClients.createDefault();

    private static Map<String, AuthorizationRequestParameters> AUTH_PARAMS_REPO = new LinkedHashMap<>();


    @Override
    @SuppressWarnings("unchecked")
    public AuthorizationRequestParameters getAuthorizationRequestParameters(
        String username,
        Map<String, String> addlParameters
    ) throws ShimException {

        String stateKey = OAuth1Utils.generateStateKey();

        try {
            String callbackUrl =
                URLEncoder.encode("http://localhost:8080/authorize/" + getShimKey() + "/callback" +
                    "?state=" + stateKey, "UTF-8");

            Map<String, String> requestTokenParameters = new HashMap<>();
            requestTokenParameters.put("oauth_callback", callbackUrl);

            String initiateAuthUrl = getBaseRequestTokenUrl();

            HttpResponse response = httpClient.execute(
                getRequestTokenRequest(initiateAuthUrl, null, null, requestTokenParameters));

            Map<String, String> tokenParameters = OAuth1Utils.parseRequestTokenResponse(response);

            String token = tokenParameters.get(OAuth.OAUTH_TOKEN);
            String tokenSecret = tokenParameters.get(OAuth.OAUTH_TOKEN_SECRET);

            URL authorizeUrl = signUrl(getBaseAuthorizeUrl(), token, tokenSecret, null);
            System.out.println("The authorization url is: ");
            System.out.println(authorizeUrl);

            /**
             * Build the auth parameters entity to return
             */
            AuthorizationRequestParameters parameters = new AuthorizationRequestParameters();
            parameters.setUsername(username);
            parameters.setRedirectUri(callbackUrl);
            parameters.setStateKey(stateKey);
            parameters.setHttpMethod(HttpMethod.GET);
            parameters.setAuthorizationUrl(authorizeUrl.toString());
            parameters.setRequestParams(tokenParameters);

            /**
             * Store the parameters in a repo.
             */
            AUTH_PARAMS_REPO.put(stateKey, parameters);

            return parameters;
        } catch (HttpClientErrorException e) {
            e.printStackTrace();
            throw new ShimException("HTTP Error: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            throw new ShimException("Unable to initiate OAuth1 authorization, " +
                "could not parse token parameters");
        }
    }

    @Override
    public AuthorizationResponse handleAuthorizationResponse(HttpServletRequest servletRequest) throws ShimException {

        // Fetch the access token.
        String stateKey = servletRequest.getParameter("state");
        String requestToken = servletRequest.getParameter(OAuth.OAUTH_TOKEN);
        final String requestVerifier = servletRequest.getParameter(OAuth.OAUTH_VERIFIER);

        AuthorizationRequestParameters authParams = AUTH_PARAMS_REPO.get(stateKey);
        if (authParams == null) {
            throw new ShimException("Invalid state, could not find " +
                "corresponding auth parameters");
        }

        // Get the token secret from the original access request.
        String requestTokenSecret = authParams.getRequestParams().get(OAuth.OAUTH_TOKEN_SECRET);

        HttpResponse response;
        try {
            response = httpClient.execute(getAccessTokenRequest(getBaseTokenUrl(),
                requestToken, requestTokenSecret, new HashMap<String, String>() {{
                    put(OAuth.OAUTH_VERIFIER, requestVerifier);
                }}));
        } catch (IOException e) {
            e.printStackTrace();
            throw new ShimException("Could not retrieve response from token URL");
        }
        Map<String, String> accessTokenParameters = OAuth1Utils.parseRequestTokenResponse(response);
        String accessToken = accessTokenParameters.get(OAuth.OAUTH_TOKEN);
        String accessTokenSecret = accessTokenParameters.get(OAuth.OAUTH_TOKEN_SECRET);

        AccessParameters accessParameters = new AccessParameters();
        accessParameters.setClientId(getClientId());
        accessParameters.setClientSecret(getClientSecret());
        accessParameters.setStateKey(stateKey);
        accessParameters.setUsername(authParams.getUsername());
        accessParameters.setAccessToken(accessToken);
        accessParameters.setTokenSecret(accessTokenSecret);
        accessParameters.setAdditionalParameters(new HashMap<String, Object>() {{
            put(OAuth.OAUTH_VERIFIER, requestVerifier);
        }});
        loadAdditionalAccessParameters(servletRequest, accessParameters);
        return AuthorizationResponse.authorized(accessParameters);
    }

    protected void loadAdditionalAccessParameters(
        HttpServletRequest request,
        AccessParameters accessParameters
    ) {
        //noop, override if additional parameters must be set here
    }

    protected HttpPost getSignedPostRequest(String unsignedUrl,
                                            String token,
                                            String tokenSecret,
                                            Map<String, String> oauthParams) throws ShimException {
        return OAuth1Utils.getSignedPostRequest(
            unsignedUrl,
            getClientId(),
            getClientSecret(),
            token, tokenSecret, oauthParams);
    }

    protected URL signUrl(String unsignedUrl,
                          String token,
                          String tokenSecret,
                          Map<String, String> oauthParams)
        throws ShimException {
        return OAuth1Utils.buildSignedUrl(
            unsignedUrl,
            getClientId(),
            getClientSecret(),
            token, tokenSecret, oauthParams);
    }

    /**
     * Some external data providers require POST vs GET.
     * In which case the signing of the requests may differ.
     *
     * @param unsignedUrl - The unsigned URL for the request.
     * @param token       - The request token or access token.
     * @param tokenSecret - The token secret, if any.
     * @param oauthParams - Any additional Oauth params.
     * @return - The appropriate request, signed.
     * @throws ShimException
     */
    protected HttpRequestBase getRequestTokenRequest(String unsignedUrl,
                                                     String token,
                                                     String tokenSecret,
                                                     Map<String, String> oauthParams) throws ShimException {
        if (HttpMethod.GET == getRequestTokenMethod()) {
            return new HttpGet(signUrl(unsignedUrl, token, tokenSecret, oauthParams).toString());
        } else {
            return getSignedPostRequest(unsignedUrl, token, tokenSecret, oauthParams);
        }
    }

    /**
     * NOTE: Same as getRequestTokenRequest with difference being that this is for access tokens.
     * <p/>
     * Some external data providers require POST vs GET.
     * In which case the signing of the requests may differ.
     *
     * @param unsignedUrl - The unsigned URL for the request.
     * @param token       - The request token or access token.
     * @param tokenSecret - The token secret, if any.
     * @param oauthParams - Any additional Oauth params.
     * @return - The appropriate request, signed.
     * @throws ShimException
     */
    protected HttpRequestBase getAccessTokenRequest(String unsignedUrl,
                                                    String token,
                                                    String tokenSecret,
                                                    Map<String, String> oauthParams) throws ShimException {
        if (HttpMethod.GET == getAccessTokenMethod()) {
            return new HttpGet(signUrl(unsignedUrl, token, tokenSecret, oauthParams).toString());
        } else {
            return getSignedPostRequest(unsignedUrl, token, tokenSecret, oauthParams);
        }
    }

    protected HttpMethod getRequestTokenMethod() {
        return HttpMethod.GET;
    }

    protected HttpMethod getAccessTokenMethod() {
        return HttpMethod.GET;
    }
}
