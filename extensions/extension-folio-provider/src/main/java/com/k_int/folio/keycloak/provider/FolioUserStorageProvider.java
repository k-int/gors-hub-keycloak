package com.k_int.folio.keycloak.provider;


import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// interface for talking to FOLIO for user details, and a Simple HTTP implementation
import com.k_int.folio.keycloak.provider.external.FolioUser;
import com.k_int.folio.keycloak.provider.external.FolioClient;
import com.k_int.folio.keycloak.provider.external.FolioClientSimpleHttp;

import org.keycloak.connections.httpclient.HttpClientProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.Header;


import org.jboss.logging.Logger;

/**
 * inspired by https://github.com/dasniko/keycloak-extensions-demo/blob/main/user-provider/src/main/java/dasniko/keycloak/user/PeanutsUserProvider.java
 */
public class FolioUserStorageProvider implements UserStorageProvider, 
                                                 UserLookupProvider,
                                                 CredentialInputValidator {

  // implement in future: CredentialInputUpdater, UserRegistrationProvider, UserQueryProvider,

  private static final Logger log = Logger.getLogger(FolioUserStorageProvider.class);
  private final KeycloakSession session;
  private final ComponentModel model;
  private final FolioClient client;

  public FolioUserStorageProvider(KeycloakSession session, ComponentModel model) {
    log.info("FolioUserStorageProvider::FolioUserStorageProvider(...)");
    this.session = session;
    this.model = model;
    this.client = new FolioClientSimpleHttp(session, model);
    log.info("FolioUserStorageProvider::FolioUserStorageProvider(...) COMPLETE");
  }

  @Override
  public void close() {
    log.debug("close");
  }


  // CredentialInputProvider
  // https://www.keycloak.org/docs-api/20.0.2/javadocs/org/keycloak/credential/CredentialInputValidator.html
  @Override
  public boolean supportsCredentialType(String credentialType) {
    log.debug(String.format("supportsCredentialType(%s)",credentialType));
    return PasswordCredentialModel.TYPE.equals(credentialType);
  }

  @Override
  public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
    log.debug(String.format("isConfiguredFor(realm,user,%s)",credentialType));
    return supportsCredentialType(credentialType);
  }


  /**
   *  
   */
  @Override
  public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {

    log.debug(String.format("isValid(..%s,%s)",user.toString(),input.toString()));

    if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
      return false;
    }

    UserCredentialModel ucm = (UserCredentialModel) input;
    String password = ucm.getChallengeResponse();
    String username = user.getUsername();

    try {
      int response = attemptFolioLogin(username, password);
      log.debugf("Got response : %d",response);
      if ( response == 201 ) {
        log.debug("RETURNING isValid:: TRUE");
        return true;
      }
    }
    catch ( Exception e ) {
      log.error("Exception talking to FOLIO/OKAPI",e);
    }

    log.debug("RETURNING isValid:: FALSE");
    return false;
  }

  @Override
  public void preRemove(RealmModel realm) {
  }

  @Override
  public void preRemove(RealmModel realm, GroupModel group) {
  }

  @Override
  public void preRemove(RealmModel realm, RoleModel role) {
  }

  private int attemptFolioLogin(String username, String password) throws Exception {

      String user_pass_json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
      StringEntity entity = new StringEntity(user_pass_json);

      int responseCode = 400;
      CloseableHttpClient client = session.getProvider(HttpClientProvider.class).getHttpClient();
      try {
        String cfg_baseUrl = model.get(FolioProviderConstants.BASE_URL);
        String cfg_tenant = model.get(FolioProviderConstants.TENANT);
        String cfg_basicUsername = model.get(FolioProviderConstants.AUTH_USERNAME);
        String cfg_basicPassword = model.get(FolioProviderConstants.AUTH_PASSWORD);
  
        log.debug(String.format("Attempting FOLIO to %s(%s)login with %s",cfg_baseUrl, cfg_tenant, user_pass_json));
  
        // get okapi token first
        log.info("/authn/login");
        String token_url = cfg_baseUrl + "/authn/login";
        HttpPost httpPost = new HttpPost(token_url);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setHeader("X-Okapi-Tenant", cfg_tenant);
        CloseableHttpResponse response = client.execute(httpPost);

        if ( response != null ) {
          log.debugf("Got okapi response %s",response.toString());
          Header okapi_token_header = response.getFirstHeader("X-Okapi-Token");
          String token = null;
          if ( okapi_token_header != null )
            token = okapi_token_header.getValue().toString();
          else 
            log.warn("Response did not carry an X-Okapi-Token - likely invalid user");

          responseCode = response.getStatusLine().getStatusCode();
        }
        else {
          log.warn("NULL response from OKAPI");
        }
      }
      finally {
      }

      return responseCode;
  }


  @Override
  public UserModel getUserByEmail(RealmModel realm, String email) {
    log.debug("getUserByEmail");
    FolioUser folio_user = new FolioUser();
    // folio_user.setFolioUUID("1234");
    folio_user.setUsername("mockuser");
    // folio_user.setFirstName("mockuserfirst");
    // folio_user.setLastName("mockuserlast");
    // folio_user.setEmail("mockemail");
    // folio_user.setBarcode("mockbarcode");
    return new FolioUserAdapter(session, realm, model, folio_user);
  }



  @Override
  public UserModel getUserByUsername(RealmModel realm, String username) {
    log.debugf("getUserByUsername: %s", username);
    FolioUser folio_user = client.getFolioUserByUsername(username);

    // If we got a response, from our api object convert it into a keycloak user model and return it
    if ( folio_user != null ) {
      log.debugf("Result of getUserByUsername(%s): %s",username,folio_user.toString());
      return new FolioUserAdapter(session, realm, model, folio_user);
    }
    else {
      log.warnf("Unable to locate user %s",username);
    }

    // Otherwise all bets are off
    return null;
  }


  @Override
  public UserModel getUserById(RealmModel realm, String id) {

    log.debugf("getUserById: %s (%s)", id, StorageId.externalId(id));

    // Whats going on here? in a user federation, keycloak constructs a user id as "f:uuid-of-provider:username" StorageId.externalId effectively
    // parses that ID out to just username, so although the function is getUserById we actually need to call 
    FolioUser folio_user = client.getFolioUserByUsername(StorageId.externalId(id));

    // and not FolioUser folio_user = client.getFolioUserById(StorageId.externalId(id));
    if ( folio_user != null ) {
      log.debugf("client.getFolioUserByUsername returned %s",folio_user.toString());
      UserModel user_model =  new FolioUserAdapter(session, realm, model, folio_user);
      log.debugf("Converted user model is %s",user_model.toString());
      return user_model;
    }

    log.debug("getUserById did not return a user - returning null");
    return null;
  }
}
